// 계정 기록을 기기에 쓰고 다른 탭이 쓴 기록을 따르거나 병합한다 (ADR-0019, ADR-0020, ADR-WEB-001)
import { MasterPasswordChangedError, type UnlockKdf } from '../auth/authService'
import { VaultDecryptError } from '../crypto/vaultCipher'
import { reconcileDeviceRecord, sameKdf, samePayload, sameRecord } from '../device/reconcileDeviceRecord'
import { DEVICE_RECORD_VERSION, DeviceStorageError, type DeviceRecord } from '../device/deviceRecord'
import type { TabMessageKind } from '../tabs/tabs'
import { isReadOnly } from '../vault/vaultData'
import type { SyncedVault, VaultSync } from '../vault/vaultSync'
import { mergeRecordIntoMemory, type MergedVault } from './recordMerge'
import { clear, lockThisTab, notify, notifySync, readDevice, resetSignIn, revoke, type SessionContext } from './sessionContext'
import { SessionStateError } from './sessionTypes'

/** 다른 탭의 로그아웃·계정 전환을 따른다. 기기 기록은 건드리지 않고, 이 탭의 토큰은 서버에서 폐기한다 (ADR-WEB-001) */
function signOutLocally(ctx: SessionContext) {
  const token = ctx.account?.accessToken ?? null
  resetSignIn(ctx)
  if (token !== null) revoke(ctx, token)
  notify(ctx.remoteListeners)
}

/** 다른 탭에서 바뀐 비밀번호로 로그인했다. 쓰지 않고 로그인 화면으로 보낸다 (SEC-003) */
function passwordChangedElsewhere(ctx: SessionContext) {
  clear(ctx)
  ctx.lastSeen = null
  notifySync(ctx, new MasterPasswordChangedError())
  notify(ctx.remoteListeners)
}

/** 메모리의 계정 기록을 쓴다. 기기 기록이 이미 같으면 쓰지 않는다. 탭 간 잠금 안에서만 부른다 */
export async function writeLocked(ctx: SessionContext, current: DeviceRecord | null): Promise<void> {
  if (ctx.account === null) return
  const { email, kdf, payload, revision, pendingSync } = ctx.account
  const record: DeviceRecord = { version: DEVICE_RECORD_VERSION, email, kdf, payload, revision, lastSeenRevision: ctx.deps.revisionMemory.get(), pendingSync }
  // 볼트는 5MB까지 크므로 바뀐 것이 없으면(잠금 해제 직후 같은 볼트 등) 다시 쓰지 않는다
  if (current !== null && !ctx.device.hasFailed() && sameRecord(current, record)) {
    ctx.lastSeen = current
    return
  }
  const saved = await ctx.device.save(record)
  ctx.lastSeen = saved ? record : current
  if (saved) ctx.deps.tabs.channel.post('record-changed')
}

/**
 * 다른 탭이 쓴 기록을 메모리 볼트와 합친다. 탭 간 잠금 안에서만 부른다
 * @returns 합친 결과를 메모리에 반영했는지. 합치는 사이 이 탭에서 또 고쳤으면 그 쓰기가 다시 합친다
 */
async function mergeLocked(ctx: SessionContext, theirs: DeviceRecord): Promise<boolean> {
  const { revisionMemory } = ctx.deps
  const base = ctx.account
  const opened = ctx.unlocked
  if (base === null || opened === null) return false
  const startedGeneration = ctx.generation
  let merged: MergedVault
  try {
    const mine = { data: opened.vault.data, payload: base.payload, revision: base.revision, lastSeenRevision: revisionMemory.get(), pendingSync: base.pendingSync }
    merged = await mergeRecordIntoMemory(mine, theirs, opened.sync)
  } catch (error) {
    if (error instanceof VaultDecryptError) passwordChangedElsewhere(ctx)
    else ctx.device.markFailed(error)
    return false
  }
  if (ctx.account !== base || ctx.generation !== startedGeneration) return false
  revisionMemory.raise(merged.lastSeenRevision)
  if (merged.payload === base.payload && merged.revision === base.revision && merged.pendingSync === base.pendingSync) return true
  ctx.account = { ...base, payload: merged.payload, revision: merged.revision, pendingSync: merged.pendingSync }
  const vault: SyncedVault = { revision: merged.revision, data: merged.data, readOnly: isReadOnly(merged.data), payload: merged.payload }
  // 합치는 사이 잠겼으면 키를 버린 채 암호화된 결과만 남긴다
  if (ctx.unlocked !== null) ctx.unlocked = { ...ctx.unlocked, vault, pendingSync: merged.pendingSync }
  if (merged.changed) notify(ctx.remoteListeners)
  return true
}

