package com.keymanage.domain.session

import com.keymanage.domain.auth.MasterPasswordChangedException
import com.keymanage.domain.auth.UnlockKdf
import com.keymanage.domain.crypto.VaultDecryptException
import com.keymanage.domain.crypto.WipedKeyException
import com.keymanage.domain.device.DeviceRecord
import com.keymanage.domain.device.DeviceStorageException
import com.keymanage.domain.device.Reconciliation
import com.keymanage.domain.device.reconcileDeviceRecord
import com.keymanage.domain.tabs.TabLockName
import com.keymanage.domain.tabs.TabMessage
import com.keymanage.domain.vault.SyncedVault
import com.keymanage.domain.vault.VaultSync
import com.keymanage.domain.vault.isReadOnly
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// 계정 기록을 기기에 쓰고 다른 탭이 쓴 기록을 따르거나 병합한다 (ADR-0019, ADR-0020, ADR-WEB-001)
// 원본: Apps/Web/src/domain/session/deviceSync.ts

/** 다른 탭의 로그아웃·계정 전환을 따른다. 기기 기록은 건드리지 않고, 이 탭의 토큰은 서버에서 폐기한다 (ADR-WEB-001) */
private fun signOutLocally(ctx: SessionContext) {
    val token = ctx.account?.accessToken
    resetSignIn(ctx)
    if (token != null) revoke(ctx, token)
    notify(ctx.remoteListeners)
}

/** 다른 탭에서 바뀐 비밀번호로 로그인했다. 쓰지 않고 로그인 화면으로 보낸다 (SEC-003) */
private fun passwordChangedElsewhere(ctx: SessionContext, record: DeviceRecord) {
    signOutForChangedPassword(ctx, record.email, record.kdf)
    notifySync(ctx, MasterPasswordChangedException())
}

/**
 * 메모리의 계정 기록을 쓴다. 기기 기록이 이미 같으면 쓰지 않는다. 탭 간 잠금 안에서만 부른다
 * @return 기기 기록이 메모리의 계정과 같아졌는지(이미 같았거나 썼음)
 */
internal suspend fun writeLocked(ctx: SessionContext, current: DeviceRecord?): Boolean {
    val account = ctx.account ?: return false
    val record = DeviceRecord(account.email, account.kdf, account.payload, account.revision, ctx.deps.revisionMemory.get(), account.pendingSync)
    // 볼트는 5MB까지 크므로 바뀐 것이 없으면(잠금 해제 직후 같은 볼트 등) 다시 쓰지 않는다
    if (current != null && !ctx.device.hasFailed() && current == record) {
        ctx.lastSeen = current
        return true
    }
    val saved = ctx.device.save(record)
    ctx.lastSeen = if (saved) record else current
    if (saved) ctx.deps.tabs.channel.post(TabMessage.RECORD_CHANGED)
    return saved
}

/**
 * 다른 탭이 쓴 기록을 메모리 볼트와 합친다. 탭 간 잠금 안에서만 부른다
 * @return 합친 결과를 메모리에 반영했는지. 합치는 사이 이 탭에서 또 고쳤으면 그 쓰기가 다시 합친다
 */
private suspend fun mergeLocked(ctx: SessionContext, theirs: DeviceRecord): Boolean {
    val revisionMemory = ctx.deps.revisionMemory
    val base = ctx.account
    val opened = ctx.unlocked
    if (base == null || opened == null) return false
    val startedGeneration = ctx.generation
    val merged = try {
        val mine = MemoryVault(opened.vault.data, base.payload, base.revision, revisionMemory.get(), base.pendingSync)
        mergeRecordIntoMemory(mine, theirs, opened.sync)
    } catch (error: CancellationException) {
        throw error
    } catch (error: WipedKeyException) {
        // 합치기 전에 잠겨 키가 이미 지워졌다. 기기 저장 실패가 아니다. 잠긴 상태는 기록의 암호문만 쓴다 (SEC-003)
        return false
    } catch (error: Exception) {
        if (error is VaultDecryptException) passwordChangedElsewhere(ctx, theirs) else ctx.device.markFailed(error)
        return false
    }
    if (ctx.account !== base || ctx.generation != startedGeneration) return false
    revisionMemory.raise(merged.lastSeenRevision)
    if (merged.payload === base.payload && merged.revision == base.revision && merged.pendingSync == base.pendingSync) return true
    ctx.account = base.copy(payload = merged.payload, revision = merged.revision, pendingSync = merged.pendingSync)
    val vault = SyncedVault(merged.revision, merged.data, isReadOnly(merged.data), merged.payload)
    // 합치는 사이 잠겼으면 키를 버린 채 암호화된 결과만 남긴다
    ctx.unlocked?.let { ctx.unlocked = Unlocked(it.sync, vault, merged.pendingSync) }
    if (merged.changed) notify(ctx.remoteListeners)
    return true
}

private suspend fun persistLocked(ctx: SessionContext) {
    if (ctx.account == null) return
    val current = try {
        readDevice(ctx)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        ctx.device.markFailed(error)
        return
    }
    val target = ctx.account ?: return
    if (!ctx.deviceRead && current != null && current.email == target.email && current.pendingSync && current.payload != target.payload) {
        // 시작할 때 읽지 못한 기록의 대기 내용은 지금 연 볼트에 병합하지 못했다. 덮어쓰지 않고 남겨, 새로고침하면 잠금 해제 때 병합해 올린다 (ADR-0020)
        ctx.device.markFailed(DeviceStorageException(IllegalStateException("기기에 남은 대기 내용을 덮어쓰지 않음")))
        return
    }
    ctx.deviceRead = true
    when (val decision = reconcileDeviceRecord(current, ctx.lastSeen, target.email, target.kdf, unlocked = ctx.unlocked != null)) {
        Reconciliation.LoggedOutElsewhere -> return signOutLocally(ctx)
        Reconciliation.PasswordChanged -> return if (current != null) passwordChangedElsewhere(ctx, current) else Unit
        Reconciliation.Skip -> return
        is Reconciliation.Merge -> if (!mergeLocked(ctx, decision.record)) return
        Reconciliation.Write -> Unit
    }
    writeLocked(ctx, current)
}

