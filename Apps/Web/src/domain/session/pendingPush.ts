// 동기화 대기 내용을 순서대로 서버에 보내고, 올린 볼트를 기기에 대기 없이 남긴다 (VLT-002)
import { samePayload, sameRecord } from '../device/reconcileDeviceRecord'
import type { DeviceRecord } from '../device/deviceRecord'
import { VaultSyncConflictError, isTransientSyncError, type SyncedVault } from '../vault/vaultSync'
import { persist, writeLocked } from './deviceSync'
import { expire, isUnauthorized, readDevice, type SessionContext } from './sessionContext'

export type PushResult = 'synced' | 'idle' | 'stillPending'

/**
 * 서버에 올린 볼트를 기기에 대기 없이 남긴다. 보내는 사이 이 탭에서 또 고쳤거나 다른 탭이 기록을 썼으면 남기지 않는다.
 * 탭 간 잠금 안에서만 부른다
 * @returns 남겼는지
 */
async function commitPushedLocked(ctx: SessionContext, pushed: SyncedVault, saved: SyncedVault, startedAt: number): Promise<boolean> {
  let current: DeviceRecord | null
  try {
    current = await readDevice(ctx)
  } catch (error) {
    ctx.device.markFailed(error)
    return false
  }
  const target = ctx.account
  if (target === null || pushed.payload === null || saved.payload === null) return false
  const { lastSeen } = ctx
  const untouched = current === null ? lastSeen === null : samePayload(current.payload, pushed.payload) || (lastSeen !== null && sameRecord(current, lastSeen))
  if (!untouched) return false
  if (ctx.unlocked === null) {
    // 보내는 사이 잠겼다. 잠기기 전에 더 고친 것이 없으면 올린 볼트를 잠긴 상태에 남긴다
    if (target.payload !== pushed.payload) return false
  } else {
    if (ctx.epoch !== startedAt || ctx.unlocked.vault !== pushed) return false
    ctx.unlocked = { sync: ctx.unlocked.sync, vault: saved, pendingSync: false }
  }
  ctx.account = { ...target, payload: saved.payload, revision: saved.revision, pendingSync: false }
  await writeLocked(ctx, current)
  return true
}

/**
 * 대기 내용을 한 번 보낸다. 서버에 닿지 못하면 대기를 유지하고 조용히 끝난다
 * @throws SessionExpiredError, 그 밖에 VaultSync.save의 분명한 거절
 */
async function pushPending(ctx: SessionContext): Promise<PushResult> {
  if (ctx.account === null || ctx.unlocked === null || !ctx.unlocked.pendingSync) return 'idle'
  // 세션 없이 열었으면 비밀번호를 다시 입력받아 로그인할 때까지 보내지 않는다 (SEC-003)
  if (ctx.account.accessToken === null) return 'stillPending'
  const startedAt = ctx.epoch
  const startedGeneration = ctx.generation
  const { sync, vault } = ctx.unlocked
  let saved: SyncedVault
  try {
    saved = await sync.save(vault, vault.data)
  } catch (error) {
    if (isTransientSyncError(error)) return 'stillPending'
    // 그사이 로그아웃·다른 로그인이 있었으면 이전 세션의 실패를 지금 세션에 알리지 않는다
    if (ctx.generation !== startedGeneration) return 'idle'
    if (isUnauthorized(error)) throw expire(ctx)
    // 잠금 전에 보낸 요청의 늦은 거절이 다시 연 세션의 재전송을 막지 않게 한다
    if (ctx.epoch === startedAt && !(error instanceof VaultSyncConflictError)) ctx.retryBlocked = true
    throw error
  }
  if (ctx.generation !== startedGeneration || ctx.account === null) return 'idle'
  if (await ctx.deps.tabs.lock.run('device', () => commitPushedLocked(ctx, vault, saved, startedAt))) return 'synced'
  if (ctx.generation !== startedGeneration || ctx.account === null || ctx.unlocked === null) return 'idle'
  // 보내는 사이 이 탭에서 또 고쳤거나 다른 탭이 기록을 썼다. 합친 내용은 대기로 남아 다음 차례에 보낸다. 서버가 앞서 있으니 병합 절차를 탄다
  await persist(ctx)
  return 'stillPending'
}

/** 앞선 보내기가 끝난 뒤 대기 내용을 보낸다 */
export function enqueuePush(ctx: SessionContext): Promise<PushResult> {
  const run = ctx.pushChain.then(() => pushPending(ctx))
  ctx.pushChain = run.catch(() => undefined)
  return run
}
