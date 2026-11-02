// 로그인·가입 뒤 기기 기록을 정리하고 남은 대기 내용을 병합해 볼트를 연다 (SEC-002, VLT-002, ADR-0019, ADR-0020)
import type { AuthResult, UnlockKdf } from '../auth/authService'
import { openVault } from '../crypto/vaultCipher'
import { sameKdf } from '../device/reconcileDeviceRecord'
import type { DeviceRecord } from '../device/deviceRecord'
import { mergeVaults } from '../vault/mergeVaults'
import { parseVaultData, type VaultData } from '../vault/vaultData'
import { RevisionRollbackError, VaultSyncConflictError, isTransientSyncError, type SyncedVault, type VaultSync } from '../vault/vaultSync'
import { open } from './deviceSync'
import { clear, ensureCurrent, isUnauthorized, notifySync, readDevice, syncFor, type SessionContext } from './sessionContext'
import type { RollbackOption } from './sessionTypes'

interface AdoptedVault {
  readonly vault: SyncedVault
  readonly pendingSync: boolean
  /** 서버가 받을 수 없어 버린 대기 내용의 사유 */
  readonly dropped?: unknown
}

async function prepareVault(sync: VaultSync, options: RollbackOption): Promise<SyncedVault> {
  const vault = await sync.load(options)
  // 볼트가 없으면(가입 직후, 가입 때 저장 실패) 열기 전에 빈 볼트를 저장한다. 잠금 해제 확인에 필요하다 (SEC-003)
  return vault.payload === null ? sync.save(vault, vault.data) : vault
}

/**
 * 세션 만료 전에 남긴 대기 내용을 서버 볼트와 병합해 올린다. 서버에 닿지 못하면 병합한 볼트를 대기로 연다.
 * 대기 내용은 볼트가 열린 뒤에야 지운다. 충돌·되돌림이면 로그인을 실패시키고 남겨 다음 로그인 때 다시 올린다.
 * 서버가 받을 수 없는 대기 내용(읽기 전용, 용량 초과 등)이면 서버 볼트로 열고 dropped로 알린다
 */
async function adoptOrphan(ctx: SessionContext, email: string, encryptionKey: CryptoKey, sync: VaultSync, vault: SyncedVault): Promise<AdoptedVault> {
  const pending = ctx.orphan
  if (pending === null || pending.email !== email) return { vault, pendingSync: false }
  let data: VaultData
  try {
    data = parseVaultData(await openVault(encryptionKey, pending.payload))
  } catch {
    // 열 수 없는 대기 내용(다른 키로 만든 것 등)은 버린다
    return { vault, pendingSync: false }
  }
  const merged = mergeVaults(data, vault.data)
  try {
    try {
      return { vault: await sync.save(vault, merged), pendingSync: false }
    } catch (error) {
      if (!isTransientSyncError(error)) throw error
      return { vault: { revision: vault.revision, data: merged, readOnly: false, payload: await sync.seal(merged) }, pendingSync: true }
    }
  } catch (error) {
    if (error instanceof VaultSyncConflictError || error instanceof RevisionRollbackError || isUnauthorized(error)) throw error
    return { vault, pendingSync: false, dropped: error }
  }
}

/**
 * 로그인 직전에 탭 간 잠금 안에서 기기 기록을 다시 읽는다. 다른 계정의 기록이면 지운다. 같은 계정의 대기 내용이 있으면
 * 세션 만료로 남긴 것처럼 받아 병합해 올리고, KDF 값이 바뀌었으면(비밀번호 변경) 버린다 (ADR-0019, ADR-0020, ADR-WEB-001)
 */
async function prepareDeviceForSignIn(ctx: SessionContext, email: string, kdf: UnlockKdf, startedAt: number): Promise<void> {
  let record: DeviceRecord | null
  try {
    record = await readDevice(ctx)
    ctx.deviceRead = true
    ctx.lastSeen = record
  } catch {
    // 읽지 못하면 마지막으로 본 기록으로 판단한다. 쓰기도 실패할 가능성이 크며, 실패는 쓸 때 알린다
    record = ctx.lastSeen
  }
  ensureCurrent(ctx, startedAt)
  if (record === null) return
  if (record.email !== email) {
    // 다른 계정의 인증이 성공했다. 볼트를 준비하지 못하더라도 이전 계정의 기록을 남기지 않는다 (ADR-0019)
    ctx.lastSeen = null
    await ctx.device.clear()
    // 이전 계정으로 열린 다른 탭은 모두 로그아웃한다
    ctx.deps.tabs.channel.post('logged-out')
    return
  }
  if (!sameKdf(record.kdf, kdf)) {
    // 비밀번호가 바뀌었다. 옛 키의 대기 내용은 열 수 없으니 버린다. 되돌림 검사는 그대로 해서,
    // 서버가 KDF 값만 바꿔 유실을 감추고 기기 사본을 덮어쓰게 하지 못한다. 기기 사본은 볼트를 연 뒤에야 바뀐다 (ADR-0019)
    ctx.orphan = null
    return
  }
  ctx.deps.revisionMemory.raise(record.lastSeenRevision)
  if (record.pendingSync && ctx.orphan === null) ctx.orphan = { email, payload: record.payload, revision: record.revision }
}

/** 로그인 상태를 지우고 새 로그인을 시작한다. @returns 이 로그인의 epoch */
export function beginSignIn(ctx: SessionContext, email: string): number {
  clear(ctx)
  // 다른 계정의 revision을 기준으로 되돌림을 판정하지 않도록 계정이 바뀌면 지운다
  if (ctx.lastEmail !== email) ctx.deps.revisionMemory.reset(0)
  ctx.lastEmail = email
  ctx.retryBlocked = false
  return ctx.epoch
}

/** 인증 결과로 기기 기록을 정리하고 서버 볼트를 준비해 연다 */
export async function signIn(ctx: SessionContext, email: string, startedAt: number, result: AuthResult, options: RollbackOption): Promise<void> {
  ensureCurrent(ctx, startedAt)
  await ctx.deps.tabs.lock.run('device', () => prepareDeviceForSignIn(ctx, email, result.kdf, startedAt))
  ensureCurrent(ctx, startedAt)
  const sync = syncFor(ctx, result.session.accessToken, result.encryptionKey)
  const prepared = await prepareVault(sync, options)
  ensureCurrent(ctx, startedAt)
  const adopted = await adoptOrphan(ctx, email, result.encryptionKey, sync, prepared)
  ensureCurrent(ctx, startedAt)
  await open(ctx, email, result.session.accessToken, result.kdf, sync, adopted.vault, adopted.pendingSync)
  // 올렸거나, 이 볼트의 대기로 옮겼거나, 서버가 받을 수 없어 버렸다. 다른 계정의 대기 내용이면 그 계정이 열렸으므로 버린다 (VLT-002)
  ctx.orphan = null
  if (adopted.dropped !== undefined) notifySync(ctx, adopted.dropped)
}