/**
 * 계정 기록을 기기에 쓴다. 다른 탭과 겹치지 않게 탭 간 잠금 안에서 기록을 다시 읽고, 다른 탭이 그사이 썼으면 병합해 쓴다.
 * 다른 계정의 기록은 로그인할 때 지운다 (한 계정만, ADR-0019, ADR-WEB-001). 실패는 알리고 던지지 않는다
 */
internal suspend fun persist(ctx: SessionContext) {
    if (ctx.account == null) return
    ctx.persisting += 1
    try {
        ctx.deps.tabs.lock.run(TabLockName.DEVICE) { persistLocked(ctx) }
    } finally {
        ctx.persisting -= 1
    }
}

internal enum class LockedRefresh { PASSWORD_CHANGED }

/**
 * 잠긴 탭이 기기 기록을 다시 읽어 다른 탭의 로그아웃·계정 전환·비밀번호 변경을 따른다 (ADR-WEB-001).
 * [replaceMemory]면 암호화 볼트·revision·대기 표시도 기록 값으로 바꾼다. 단 메모리가 마지막으로 본 기록 그대로이고
 * 기기 쓰기가 밀려 있지 않을 때만 바꾼다. 기기에 쓰지 못한 대기 내용은 잠금을 풀 때 키로 병합한다.
 * 기록을 읽지 못하면 메모리 값을 그대로 둔다
 * @return 비밀번호가 바뀌어 로그아웃 상태로 바꿨으면 PASSWORD_CHANGED
 */
internal suspend fun refreshLockedFromDevice(ctx: SessionContext, replaceMemory: Boolean): LockedRefresh? {
    var outcome: LockedRefresh? = null
    ctx.deps.tabs.lock.run(TabLockName.DEVICE) {
        val target = ctx.account
        if (target == null || ctx.unlocked != null) return@run
        val record = try {
            readDevice(ctx)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return@run
        }
        if (ctx.account !== target || ctx.unlocked != null) return@run
        if (record == null) {
            // 이 탭이 본 기록이 사라졌다. 다른 탭에서 로그아웃했다
            if (ctx.lastSeen != null) signOutLocally(ctx)
            return@run
        }
        if (record.email != target.email) return@run signOutLocally(ctx)
        if (record.kdf != target.kdf) {
            outcome = LockedRefresh.PASSWORD_CHANGED
            return@run passwordChangedElsewhere(ctx, record)
        }
        val lastSeen = ctx.lastSeen
        if (!replaceMemory || lastSeen == null || lastSeen.payload != target.payload || ctx.device.isBehind()) return@run
        ctx.lastSeen = record
        ctx.deps.revisionMemory.raise(record.lastSeenRevision)
        if (record.payload == target.payload && record.revision == target.revision && record.pendingSync == target.pendingSync) return@run
        ctx.account = target.copy(payload = record.payload, revision = record.revision, pendingSync = record.pendingSync)
        notify(ctx.remoteListeners)
    }
    return outcome
}

/** 다른 탭이 보낸 알림. 메시지는 믿지 않고 필요한 값은 기기 기록에서 직접 읽는다 (ADR-WEB-001) */
internal fun onTabMessage(ctx: SessionContext, message: TabMessage) {
    // 로그인 전(로그인 중 포함) 탭은 다른 탭의 알림을 따르지 않는다
    if (ctx.account == null) return
    when (message) {
        TabMessage.RECORD_CHANGED -> ctx.deps.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (ctx.unlocked == null) refreshLockedFromDevice(ctx, replaceMemory = true) else persist(ctx)
        }
        TabMessage.LOCKED -> if (lockThisTab(ctx)) notify(ctx.remoteListeners)
        TabMessage.LOGGED_OUT -> signOutLocally(ctx)
        TabMessage.ACTIVITY -> Unit
    }
}

/**
 * 볼트를 연 상태로 계정과 잠금 해제 값을 바꾸고 기기에 쓴다. 기기 쓰기가 실패해도 던지지 않는다
 * @throws SessionStateException 볼트 없이 열려고 함
 */
internal suspend fun open(
    ctx: SessionContext,
    email: String,
    accessToken: String?,
    kdf: UnlockKdf,
    sync: VaultSync,
    vault: SyncedVault,
    pendingSync: Boolean = false,
) {
    // 모든 계정이 볼트를 가지므로 여기서 payload가 없을 수 없다 (SEC-003)
    val payload = vault.payload ?: throw SessionStateException("볼트 없이 잠금을 해제할 수 없음")
    val wasLocked = ctx.unlocked == null
    // 다른 키로 다시 열면(재연결 등) 옛 키는 더 쓰지 않는다
    if (ctx.unlocked?.sync?.encryptionKey !== sync.encryptionKey) retireOpenKey(ctx)
    ctx.account = Account(email, accessToken, kdf, payload, vault.revision, pendingSync)
    ctx.unlocked = Unlocked(sync, vault, pendingSync)
    // 로그인·가입·잠금 해제·재연결로 연 계정을 이 탭이 기억한다
    rememberKdf(ctx, email, kdf)
    if (wasLocked) notify(ctx.unlockListeners)
    persist(ctx)
}
