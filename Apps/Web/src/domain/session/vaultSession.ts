// 로그인·가입·잠금·잠금 해제·로그아웃 상태 관리 (SEC-002, SEC-003)
// 잠기면 암호화 키와 평문 볼트를 버리고, 이메일·KDF 값·토큰·암호화 볼트만 남긴다
// 서버에 닿지 못한 저장은 동기화 대기로 두고 나중에 다시 보낸다 (VLT-001)
import { normalizeEmail, type AuthResult, type AuthService, type UnlockKdf } from '../auth/authService'
import { fromBase64 } from '../crypto/base64'
import { deriveKeys } from '../crypto/keys'
import { VaultDecryptError, openVault, type EncryptedVaultPayload } from '../crypto/vaultCipher'
import { ServerError } from '../server/serverErrors'
import { mergeVaults } from '../vault/mergeVaults'
import { isReadOnly, parseVaultData, type VaultData } from '../vault/vaultData'
import { RevisionRollbackError, isTransientSyncError, type RevisionMemory, type SyncedVault, type VaultSync } from '../vault/vaultSync'

/** 마스터 비밀번호가 틀려 저장해 둔 볼트를 열 수 없음 */
export class WrongMasterPasswordError extends Error {
  constructor() {
    super('마스터 비밀번호가 올바르지 않음')
    this.name = 'WrongMasterPasswordError'
  }
}

/** 지금 상태에서는 할 수 없는 동작이거나, 기다리는 사이 잠금·로그아웃으로 취소된 작업 */
export class SessionStateError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'SessionStateError'
  }
}

/** 세션 토큰이 만료·폐기됨. 로그아웃 상태로 바뀌었으니 다시 로그인해야 한다 (SEC-002) */
export class SessionExpiredError extends Error {
  constructor() {
    super('세션이 만료됨')
    this.name = 'SessionExpiredError'
  }
}

/** 가입은 됐지만 빈 볼트를 저장하지 못함. 다시 가입하지 말고 로그인하면 그때 볼트를 만든다 (SEC-003) */
export class VaultSetupError extends Error {
  constructor(cause: unknown) {
    super('가입 뒤 볼트 준비 실패', { cause })
    this.name = 'VaultSetupError'
  }
}

export type SessionState =
  | { readonly status: 'signedOut' }
  | { readonly status: 'locked'; readonly email: string }
  | {
      readonly status: 'unlocked'
      readonly email: string
      readonly vault: SyncedVault
      /** 서버에 올리지 못한 변경 사항이 있음 (VLT-001) */
      readonly pendingSync: boolean
    }

export interface VaultSessionDeps {
  readonly auth: AuthService
  readonly vaultSyncFor: (accessToken: string, encryptionKey: CryptoKey) => VaultSync
  /** vaultSyncFor가 쓰는 것과 같은 기억. 로그아웃·계정 전환 때 지운다 */
  readonly revisionMemory: RevisionMemory
}

// 잠겨도 남는 값. 비밀이 아니거나(KDF 값) 암호화돼 있다(볼트)
interface Account {
  readonly email: string
  readonly accessToken: string
  readonly kdf: UnlockKdf
  /** 마지막 볼트. 동기화 대기 중이면 대기 내용을 담은 볼트다 */
  readonly payload: EncryptedVaultPayload
  /** payload가 바탕으로 삼은 서버 revision */
  readonly revision: number
  readonly pendingSync: boolean
}

// 잠금 해제 동안만 있는 값
interface Unlocked {
  readonly sync: VaultSync
  readonly vault: SyncedVault
  readonly pendingSync: boolean
}

// 세션 만료로 로그아웃될 때 남긴 동기화 대기 내용. 같은 계정으로 다시 로그인하면 올린다 (VLT-001)
interface OrphanedPending {
  readonly email: string
  readonly payload: EncryptedVaultPayload
  readonly revision: number
}

interface RollbackOption {
  /** 서버 볼트 되돌림 경고를 사용자가 확인했을 때만 true (VLT-001) */
  readonly acceptRollback?: boolean
}

/** 동기화 대기 내용을 다시 보낸 결과. null이면 모두 올렸다 */
export type SyncChangeListener = (error: unknown) => void

type PushResult = 'synced' | 'idle' | 'stillPending'

function isUnauthorized(error: unknown): boolean {
  return error instanceof ServerError && error.code === 'UNAUTHORIZED'
}

