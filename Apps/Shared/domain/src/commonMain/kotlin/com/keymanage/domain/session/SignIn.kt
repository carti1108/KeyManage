package com.keymanage.domain.session

import com.keymanage.domain.auth.AuthResult
import com.keymanage.domain.auth.UnlockKdf
import com.keymanage.domain.crypto.EncryptionKey
import com.keymanage.domain.crypto.openVault
import com.keymanage.domain.device.DeviceRecord
import com.keymanage.domain.tabs.TabLockName
import com.keymanage.domain.tabs.TabMessage
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.domain.vault.SyncedVault
import com.keymanage.domain.vault.VaultSync
import com.keymanage.domain.vault.VaultSyncConflictException
import com.keymanage.domain.vault.isTransientSyncError
import com.keymanage.domain.vault.mergeVaults
import com.keymanage.domain.vault.parseVaultData
import kotlin.coroutines.cancellation.CancellationException

// 로그인·가입 뒤 기기 기록을 정리하고 남은 대기 내용을 병합해 볼트를 연다 (SEC-002, VLT-002, ADR-0019, ADR-0020)
// 원본: Apps/Web/src/domain/session/signIn.ts

private class AdoptedVault(
    val vault: SyncedVault,
    val pendingSync: Boolean,
    /** 서버가 받을 수 없어 버린 대기 내용의 사유 */
    val dropped: Throwable? = null,
)

private suspend fun prepareVault(sync: VaultSync, acceptRollback: Boolean): SyncedVault {
    val vault = sync.load(acceptRollback)
    // 볼트가 없으면(가입 직후, 가입 때 저장 실패) 열기 전에 빈 볼트를 저장한다. 잠금 해제 확인에 필요하다 (SEC-003)
    return if (vault.payload == null) sync.save(vault, vault.data) else vault
}

/**
 * 세션 만료 전에 남긴 대기 내용을 서버 볼트와 병합해 올린다. 서버에 닿지 못하면 병합한 볼트를 대기로 연다.
 * 대기 내용은 볼트가 열린 뒤에야 지운다. 충돌·되돌림이면 로그인을 실패시키고 남겨 다음 로그인 때 다시 올린다.
 * 서버가 받을 수 없는 대기 내용(읽기 전용, 용량 초과 등)이면 서버 볼트로 열고 dropped로 알린다
 */
private suspend fun adoptOrphan(ctx: SessionContext, email: String, encryptionKey: EncryptionKey, sync: VaultSync, vault: SyncedVault): AdoptedVault {
    val pending = ctx.orphan
    if (pending == null || pending.email != email) return AdoptedVault(vault, pendingSync = false)
    val data = try {
        parseVaultData(openVault(encryptionKey, pending.payload))
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // 열 수 없는 대기 내용(다른 키로 만든 것 등)은 버린다
        return AdoptedVault(vault, pendingSync = false)
    }
    val merged = mergeVaults(data, vault.data)
    try {
        try {
            return AdoptedVault(sync.save(vault, merged), pendingSync = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!isTransientSyncError(error)) throw error
            return AdoptedVault(SyncedVault(vault.revision, merged, readOnly = false, payload = sync.seal(merged)), pendingSync = true)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        if (error is VaultSyncConflictException || error is RevisionRollbackException || isUnauthorized(error)) throw error
        return AdoptedVault(vault, pendingSync = false, dropped = error)
    }
}

/**
 * 로그인 직전에 탭 간 잠금 안에서 기기 기록을 다시 읽는다. 다른 계정의 기록이면 지운다. 같은 계정의 대기 내용이 있으면
 * 세션 만료로 남긴 것처럼 받아 병합해 올리고, KDF 값이 바뀌었으면(비밀번호 변경) 버린다 (ADR-0019, ADR-0020, ADR-WEB-001)
 */
private suspend fun prepareDeviceForSignIn(ctx: SessionContext, email: String, kdf: UnlockKdf, startedAt: Int) {
    val record: DeviceRecord? = try {
        readDevice(ctx).also {
            ctx.deviceRead = true
            ctx.lastSeen = it
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // 읽지 못하면 마지막으로 본 기록으로 판단한다. 쓰기도 실패할 가능성이 크며, 실패는 쓸 때 알린다
        ctx.lastSeen
    }
    ensureCurrent(ctx, startedAt)
    if (record == null) return
    if (record.email != email) {
        // 다른 계정의 인증이 성공했다. 볼트를 준비하지 못하더라도 이전 계정의 기록을 남기지 않는다 (ADR-0019)
        ctx.lastSeen = null
        ctx.device.clear()
        // 이전 계정으로 열린 다른 탭은 모두 로그아웃한다
        ctx.deps.tabs.channel.post(TabMessage.LOGGED_OUT)
        return
    }
    if (record.kdf != kdf) {
        // 비밀번호가 바뀌었다. 옛 키의 대기 내용은 열 수 없으니 버린다. 되돌림 검사는 그대로 해서,
        // 서버가 KDF 값만 바꿔 유실을 감추고 기기 사본을 덮어쓰게 하지 못한다. 기기 사본은 볼트를 연 뒤에야 바뀐다 (ADR-0019)
        ctx.orphan = null
        return
    }
    ctx.deps.revisionMemory.raise(record.lastSeenRevision)
    if (record.pendingSync && ctx.orphan == null) ctx.orphan = OrphanedPending(email, record.payload, record.revision)
}

/** 로그인 상태를 지우고 새 로그인을 시작한다. @return 이 로그인의 epoch */
internal fun beginSignIn(ctx: SessionContext, email: String): Int {
    clear(ctx)
    // 다른 계정의 revision을 기준으로 되돌림을 판정하지 않도록 계정이 바뀌면 지운다
    if (ctx.lastEmail != email) ctx.deps.revisionMemory.reset(0)
    ctx.lastEmail = email
    ctx.retryBlocked = false
    return ctx.epoch
}

/** 인증 결과로 기기 기록을 정리하고 서버 볼트를 준비해 연다 */
internal suspend fun signIn(ctx: SessionContext, email: String, startedAt: Int, result: AuthResult, acceptRollback: Boolean) {
    try {
        openSignedIn(ctx, email, startedAt, result, acceptRollback)
    } finally {
        // 열지 못하고 끝났으면(되돌림 확인·취소·오류) 키를 지운다 (SEC-003)
        retireUnlessOpen(ctx, result.encryptionKey)
    }
}

private suspend fun openSignedIn(ctx: SessionContext, email: String, startedAt: Int, result: AuthResult, acceptRollback: Boolean) {
    ensureCurrent(ctx, startedAt)
    ctx.deps.tabs.lock.run(TabLockName.DEVICE) { prepareDeviceForSignIn(ctx, email, result.kdf, startedAt) }
    ensureCurrent(ctx, startedAt)
    val sync = syncFor(ctx, result.session.accessToken, result.encryptionKey)
    val prepared = prepareVault(sync, acceptRollback)
    ensureCurrent(ctx, startedAt)
    val adopted = adoptOrphan(ctx, email, result.encryptionKey, sync, prepared)
    ensureCurrent(ctx, startedAt)
    open(ctx, email, result.session.accessToken, result.kdf, sync, adopted.vault, adopted.pendingSync)
    // 올렸거나, 이 볼트의 대기로 옮겼거나, 서버가 받을 수 없어 버렸다. 다른 계정의 대기 내용이면 그 계정이 열렸으므로 버린다 (VLT-002)
    ctx.orphan = null
    adopted.dropped?.let { notifySync(ctx, it) }
}