async function persistLocked(ctx: SessionContext): Promise<void> {
  if (ctx.account === null) return
  let current: DeviceRecord | null
  try {
    current = await readDevice(ctx)
  } catch (error) {
    ctx.device.markFailed(error)
    return
  }
  const target = ctx.account
  if (target === null) return
  if (!ctx.deviceRead && current !== null && current.email === target.email && current.pendingSync && !samePayload(current.payload, target.payload)) {
    // 시작할 때 읽지 못한 기록의 대기 내용은 지금 연 볼트에 병합하지 못했다. 덮어쓰지 않고 남겨, 새로고침하면 잠금 해제 때 병합해 올린다 (ADR-0020)
    ctx.device.markFailed(new DeviceStorageError(new Error('기기에 남은 대기 내용을 덮어쓰지 않음')))
    return
  }
  ctx.deviceRead = true
  const decision = reconcileDeviceRecord({ current, lastSeen: ctx.lastSeen, email: target.email, kdf: target.kdf, unlocked: ctx.unlocked !== null })
  if (decision.kind === 'loggedOutElsewhere') return signOutLocally(ctx)
  if (decision.kind === 'passwordChanged') return passwordChangedElsewhere(ctx)
  if (decision.kind === 'skip') return
  if (decision.kind === 'merge' && !(await mergeLocked(ctx, decision.record))) return
  await writeLocked(ctx, current)
}

/**
 * 계정 기록을 기기에 쓴다. 다른 탭과 겹치지 않게 탭 간 잠금 안에서 기록을 다시 읽고, 다른 탭이 그사이 썼으면 병합해 쓴다.
 * 다른 계정의 기록은 로그인할 때 지운다 (한 계정만, ADR-0019, ADR-WEB-001). 실패는 알리고 거절하지 않는다
 */
export async function persist(ctx: SessionContext): Promise<void> {
  if (ctx.account === null) return
  ctx.persisting += 1
  try {
    await ctx.deps.tabs.lock.run('device', () => persistLocked(ctx))
  } finally {
    ctx.persisting -= 1
  }
}

/**
 * 잠긴 탭이 기기 기록을 다시 읽어 다른 탭의 로그아웃·계정 전환·비밀번호 변경을 따른다 (ADR-WEB-001).
 * replaceMemory면 암호화 볼트·revision·대기 표시도 기록 값으로 바꾼다. 단 메모리가 마지막으로 본 기록 그대로이고
 * 기기 쓰기가 밀려 있지 않을 때만 바꾼다. 기기에 쓰지 못한 대기 내용은 잠금을 풀 때 키로 병합한다.
 * 기록을 읽지 못하면 메모리 값을 그대로 둔다
 */
export async function refreshLockedFromDevice(ctx: SessionContext, replaceMemory: boolean): Promise<void> {
  await ctx.deps.tabs.lock.run('device', async () => {
    const target = ctx.account
    if (target === null || ctx.unlocked !== null) return
    let record: DeviceRecord | null
    try {
      record = await readDevice(ctx)
    } catch {
      return
    }
    if (ctx.account !== target || ctx.unlocked !== null) return
    if (record === null) {
      // 이 탭이 본 기록이 사라졌다. 다른 탭에서 로그아웃했다
      if (ctx.lastSeen !== null) signOutLocally(ctx)
      return
    }
    if (record.email !== target.email) return signOutLocally(ctx)
    if (!sameKdf(record.kdf, target.kdf)) return passwordChangedElsewhere(ctx)
    if (!replaceMemory || ctx.lastSeen === null || !samePayload(ctx.lastSeen.payload, target.payload) || ctx.device.isBehind()) return
    ctx.lastSeen = record
    ctx.deps.revisionMemory.raise(record.lastSeenRevision)
    if (samePayload(record.payload, target.payload) && record.revision === target.revision && record.pendingSync === target.pendingSync) return
    ctx.account = { ...target, payload: record.payload, revision: record.revision, pendingSync: record.pendingSync }
    notify(ctx.remoteListeners)
  })
}

/** 다른 탭이 보낸 알림. 메시지는 믿지 않고 필요한 값은 기기 기록에서 직접 읽는다 (ADR-WEB-001) */
export function onTabMessage(ctx: SessionContext, kind: TabMessageKind) {
  // 로그인 전(로그인 중 포함) 탭은 다른 탭의 알림을 따르지 않는다
  if (ctx.account === null) return
  if (kind === 'record-changed') void (ctx.unlocked === null ? refreshLockedFromDevice(ctx, true) : persist(ctx))
  else if (kind === 'locked') {
    if (lockThisTab(ctx)) notify(ctx.remoteListeners)
  } else if (kind === 'logged-out') signOutLocally(ctx)
}

/**
 * 볼트를 연 상태로 계정과 잠금 해제 값을 바꾸고 기기에 쓴다
 * @returns 기기 기록 쓰기. 실패해도 거절하지 않는다
 */
export function open(ctx: SessionContext, email: string, accessToken: string | null, kdf: UnlockKdf, sync: VaultSync, vault: SyncedVault, pendingSync = false): Promise<void> {
  // 모든 계정이 볼트를 가지므로 여기서 payload가 없을 수 없다 (SEC-003)
  if (vault.payload === null) throw new SessionStateError('볼트 없이 잠금을 해제할 수 없음')
  const wasLocked = ctx.unlocked === null
  ctx.account = { email, accessToken, kdf, payload: vault.payload, revision: vault.revision, pendingSync }
  ctx.unlocked = { sync, vault, pendingSync }
  if (wasLocked) notify(ctx.unlockListeners)
  return persist(ctx)
}