export function createVaultSession({ auth, vaultSyncFor, revisionMemory }: VaultSessionDeps) {
  let account: Account | null = null
  let unlocked: Unlocked | null = null
  let lastEmail: string | null = null
  let orphan: OrphanedPending | null = null
  // 잠금·로그아웃·새 로그인마다 올린다. 기다리는 사이 값이 바뀐 작업은 결과를 버린다
  let epoch = 0
  // 로그아웃·새 로그인마다 올린다. 잠금으로는 바뀌지 않는다
  let generation = 0
  // 대기 내용 보내기를 한 번에 하나씩 순서대로 한다
  let pushChain: Promise<unknown> = Promise.resolve()
  // 되돌림이 감지되면 잠금 해제·저장 전까지 자동으로 다시 보내지 않는다
  let retryBlocked = false
  const lockListeners = new Set<() => void>()
  const unlockListeners = new Set<() => void>()
  const syncListeners = new Set<SyncChangeListener>()

  function notify(listeners: Set<() => void>) {
    for (const listener of listeners) listener()
  }

  function notifySync(error: unknown) {
    for (const listener of syncListeners) listener(error)
  }

  function ensureCurrent(startedAt: number) {
    if (epoch !== startedAt) throw new SessionStateError('기다리는 사이 잠금 또는 로그아웃되어 작업을 취소함')
  }

  function open(email: string, accessToken: string, kdf: UnlockKdf, sync: VaultSync, vault: SyncedVault, pendingSync = false) {
    // 모든 계정이 볼트를 가지므로 여기서 payload가 없을 수 없다 (SEC-003)
    if (vault.payload === null) throw new SessionStateError('볼트 없이 잠금을 해제할 수 없음')
    const wasLocked = unlocked === null
    account = { email, accessToken, kdf, payload: vault.payload, revision: vault.revision, pendingSync }
    unlocked = { sync, vault, pendingSync }
    if (wasLocked) notify(unlockListeners)
  }

  function clear() {
    const wasUnlocked = unlocked !== null
    epoch += 1
    generation += 1
    account = null
    unlocked = null
    if (wasUnlocked) notify(lockListeners)
  }

  /** 세션 만료. 대기 내용은 남겨 같은 계정으로 다시 로그인하면 올린다 */
  function expire(): SessionExpiredError {
    if (account?.pendingSync) orphan = { email: account.email, payload: account.payload, revision: account.revision }
    clear()
    const error = new SessionExpiredError()
    notifySync(error)
    return error
  }

  /**
   * 대기 내용을 한 번 보낸다. 서버에 닿지 못하면 대기를 유지하고 조용히 끝난다
   * @throws SessionExpiredError, 그 밖에 VaultSync.save의 분명한 거절
   */
  async function pushPending(): Promise<PushResult> {
    if (account === null || unlocked === null || !unlocked.pendingSync) return 'idle'
    const startedAt = epoch
    const startedGeneration = generation
    const { email, accessToken, kdf } = account
    const { sync, vault } = unlocked
    let saved: SyncedVault
    try {
      saved = await sync.save(vault, vault.data)
    } catch (error) {
      if (isTransientSyncError(error)) return 'stillPending'
      if (isUnauthorized(error)) throw generation === startedGeneration ? expire() : new SessionExpiredError()
      if (error instanceof RevisionRollbackError) retryBlocked = true
      throw error
    }
    if (generation !== startedGeneration || account === null) return 'idle'
    if (epoch !== startedAt || unlocked === null) {
      // 보내는 사이 잠겼다. 잠기기 전에 더 고친 것이 없으면 올린 볼트를 잠긴 상태에 남긴다
      if (account.payload === vault.payload && saved.payload !== null) {
        account = { ...account, payload: saved.payload, revision: saved.revision, pendingSync: false }
      }
      return 'idle'
    }
    // 보내는 사이 또 고쳤으면 새 수정은 다음 차례에 보낸다. 서버가 앞서 있으니 병합 절차를 탄다
    if (unlocked.vault !== vault) return 'stillPending'
    open(email, accessToken, kdf, sync, saved)
    return 'synced'
  }

  function enqueuePush(): Promise<PushResult> {
    const run = pushChain.then(pushPending)
    pushChain = run.catch(() => undefined)
    return run
  }

  async function prepareVault(sync: VaultSync, options: RollbackOption): Promise<SyncedVault> {
    const vault = await sync.load(options)
    // 볼트가 없으면(가입 직후, 가입 때 저장 실패) 열기 전에 빈 볼트를 저장한다. 잠금 해제 확인에 필요하다 (SEC-003)
    return vault.payload === null ? sync.save(vault, vault.data) : vault
  }

  /** 세션 만료 전에 남긴 대기 내용을 서버 볼트와 병합해 올린다. 서버에 닿지 못하면 대기로 연다 */
  async function adoptOrphan(email: string, encryptionKey: CryptoKey, sync: VaultSync, vault: SyncedVault) {
    const pending = orphan
    orphan = null
    if (pending === null || pending.email !== email) return { vault, pendingSync: false }
    let data: VaultData
    try {
      data = parseVaultData(await openVault(encryptionKey, pending.payload))
    } catch {
      // 열 수 없는 대기 내용(다른 키로 만든 것 등)은 버린다
      return { vault, pendingSync: false }
    }
    const local: SyncedVault = { revision: pending.revision, data, readOnly: false, payload: pending.payload }
    try {
      // 옛 revision을 기준으로 보내 서버가 앞서 있으면 병합 절차를 탄다
      return { vault: await sync.save(local, data), pendingSync: false }
    } catch (error) {
      if (!isTransientSyncError(error)) throw error
      return { vault: local, pendingSync: true }
    }
  }

  async function signIn(email: string, startedAt: number, result: AuthResult, options: RollbackOption) {
    ensureCurrent(startedAt)
    const sync = vaultSyncFor(result.session.accessToken, result.encryptionKey)
    const prepared = await prepareVault(sync, options)
    ensureCurrent(startedAt)
    const { vault, pendingSync } = await adoptOrphan(email, result.encryptionKey, sync, prepared)
    ensureCurrent(startedAt)
    open(email, result.session.accessToken, result.kdf, sync, vault, pendingSync)
  }

  function beginSignIn(email: string): number {
    clear()
    // 다른 계정의 revision을 기준으로 되돌림을 판정하지 않도록 계정이 바뀌면 지운다
    if (lastEmail !== email) revisionMemory.reset(0)
    if (orphan !== null && orphan.email !== email) orphan = null
    lastEmail = email
    retryBlocked = false
    return epoch
  }

  /**
   * 잠금 해제 직후 서버와 맞춘다. 대기 내용이 있으면 서버 볼트로 덮지 않고 병합해 올린다
   * @returns 서버와 맞춘 볼트. 서버에 닿지 못하거나 서버 오류·응답 형식 오류면 null
   * @throws RevisionRollbackError, SessionExpiredError
   */
  async function syncAfterUnlock(sync: VaultSync, cached: SyncedVault, pendingSync: boolean, options: RollbackOption) {
    try {
      // 옛 revision을 기준으로 보내 서버가 앞서 있으면 병합 절차를 탄다
      if (pendingSync && options.acceptRollback !== true) return await sync.save(cached, cached.data)
      const fresh = await sync.load(options)
      // 대기 내용이 있거나, 되돌림을 받아들였는데 서버 볼트가 없으면(유실) 이 기기의 볼트를 병합해 올린다
      if (pendingSync || fresh.payload === null) return await sync.save(fresh, mergeVaults(cached.data, fresh.data))
      return fresh
    } catch (error) {
      if (error instanceof RevisionRollbackError) throw error
      if (isUnauthorized(error)) throw expire()
      return null
    }
  }

  return {
    state(): SessionState {
      if (account === null) return { status: 'signedOut' }
      if (unlocked === null) return { status: 'locked', email: account.email }
      return { status: 'unlocked', email: account.email, vault: unlocked.vault, pendingSync: unlocked.pendingSync }
    },

    /** @throws VaultSetupError 가입은 됐지만 빈 볼트를 저장하지 못했을 때 */
    async register(email: string, password: string): Promise<void> {
      const normalized = normalizeEmail(email)
      const startedAt = beginSignIn(normalized)
      const result = await auth.register(email, password)
      try {
        await signIn(normalized, startedAt, result, {})
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
      const startedAt = beginSignIn(normalized)
      await signIn(normalized, startedAt, await auth.login(email, password), options)
    },

    /** 키와 평문 볼트를 버린다. 진행 중인 잠금 해제·저장도 취소한다. 동기화 대기 내용은 암호화된 채 남는다 */
    lock(): void {
      epoch += 1
      if (unlocked === null) return
      unlocked = null
      notify(lockListeners)
    },

    /**
     * 저장해 둔 암호화 볼트를 복호화해 비밀번호를 확인하므로 오프라인에서도 동작한다.
     * 확인 뒤 바로 서버와 맞추며(대기 내용이 있으면 병합해 올림), 서버와 맞추지 못하면 저장해 둔 볼트로 연다
     * @returns offline: 서버와 맞추지 못했는지
     * @throws WrongMasterPasswordError, SessionExpiredError(로그아웃됨), RevisionRollbackError(잠긴 상태 유지), SessionStateError
     */
    async unlock(password: string, options: RollbackOption = {}): Promise<{ offline: boolean }> {
      if (account === null) throw new SessionStateError('로그인 전에는 잠금을 해제할 수 없음')
      if (unlocked !== null) return { offline: false }
      const locked = account
      const startedAt = epoch

      const keys = await deriveKeys(password, fromBase64(locked.kdf.salt), locked.kdf.iterations)
      // 잠금 해제에는 인증 해시가 필요 없다. 메모리에 남기지 않는다
      keys.authHash.fill(0)
      let data: VaultData
      try {
        data = parseVaultData(await openVault(keys.encryptionKey, locked.payload))
      } catch (error) {
        if (error instanceof VaultDecryptError) throw new WrongMasterPasswordError()
        throw error
      }
      ensureCurrent(startedAt)

      const sync = vaultSyncFor(locked.accessToken, keys.encryptionKey)
      const cached: SyncedVault = { revision: locked.revision, data, readOnly: isReadOnly(data), payload: locked.payload }
      retryBlocked = false
      const fresh = await syncAfterUnlock(sync, cached, locked.pendingSync, options)
      ensureCurrent(startedAt)
      open(locked.email, locked.accessToken, locked.kdf, sync, fresh ?? cached, fresh === null && locked.pendingSync)
      return { offline: fresh === null }
    },

    /**
     * 먼저 이 기기에 반영한 뒤 서버에 보낸다. 서버에 닿지 못하면 동기화 대기로 두고 성공으로 끝난다 (VLT-001)
     * @throws SessionStateError(잠김·취소), 보내기 전 검사 오류(ReadOnlyVaultError, VaultTooLargeError, InvalidVaultDataError),
     *   SessionExpiredError, 그 밖에 VaultSync.save의 분명한 거절. 분명한 거절이어도 변경 사항은 대기로 남는다
     */
    async save(data: VaultData): Promise<void> {
      if (account === null || unlocked === null) throw new SessionStateError('잠금 해제 상태가 아님')
      const { email, accessToken, kdf } = account
      const { sync, vault } = unlocked
      const startedAt = epoch
      const payload = await sync.seal(data)
      ensureCurrent(startedAt)
      // 기준 revision은 마지막으로 서버와 맞춘 값 그대로다. 서버가 앞서 있으면 보낼 때 병합한다
      open(email, accessToken, kdf, sync, { revision: vault.revision, data, readOnly: false, payload }, true)
      retryBlocked = false
      await enqueuePush()
    },

    /**
     * 동기화 대기 내용을 다시 보낸다. 재연결·주기 재전송이 부르며 결과는 onSyncChange로 알린다.
     * 서버에 닿지 못하면 조용히 대기를 유지하고, 되돌림이 감지되면 잠금 해제·저장 전까지 다시 보내지 않는다
     */
    async retryPendingSync(): Promise<void> {
      if (retryBlocked || unlocked === null || !unlocked.pendingSync) return
      try {
        if ((await enqueuePush()) === 'synced') notifySync(null)
      } catch (error) {
        // 세션 만료는 expire가 이미 알렸다
        if (!(error instanceof SessionExpiredError)) notifySync(error)
      }
    },

    /**
     * 이 기기의 세션을 먼저 지우고(볼트가 열려 있었으면 잠금 알림), 서버에 토큰 폐기를 요청한다.
     * 동기화 대기 내용도 버린다. 서버 요청이 실패하면 오류를 그대로 알린다. 이 기기는 이미 로그아웃된 상태다
     */
    async logout(): Promise<void> {
      const token = account?.accessToken
      clear()
      orphan = null
      revisionMemory.reset(0)
      lastEmail = null
      if (token !== undefined) await auth.logout(token)
    },

    /** 볼트가 닫힐 때(잠금, 잠금 해제 상태에서 로그아웃) 부를 함수를 등록한다. 클립보드 비우기 등 (COM-001) */
    onLock(listener: () => void): () => void {
      lockListeners.add(listener)
      return () => lockListeners.delete(listener)
    },

    /** 볼트가 열릴 때(로그인, 가입, 잠금 해제) 부를 함수를 등록한다. 자동 잠금 타이머 재시작 등 */
    onUnlock(listener: () => void): () => void {
      unlockListeners.add(listener)
      return () => unlockListeners.delete(listener)
    },

    /** 대기 내용 재전송 결과나 세션 만료를 알릴 함수를 등록한다. 화면이 상태를 다시 읽고 오류를 보여 준다 */
    onSyncChange(listener: SyncChangeListener): () => void {
      syncListeners.add(listener)
      return () => syncListeners.delete(listener)
    },
  }
}

export type VaultSession = ReturnType<typeof createVaultSession>
