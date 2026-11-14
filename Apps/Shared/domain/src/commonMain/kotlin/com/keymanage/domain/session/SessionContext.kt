package com.keymanage.domain.session

import com.keymanage.domain.auth.UnlockKdf
import com.keymanage.domain.crypto.EncryptionKey
import com.keymanage.domain.device.DevicePersistence
import com.keymanage.domain.device.DeviceRecord
import com.keymanage.domain.device.parseDeviceRecord
import com.keymanage.domain.device.rememberedKdf
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.vault.RevisionMemory
import com.keymanage.domain.vault.VaultSync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// 세션 하위 모듈이 함께 읽고 바꾸는 상태 묶음과 상태만 다루는 공통 동작 (SEC-002, SEC-003). 세션 밖으로 내보내지 않는다
// 원본: Apps/Web/src/domain/session/sessionContext.ts

/**
 * 세션 하나의 상태. VaultSession이 하나 만들어 하위 모듈에 넘긴다. 필드는 기다린 뒤 늘 다시 읽는다(지역 변수로 들고 있지 않는다).
 * 키와 평문 볼트(unlocked)가 들어 있으므로 세션 모듈 밖으로 넘기지 않는다.
 * 원본(JS 한 스레드)처럼 한 스레드에서만 읽고 바꾼다. 잠금이 없으므로 여러 스레드 디스패처에서 부르면 안 된다
 */
internal class SessionContext(val deps: VaultSessionDeps) {
    val syncListeners = mutableSetOf<SyncChangeListener>()
    val lockListeners = mutableSetOf<() -> Unit>()
    val unlockListeners = mutableSetOf<() -> Unit>()
    val remoteListeners = mutableSetOf<() -> Unit>()

    val device = DevicePersistence(deps.deviceStore) { error -> notifySync(this, error) }
    var account: Account? = null
    var unlocked: Unlocked? = null
    var lastEmail: String? = null
    var orphan: OrphanedPending? = null

    /** 잠금·로그아웃·새 로그인마다 올린다. 기다리는 사이 값이 바뀐 작업은 결과를 버린다 */
    var epoch = 0

    /** 로그아웃·새 로그인마다 올린다. 잠금으로는 바뀌지 않는다 */
    var generation = 0

    /** 대기 내용 보내기를 한 번에 하나씩 순서대로 한다. 마지막으로 줄에 선 작업이 끝나면 완료된다 */
    var pushChain: Deferred<Unit> = CompletableDeferred(Unit)

    /** 충돌이 아닌 분명한 거절을 받으면 잠금 해제·저장·로그인 전까지 자동으로 다시 보내지 않는다 */
    var retryBlocked = false

    /** 이 탭이 마지막으로 읽거나 쓴 기기 기록. 쓰기 전에 다시 읽어 다른 탭이 그사이 썼는지 본다 (ADR-WEB-001) */
    var lastSeen: DeviceRecord? = null

    /** 기기 기록을 한 번이라도 읽었는지. 읽지 못한 채 처음 쓸 때 같은 계정의 대기 내용을 덮어쓰지 않는다 (ADR-0020) */
    var deviceRead = false

    /** 기기 기록을 쓰려고 기다리거나 쓰는 중인 횟수. 떠남 확인에 쓴다 */
    var persisting = 0
}

/**
 * 앞서 줄에 선 작업이 끝난 뒤 [block]을 실행한다. 원본의 `ctx.pushChain = run.catch(() => undefined)`와 같이
 * 줄에 서는 것은 부르는 즉시(기다리기 전에) 일어나고, 실패해도 다음 작업을 막지 않는다
 */
internal suspend fun <T> SessionContext.inPushChain(block: suspend () -> T): T {
    val previous = pushChain
    val done = CompletableDeferred<Unit>()
    pushChain = done
    try {
        previous.await()
    } catch (error: CancellationException) {
        // 기다리다 취소됐어도 뒤에 선 작업은 앞 작업이 끝난 뒤에 시작해야 한다. 원본 Promise 사슬은 취소되지 않는다
        previous.invokeOnCompletion { done.complete(Unit) }
        throw error
    }
    try {
        return block()
    } finally {
        done.complete(Unit)
    }
}

// 세션 토큰 없이 연 볼트의 동기화 객체에 넘기는 값. pushPending·syncAfterUnlock이 토큰 없이는 요청하지 않는다
internal const val NO_TOKEN = ""

internal fun isUnauthorized(error: Throwable): Boolean = error is ServerError && error.code == ServerErrorCode.UNAUTHORIZED

internal fun notify(listeners: Set<() -> Unit>) {
    for (listener in listeners.toList()) listener()
}

internal fun notifySync(ctx: SessionContext, error: Throwable?) {
    for (listener in ctx.syncListeners.toList()) listener(error)
}

internal fun ensureCurrent(ctx: SessionContext, startedAt: Int) {
    if (ctx.epoch != startedAt) throw SessionStateException("기다리는 사이 잠금 또는 로그아웃되어 작업을 취소함")
}

