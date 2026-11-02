// 로그인·가입·잠금·잠금 해제·로그아웃 상태 관리 (SEC-002, SEC-003)
// 잠기면 암호화 키와 평문 볼트를 버리고, 이메일·KDF 값·토큰·암호화 볼트만 남긴다
// 서버에 닿지 못한 저장은 동기화 대기로 두고 나중에 다시 보낸다 (VLT-002)
// 상태는 sessionContext, 기기 기록은 deviceSync가 맡는다
import { MasterPasswordChangedError, normalizeEmail, type AuthResult, type UnlockKdf } from '../auth/authService'
import { fromBase64 } from '../crypto/base64'
import { deriveKeys } from '../crypto/keys'
import { VaultDecryptError, openVault } from '../crypto/vaultCipher'
import { sameKdf, samePayload, sameRecord } from '../device/reconcileDeviceRecord'
import { DeviceStorageError, type DeviceRecord } from '../device/deviceRecord'
import { NetworkError } from '../server/serverErrors'
import { mergeVaults } from '../vault/mergeVaults'
import { isReadOnly, parseVaultData, type VaultData } from '../vault/vaultData'
import { RevisionRollbackError, VaultSyncConflictError, isTransientSyncError, type SyncedVault, type VaultSync } from '../vault/vaultSync'
import { createBackupFile, type BackupFile, type VaultBackup } from '../backup/backupFile'
import { restoreBackupInto } from '../backup/restoreBackup'
import { onTabMessage, open, persist, refreshLockedFromDevice, writeLocked } from './deviceSync'
import { NO_TOKEN, clear, createSessionContext, ensureCurrent, ensureCurrentOrRevoke, expire, isUnauthorized, lockThisTab, notifySync, readDevice, resetSignIn, revoke, syncFor, type SessionContext } from './sessionContext'
import {
  SessionExpiredError,
  SessionStateError,
  VaultSetupError,
  WrongMasterPasswordError,
  type Account,
  type LockScope,
  type RollbackOption,
  type SessionState,
  type SyncChangeListener,
  type VaultSessionDeps,
} from './sessionTypes'

export { SessionExpiredError, SessionStateError, VaultSetupError, WrongMasterPasswordError }
export type { LockScope, SessionState, SyncChangeListener, VaultSessionDeps }


type PushResult = 'synced' | 'idle' | 'stillPending'

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
function enqueuePush(ctx: SessionContext): Promise<PushResult> {
  const run = ctx.pushChain.then(() => pushPending(ctx))
  ctx.pushChain = run.catch(() => undefined)
  return run
}


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
function beginSignIn(ctx: SessionContext, email: string): number {
  clear(ctx)
  // 다른 계정의 revision을 기준으로 되돌림을 판정하지 않도록 계정이 바뀌면 지운다
  if (ctx.lastEmail !== email) ctx.deps.revisionMemory.reset(0)
  ctx.lastEmail = email
  ctx.retryBlocked = false
  return ctx.epoch
}

