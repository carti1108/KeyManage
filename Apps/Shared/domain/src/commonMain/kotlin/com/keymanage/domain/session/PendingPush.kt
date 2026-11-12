package com.keymanage.domain.session

import com.keymanage.domain.tabs.TabLockName
import com.keymanage.domain.vault.SyncedVault
import com.keymanage.domain.vault.VaultSyncConflictException
import com.keymanage.domain.vault.isTransientSyncError
import kotlin.coroutines.cancellation.CancellationException

// 동기화 대기 내용을 순서대로 서버에 보내고, 올린 볼트를 기기에 대기 없이 남긴다 (VLT-002)
// 원본: Apps/Web/src/domain/session/pendingPush.ts

internal enum class PushResult { SYNCED, IDLE, STILL_PENDING }

/**
 * 서버에 올린 볼트를 기기에 대기 없이 남긴다. 보내는 사이 이 탭에서 또 고쳤거나 다른 탭이 기록을 썼으면 남기지 않는다.
 * 탭 간 잠금 안에서만 부른다
 * @return 남겼는지
 */
private suspend fun commitPushedLocked(ctx: SessionContext, pushed: SyncedVault, saved: SyncedVault, startedAt: Int): Boolean {
    val current = try {
        readDevice(ctx)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        ctx.device.markFailed(error)
        return false
    }
    val target = ctx.account
    val pushedPayload = pushed.payload
    val savedPayload = saved.payload
    if (target == null || pushedPayload == null || savedPayload == null) return false
    val lastSeen = ctx.lastSeen
    val untouched = if (current == null) lastSeen == null else current.payload == pushedPayload || (lastSeen != null && current == lastSeen)
    if (!untouched) return false
    val opened = ctx.unlocked
    if (opened == null) {
        // 보내는 사이 잠겼다. 잠기기 전에 더 고친 것이 없으면 올린 볼트를 잠긴 상태에 남긴다
        if (target.payload !== pushedPayload) return false
    } else {
        if (ctx.epoch != startedAt || opened.vault !== pushed) return false
        ctx.unlocked = Unlocked(opened.sync, saved, pendingSync = false)
    }
    ctx.account = target.copy(payload = savedPayload, revision = saved.revision, pendingSync = false)
    writeLocked(ctx, current)
    return true
}

/**
 * 대기 내용을 한 번 보낸다. 서버에 닿지 못하면 대기를 유지하고 조용히 끝난다
 * @throws SessionExpiredException, 그 밖에 VaultSync.save의 분명한 거절
 */
private suspend fun pushPending(ctx: SessionContext): PushResult {
    val opened = ctx.unlocked
    val account = ctx.account
    if (account == null || opened == null || !opened.pendingSync) return PushResult.IDLE
    // 세션 없이 열었으면 비밀번호를 다시 입력받아 로그인할 때까지 보내지 않는다 (SEC-003)
    if (account.accessToken == null) return PushResult.STILL_PENDING
    val startedAt = ctx.epoch
    val startedGeneration = ctx.generation
    val vault = opened.vault
    val saved = try {
        opened.sync.save(vault, vault.data)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        if (isTransientSyncError(error)) return PushResult.STILL_PENDING
        // 그사이 로그아웃·다른 로그인이 있었으면 이전 세션의 실패를 지금 세션에 알리지 않는다
        if (ctx.generation != startedGeneration) return PushResult.IDLE
        if (isUnauthorized(error)) throw expire(ctx)
        // 잠금 전에 보낸 요청의 늦은 거절이 다시 연 세션의 재전송을 막지 않게 한다
        if (ctx.epoch == startedAt && error !is VaultSyncConflictException) ctx.retryBlocked = true
        throw error
    }
    if (ctx.generation != startedGeneration || ctx.account == null) return PushResult.IDLE
    if (ctx.deps.tabs.lock.run(TabLockName.DEVICE) { commitPushedLocked(ctx, vault, saved, startedAt) } == true) return PushResult.SYNCED
    if (ctx.generation != startedGeneration || ctx.account == null || ctx.unlocked == null) return PushResult.IDLE
    // 보내는 사이 이 탭에서 또 고쳤거나 다른 탭이 기록을 썼다. 합친 내용은 대기로 남아 다음 차례에 보낸다. 서버가 앞서 있으니 병합 절차를 탄다
    persist(ctx)
    return PushResult.STILL_PENDING
}

/** 앞선 보내기가 끝난 뒤 대기 내용을 보낸다 */
internal suspend fun enqueuePush(ctx: SessionContext): PushResult = ctx.inPushChain { pushPending(ctx) }