/** 이번 로그인에서만 기준을 바꾸는 되돌림 기억. 로그아웃·다른 로그인 뒤 늦게 온 응답은 무시한다 */
private fun memoryForThisSignIn(ctx: SessionContext): RevisionMemory {
    val revisionMemory = ctx.deps.revisionMemory
    val owner = ctx.generation
    return object : RevisionMemory {
        override fun get(): Int = revisionMemory.get()

        override fun raise(revision: Int) {
            if (ctx.generation == owner) revisionMemory.raise(revision)
        }

        override fun reset(revision: Int) {
            if (ctx.generation == owner) revisionMemory.reset(revision)
        }
    }
}

internal fun syncFor(ctx: SessionContext, accessToken: String, encryptionKey: EncryptionKey): VaultSync =
    ctx.deps.vaultSyncFor(accessToken, encryptionKey, memoryForThisSignIn(ctx))

/** 쓰지 않게 된 토큰을 서버에서 폐기한다. 기다리지 않는다. 실패해도 토큰 만료(30일)로 정리된다 (SEC-002) */
internal fun revoke(ctx: SessionContext, accessToken: String) {
    // 원본처럼 요청은 바로 보낸다(첫 중단점까지 지금 실행)
    ctx.deps.scope.launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            ctx.deps.auth.logout(accessToken)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // 폐기 요청 실패는 사용자에게 알릴 일이 아니다. 토큰은 만료로 정리된다
        }
    }
}

/** 기다리는 사이 잠금·로그아웃됐으면 새로 받은 토큰을 폐기하고 취소한다 */
internal fun ensureCurrentOrRevoke(ctx: SessionContext, startedAt: Int, newToken: String?) {
    if (ctx.epoch == startedAt) return
    if (newToken != null) revoke(ctx, newToken)
    ensureCurrent(ctx, startedAt)
}

internal suspend fun readDevice(ctx: SessionContext): DeviceRecord? = parseDeviceRecord(ctx.deps.deviceStore.load())

/** 열린 상태의 키를 은퇴시킨다. 진행 중 작업이 없으면 바로, 있으면 끝날 때 바이트를 지운다 (SEC-003) */
internal fun retireOpenKey(ctx: SessionContext) {
    ctx.unlocked?.sync?.encryptionKey?.retire()
}

/** 세션이 가져가지 않은 [key](확인용·열지 못한 키)를 은퇴시킨다. 세션이 연 키는 잠금·로그아웃 때 지운다 */
internal fun retireUnlessOpen(ctx: SessionContext, key: EncryptionKey) {
    if (ctx.unlocked?.sync?.encryptionKey !== key) key.retire()
}

internal fun clear(ctx: SessionContext) {
    val wasUnlocked = ctx.unlocked != null
    retireOpenKey(ctx)
    ctx.epoch += 1
    ctx.generation += 1
    ctx.account = null
    ctx.unlocked = null
    if (wasUnlocked) notify(ctx.lockListeners)
}

/** 이 탭이 연 계정의 KDF 값을 탭 기억에 남긴다 (SEC-003 규칙 11) */
internal fun rememberKdf(ctx: SessionContext, email: String, kdf: UnlockKdf) {
    ctx.deps.tabMemory?.set(rememberedKdf(email, kdf))
}

/**
 * 다른 곳에서 비밀번호가 바뀌어 로그인 전 상태로 바꾼다. 기기 기록은 새 비밀번호로 로그인에 성공할 때 버린다 (ADR-0020).
 * 이 탭은 이미 안내를 받으므로 탭 기억을 기기 기록의 KDF 값으로 맞춰, 새로고침할 때 같은 안내를 다시 하지 않는다 (SEC-003 규칙 11)
 */
internal fun signOutForChangedPassword(ctx: SessionContext, email: String, kdf: UnlockKdf) {
    clear(ctx)
    ctx.lastSeen = null
    rememberKdf(ctx, email, kdf)
    notify(ctx.remoteListeners)
}

/** 로그인 상태를 지운다. 기기 기록은 부른 쪽이 정하고, 탭 기억은 지운다 */
internal fun resetSignIn(ctx: SessionContext) {
    clear(ctx)
    ctx.deps.tabMemory?.clear()
    ctx.orphan = null
    ctx.deps.revisionMemory.reset(0)
    ctx.lastEmail = null
    ctx.lastSeen = null
}

/** 세션 만료. 대기 내용은 남겨 같은 계정으로 다시 로그인하면 올린다 */
internal fun expire(ctx: SessionContext): SessionExpiredException {
    val account = ctx.account
    if (account?.pendingSync == true) ctx.orphan = OrphanedPending(account.email, account.payload, account.revision)
    clear(ctx)
    val error = SessionExpiredException()
    notifySync(ctx, error)
    return error
}

/** 볼트가 열려 있으면 키와 평문 볼트를 버린다 */
internal fun lockThisTab(ctx: SessionContext): Boolean {
    ctx.epoch += 1
    if (ctx.unlocked == null) return false
    retireOpenKey(ctx)
    ctx.unlocked = null
    notify(ctx.lockListeners)
    return true
}