/** 인증 결과로 기기 기록을 정리하고 서버 볼트를 준비해 연다 */
async function signIn(ctx: SessionContext, email: string, startedAt: number, result: AuthResult, options: RollbackOption): Promise<void> {
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


/** 비밀번호로 이 기기의 볼트를 열어 확인한다. 틀리면 인증 해시도 지운다 */
async function verifyPassword(password: string, target: Account) {
  const keys = await deriveKeys(password, fromBase64(target.kdf.salt), target.kdf.iterations)
  try {
    return { keys, data: parseVaultData(await openVault(keys.encryptionKey, target.payload)) }
  } catch (error) {
    keys.authHash.fill(0)
    if (error instanceof VaultDecryptError) throw new WrongMasterPasswordError()
    throw error
  }
}

/**
 * 세션 토큰 없이 연 계정을 이미 만든 인증 해시로 로그인한다. 인증 해시는 이 요청 안에서 지운다 (ADR-0019)
 * @returns 세션 토큰. 로그인하지 못했으면 null과 그 사유(서버에 닿지 못함, 서버 거절 등)
 * @throws MasterPasswordChangedError(로그아웃 상태로 바꾼다)
 */
async function resumeSession(ctx: SessionContext, target: Account, authHash: Uint8Array): Promise<{ accessToken: string | null; failure?: unknown }> {
  const startedGeneration = ctx.generation
  try {
    return { accessToken: (await ctx.deps.auth.resume(target.email, target.kdf, authHash)).accessToken }
  } catch (error) {
    if (error instanceof MasterPasswordChangedError) {
      // 기기 기록은 새 비밀번호로 로그인에 성공할 때 버린다 (ADR-0020)
      if (ctx.generation === startedGeneration) clear(ctx)
      throw error
    }
    // 그 밖의 실패여도 비밀번호는 이 기기에서 확인했으니 볼트는 연다. 사유는 부른 쪽이 알린다
    return { accessToken: null, failure: error }
  } finally {
    authHash.fill(0)
  }
}

/**
 * 잠금 해제 직후 서버와 맞춘다. 대기 내용이 있으면 서버 볼트로 덮지 않고 병합해 올린다
 * @returns 서버와 맞춘 볼트. 서버에 닿지 못하거나 서버 오류·응답 형식 오류면 null
 * @throws RevisionRollbackError, SessionExpiredError, SessionStateError(그사이 로그아웃·다른 로그인)
 */
async function syncAfterUnlock(ctx: SessionContext, sync: VaultSync, cached: SyncedVault, pendingSync: boolean, options: RollbackOption): Promise<SyncedVault | null> {
  const startedGeneration = ctx.generation
  try {
    // 대기 내용이 있어도 먼저 받아 되돌림을 검사한다. 기준과 같은 revision으로 되돌아간 서버도 잡는다
    const fresh = await sync.load(options)
    // 대기 내용이 있거나, 되돌림을 받아들였는데 서버 볼트가 없으면(유실) 이 기기의 볼트를 병합해 올린다
    if (pendingSync || fresh.payload === null) return await sync.save(fresh, mergeVaults(cached.data, fresh.data))
    return fresh
  } catch (error) {
    if (error instanceof RevisionRollbackError) throw error
    if (isUnauthorized(error)) {
      // 그사이 로그아웃·다른 로그인이 있었으면 지금 세션을 끝내지 않는다
      if (ctx.generation !== startedGeneration) throw new SessionStateError('기다리는 사이 로그아웃되어 작업을 취소함')
      throw expire(ctx)
    }
    return null
  }
}

export function createVaultSession(deps: VaultSessionDeps) {
  const { auth, revisionMemory, tabs } = deps
  const ctx = createSessionContext(deps)
  const { device } = ctx

  tabs.channel.subscribe((kind) => onTabMessage(ctx, kind))

  const session = {
    state(): SessionState {
      if (ctx.account === null) return { status: 'signedOut' }
      if (ctx.unlocked === null) return { status: 'locked', email: ctx.account.email, pendingSync: ctx.account.pendingSync }
      return { status: 'unlocked', email: ctx.account.email, vault: ctx.unlocked.vault, pendingSync: ctx.unlocked.pendingSync, signedIn: ctx.account.accessToken !== null, storedOnDevice: !device.hasFailed() }
    },

    /** @throws VaultSetupError 가입은 됐지만 빈 볼트를 저장하지 못했을 때 */
    async register(email: string, password: string): Promise<void> {
      const normalized = normalizeEmail(email)
      const startedAt = beginSignIn(ctx, normalized)
      const result = await auth.register(email, password)
      try {
        await signIn(ctx, normalized, startedAt, result, {})
      } catch (error) {
        if (error instanceof SessionStateError) throw error
        throw new VaultSetupError(error)
      }
    },

    /**
     * 세션 만료 전에 남긴 동기화 대기 내용이 같은 계정이면 병합해 올린다
     * @throws RevisionRollbackError 서버 볼트가 되돌아감. 사용자가 확인하면 acceptRollback으로 다시 부른다
     */
    async login(email: string, password: string, options: RollbackOption = {}): Promise<void> {
      const normalized = normalizeEmail(email)
      const startedAt = beginSignIn(ctx, normalized)
      await signIn(ctx, normalized, startedAt, await auth.login(email, password), options)
    },

    /**
     * 키와 평문 볼트를 버린다. 진행 중인 잠금 해제·저장도 취소한다. 동기화 대기 내용은 암호화된 채 남는다.
     * allTabs(잠금 버튼, 보이는 탭의 자동 잠금 시간 초과)면 같은 계정의 다른 탭도 잠근다. 새로고침·떠남·동결, "즉시" 설정의 숨김은 thisTab (SEC-003)
     */
    lock(scope: LockScope = 'allTabs'): void {
      if (lockThisTab(ctx) && scope === 'allTabs') tabs.channel.post('locked')
    },

    /**
     * 저장해 둔 암호화 볼트를 복호화해 비밀번호를 확인하므로 오프라인에서도 동작한다.
     * 확인 뒤 바로 서버와 맞추며(대기 내용이 있으면 병합해 올림), 서버와 맞추지 못하면 저장해 둔 볼트로 연다
     * @returns offline: 서버와 맞추지 못했는지
     * @throws WrongMasterPasswordError, SessionExpiredError(로그아웃됨), RevisionRollbackError(잠긴 상태 유지), SessionStateError
     */
    async unlock(password: string, options: RollbackOption = {}): Promise<{ offline: boolean }> {
      if (ctx.account === null) throw new SessionStateError('로그인 전에는 잠금을 해제할 수 없음')
      if (ctx.unlocked !== null) return { offline: false }
      const startedAt = ctx.epoch
      // 동결 등으로 다른 탭의 알림을 놓쳤을 수 있으니 기기 기록을 다시 읽는다. 기록의 볼트는 연 뒤 쓸 때 병합한다 (ADR-WEB-001)
      await refreshLockedFromDevice(ctx, false)
      ensureCurrent(ctx, startedAt)
      if (ctx.account === null) throw new SessionStateError('다른 탭에서 로그아웃됨')
      if (ctx.unlocked !== null) return { offline: false }
      const locked = ctx.account

      const { keys, data } = await verifyPassword(password, locked)
      // 토큰이 있으면 인증 해시가 필요 없어 바로 지운다. 없으면(기기 기록으로 다시 열었으면) 바로 로그인하고 지운다 (SEC-003, ADR-0019)
      let accessToken = locked.accessToken
      if (accessToken === null) {
        const resumed = await resumeSession(ctx, locked, keys.authHash)
        accessToken = resumed.accessToken
        // 오프라인은 조용히 세션 없이 연다. 서버가 거절했으면(출처 거부 등) 사유를 알린다
        if (resumed.failure !== undefined && !isTransientSyncError(resumed.failure) && ctx.epoch === startedAt) notifySync(ctx, resumed.failure)
      }
      keys.authHash.fill(0)
      ensureCurrentOrRevoke(ctx, startedAt, locked.accessToken === null ? accessToken : null)
      // 받은 토큰은 되돌림 확인으로 잠긴 채 끝나도 남겨, 다시 풀 때 또 로그인하지 않게 한다
      if (locked.accessToken === null && accessToken !== null) ctx.account = { ...locked, accessToken }

      const sync = syncFor(ctx, accessToken ?? NO_TOKEN, keys.encryptionKey)
      const cached: SyncedVault = { revision: locked.revision, data, readOnly: isReadOnly(data), payload: locked.payload }
      ctx.retryBlocked = false
      const fresh = accessToken === null ? null : await syncAfterUnlock(ctx, sync, cached, locked.pendingSync, options)
      ensureCurrent(ctx, startedAt)
      await open(ctx, locked.email, accessToken, locked.kdf, sync, fresh ?? cached, fresh === null && locked.pendingSync)
      return { offline: fresh === null }
    },

    /**
     * 세션 없이 연 볼트에서 비밀번호를 다시 입력받아 로그인하고 서버와 맞춘다 (SEC-003, ADR-0019)
     * @throws WrongMasterPasswordError, 로그인하지 못한 실제 사유(NetworkError, ServerError 등), MasterPasswordChangedError(로그아웃됨),
     *   RevisionRollbackError, SessionExpiredError, SessionStateError
     */
    async reconnect(password: string, options: RollbackOption = {}): Promise<void> {
      if (ctx.account === null || ctx.unlocked === null) throw new SessionStateError('잠금 해제 상태가 아님')
      if (ctx.account.accessToken !== null) return
      const current = ctx.account
      const startedAt = ctx.epoch
      const { keys } = await verifyPassword(password, current)
      const { accessToken, failure } = await resumeSession(ctx, current, keys.authHash)
      // 왜 동기화하지 못하는지 알 수 있게 실제 사유(연결 실패, 서버 오류, 거절)를 그대로 낸다
      if (accessToken === null) throw failure ?? new NetworkError()
      ensureCurrentOrRevoke(ctx, startedAt, accessToken)
      const opened = ctx.unlocked
      if (opened === null) throw new SessionStateError('잠금 해제 상태가 아님')
      const sync = syncFor(ctx, accessToken, keys.encryptionKey)
      ctx.retryBlocked = false
      let fresh: SyncedVault | null
      try {
        // 서버와 맞추기 전에는 토큰을 세션에 넣지 않는다. 되돌림이면 세션 없이 두고 확인을 받는다
        fresh = await syncAfterUnlock(ctx, sync, opened.vault, opened.pendingSync, options)
      } catch (error) {
        revoke(ctx, accessToken)
        throw error
      }
      ensureCurrentOrRevoke(ctx, startedAt, accessToken)
      const latest = ctx.unlocked
      if (latest === null) throw new SessionStateError('잠금 해제 상태가 아님')
      // 다시 닿지 못했거나 맞추는 사이 또 고쳤으면 지금 볼트를 대기로 두고 보낸다. 서버가 앞서 있으면 병합 절차를 탄다
      if (fresh === null || latest.vault !== opened.vault) {
        const pending = latest.pendingSync || latest.vault !== opened.vault
        await open(ctx, current.email, accessToken, current.kdf, sync, latest.vault, pending)
        await enqueuePush(ctx)
        return
      }
      await open(ctx, current.email, accessToken, current.kdf, sync, fresh)
    },

    /**
     * 먼저 이 기기에 반영한 뒤 서버에 보낸다. 서버에 닿지 못하면 동기화 대기로 두고 성공으로 끝난다 (VLT-002)
     * @throws SessionStateError(잠김·취소), 보내기 전 검사 오류(ReadOnlyVaultError, VaultTooLargeError, InvalidVaultDataError),
     *   SessionExpiredError, 그 밖에 VaultSync.save의 분명한 거절. 분명한 거절이어도 변경 사항은 대기로 남는다
     */
    async save(data: VaultData): Promise<void> {
      if (ctx.account === null || ctx.unlocked === null) throw new SessionStateError('잠금 해제 상태가 아님')
      const { vault } = ctx.unlocked
      const startedAt = ctx.epoch
      const payload = await ctx.unlocked.sync.seal(data)
      ensureCurrent(ctx, startedAt)
      // 암호화하는 사이 다시 로그인했을 수 있으니 지금 계정의 토큰과 동기화 객체를 쓴다
      const latestAccount = ctx.account
      const latest = ctx.unlocked
      if (latestAccount === null || latest === null) throw new SessionStateError('잠금 해제 상태가 아님')
      const { email, accessToken, kdf } = latestAccount
      // 기준 revision은 마지막으로 서버와 맞춘 값 그대로다. 서버가 앞서 있으면 보낼 때 병합한다
      await open(ctx, email, accessToken, kdf, latest.sync, { revision: vault.revision, data, readOnly: false, payload }, true)
      ctx.retryBlocked = false
      await enqueuePush(ctx)
    },

    /**
     * 화면의 볼트(대기 중인 변경 포함)를 지금 키로 새로 암호화해 계정의 KDF 값과 함께 백업 파일로 만든다 (VLT-004).
     * 키는 세션 밖으로 나가지 않는다. 읽기 전용 볼트도 내보낸다
     * @throws SessionStateError 잠금 해제 상태가 아니거나 암호화하는 사이 잠김
     */
    async exportBackup(now: Date): Promise<BackupFile> {
      if (ctx.account === null || ctx.unlocked === null) throw new SessionStateError('잠금 해제 상태가 아님')
      const { kdf } = ctx.account
      const startedAt = ctx.epoch
      const payload = await ctx.unlocked.sync.sealBackup(ctx.unlocked.vault.data)
      ensureCurrent(ctx, startedAt)
      return createBackupFile(payload, kdf, now)
    },

    /**
     * 백업을 파일의 KDF 값과 비밀번호로 열어 백업에 있던 항목을 백업 시점 내용으로 되돌리고 save와 같이 저장한다 (VLT-004)
     * @param now 복원 시각. 되돌린 항목의 수정 시각이 된다
     * @returns 복원한 항목 수
     * @throws ReadOnlyVaultError, BackupDecryptError, NewerBackupVersionError, SessionStateError, save의 오류
     */
    restoreBackup(backup: VaultBackup, password: string, now: Date): Promise<number> {
      return restoreBackupInto(session, backup, password, now)
    },

    /**
     * 서버에 올리지 못한 변경 사항이 기기에도 아직 저장되지 않았는지(쓰는 중이거나 실패). 떠남 확인용 (VLT-002).
     * 잠겼거나 세션 만료로 로그아웃된 뒤 남긴 것도 센다
     */
    hasUnsavedChanges(): boolean {
      return (ctx.account?.pendingSync === true || ctx.orphan !== null) && (device.isBehind() || ctx.persisting > 0)
    },

    /**
     * 기기 기록이 있으면 그 계정의 잠금 화면으로 시작한다 (SEC-003, ADR-0019). 앱을 열 때 한 번 부른다.
     * 기록이 없거나 규격에 맞지 않으면 로그인 전 상태로 둔다
     * @throws DeviceStorageError 기기 저장소를 읽지 못함
     */
    async restore(): Promise<void> {
      const record = await readDevice(ctx)
      ctx.deviceRead = true
      if (record === null || ctx.account !== null) return
      ctx.lastSeen = record
      const { email, kdf, payload, revision, pendingSync } = record
      ctx.account = { email, accessToken: null, kdf, payload, revision, pendingSync }
      ctx.lastEmail = email
      revisionMemory.reset(record.lastSeenRevision)
    },

    /**
     * 동기화 대기 내용을 다시 보낸다. 재연결·주기 재전송이 부르며 결과는 onSyncChange로 알린다.
     * 서버에 닿지 못하면 조용히 대기를 유지하고, 되돌림이 감지되면 잠금 해제·저장 전까지 다시 보내지 않는다
     */
    async retryPendingSync(): Promise<void> {
      if (ctx.retryBlocked || ctx.unlocked === null || !ctx.unlocked.pendingSync) return
      try {
        if ((await enqueuePush(ctx)) === 'synced') notifySync(ctx, null)
      } catch (error) {
        // 세션 만료는 expire가 이미 알렸다
        if (!(error instanceof SessionExpiredError)) notifySync(ctx, error)
      }
    },

    /**
     * 이 기기의 세션과 기기 기록을 먼저 지우고(볼트가 열려 있었으면 잠금 알림), 서버에 토큰 폐기를 요청한다.
     * 동기화 대기 내용도 버린다. 서버 요청이 실패하면 오류를 그대로 알린다. 이 기기는 이미 로그아웃된 상태다
     * @throws DeviceStorageError 기기 기록을 지우지 못함(서버 폐기 오류보다 먼저 알린다), 그 밖에 서버 폐기 요청 오류
     */
    async logout(): Promise<void> {
      const token = ctx.account?.accessToken
      resetSignIn(ctx)
      // 이 기기의 기록도 지운다 (ADR-0019). 지우지 못했으면 서버 폐기 결과보다 먼저 알린다
      const cleared = (await tabs.lock.run('device', () => device.clear())) === true
      // 같은 계정의 다른 탭도 로그아웃한다. 기기 기록은 이 탭이 지웠다
      if (token !== undefined) tabs.channel.post('logged-out')
      let serverError: unknown = null
      if (token !== undefined && token !== null) {
        try {
          await auth.logout(token)
        } catch (error) {
          serverError = error
        }
      }
      if (!cleared) throw new DeviceStorageError(new Error('로그아웃 때 기기 기록을 지우지 못함'))
      if (serverError !== null) throw serverError
    },

    /**
     * 기기 기록에 서버에 올리지 못한 변경이 있는지. 기록은 모든 탭의 대기 내용을 합친 것이므로 로그아웃 확인에 쓴다 (ADR-WEB-001).
     * 다른 탭이 쓰는 중이면 끝난 뒤 읽는다. 읽지 못하면 다른 탭의 대기 내용을 알 수 없으니 있다고 보고 확인을 받는다
     */
    async pendingOnDevice(): Promise<boolean> {
      const record = await tabs.lock.run('device', () => readDevice(ctx)).catch(() => undefined)
      if (record === undefined) return true
      return ctx.account !== null && record !== null && record.email === ctx.account.email && record.pendingSync
    },

    /** 다른 탭 때문에 상태가 바뀌었을 때(병합, 잠금, 로그아웃) 부를 함수를 등록한다. 화면이 상태를 다시 읽는다 */
    onRemoteChange(listener: () => void): () => void {
      ctx.remoteListeners.add(listener)
      return () => ctx.remoteListeners.delete(listener)
    },

    /** 볼트가 닫힐 때(잠금, 잠금 해제 상태에서 로그아웃) 부를 함수를 등록한다. 클립보드 비우기 등 (COM-001) */
    onLock(listener: () => void): () => void {
      ctx.lockListeners.add(listener)
      return () => ctx.lockListeners.delete(listener)
    },

    /** 볼트가 열릴 때(로그인, 가입, 잠금 해제) 부를 함수를 등록한다. 자동 잠금 타이머 재시작 등 */
    onUnlock(listener: () => void): () => void {
      ctx.unlockListeners.add(listener)
      return () => ctx.unlockListeners.delete(listener)
    },

    /** 대기 내용 재전송 결과나 세션 만료를 알릴 함수를 등록한다. 화면이 상태를 다시 읽고 오류를 보여 준다 */
    onSyncChange(listener: SyncChangeListener): () => void {
      ctx.syncListeners.add(listener)
      return () => ctx.syncListeners.delete(listener)
    },
  }
  return session
}

export type VaultSession = ReturnType<typeof createVaultSession>
