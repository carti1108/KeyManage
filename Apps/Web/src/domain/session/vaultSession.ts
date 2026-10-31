// 로그인·가입·잠금·잠금 해제·로그아웃 상태 관리 (SEC-002, SEC-003)
// 잠기면 암호화 키와 평문 볼트를 버리고, 이메일·KDF 값·토큰·암호화 볼트만 남긴다
import { normalizeEmail, type AuthResult, type AuthService, type UnlockKdf } from '../auth/authService'
import { fromBase64 } from '../crypto/base64'
import { deriveKeys } from '../crypto/keys'
import { VaultDecryptError, openVault, type EncryptedVaultPayload } from '../crypto/vaultCipher'
import { ServerError } from '../server/serverErrors'
import { isReadOnly, parseVaultData, type VaultData } from '../vault/vaultData'
import { RevisionRollbackError, type RevisionMemory, type SyncedVault, type VaultSync } from '../vault/vaultSync'

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
  | { readonly status: 'unlocked'; readonly email: string; readonly vault: SyncedVault }

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
  readonly payload: EncryptedVaultPayload
  readonly revision: number
}

// 잠금 해제 동안만 있는 값
interface Unlocked {
  readonly sync: VaultSync
  readonly vault: SyncedVault
}

interface RollbackOption {
  /** 서버 볼트 되돌림 경고를 사용자가 확인했을 때만 true (VLT-001) */
  readonly acceptRollback?: boolean
}

export function createVaultSession({ auth, vaultSyncFor, revisionMemory }: VaultSessionDeps) {
  let account: Account | null = null
  let unlocked: Unlocked | null = null
  let lastEmail: string | null = null
  // 잠금·로그아웃·새 로그인마다 올린다. 기다리는 사이 값이 바뀐 작업은 결과를 버린다
  let epoch = 0
  const lockListeners = new Set<() => void>()
  const unlockListeners = new Set<() => void>()

  function notify(listeners: Set<() => void>) {
    for (const listener of listeners) listener()
  }

  function ensureCurrent(startedAt: number) {
    if (epoch !== startedAt) throw new SessionStateError('기다리는 사이 잠금 또는 로그아웃되어 작업을 취소함')
  }

  function open(email: string, accessToken: string, kdf: UnlockKdf, sync: VaultSync, vault: SyncedVault) {
    // 모든 계정이 볼트를 가지므로 여기서 payload가 없을 수 없다 (SEC-003)
    if (vault.payload === null) throw new SessionStateError('볼트 없이 잠금을 해제할 수 없음')
    const wasLocked = unlocked === null
    account = { email, accessToken, kdf, payload: vault.payload, revision: vault.revision }
    unlocked = { sync, vault }
    if (wasLocked) notify(unlockListeners)
  }

  function clear() {
    const wasUnlocked = unlocked !== null
    epoch += 1
    account = null
    unlocked = null
    if (wasUnlocked) notify(lockListeners)
  }

  async function prepareVault(sync: VaultSync, options: RollbackOption): Promise<SyncedVault> {
    const vault = await sync.load(options)
    // 볼트가 없으면(가입 직후, 가입 때 저장 실패) 열기 전에 빈 볼트를 저장한다. 잠금 해제 확인에 필요하다 (SEC-003)
    return vault.payload === null ? sync.save(vault, vault.data) : vault
  }

  async function signIn(email: string, startedAt: number, result: AuthResult, options: RollbackOption) {
    ensureCurrent(startedAt)
    const sync = vaultSyncFor(result.session.accessToken, result.encryptionKey)
    const vault = await prepareVault(sync, options)
    ensureCurrent(startedAt)
    open(email, result.session.accessToken, result.kdf, sync, vault)
  }

  function beginSignIn(email: string): number {
    clear()
    // 다른 계정의 revision을 기준으로 되돌림을 판정하지 않도록 계정이 바뀌면 지운다
    if (lastEmail !== email) revisionMemory.reset(0)
    lastEmail = email
    return epoch
  }

  return {
    state(): SessionState {
      if (account === null) return { status: 'signedOut' }
      if (unlocked === null) return { status: 'locked', email: account.email }
      return { status: 'unlocked', email: account.email, vault: unlocked.vault }
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

    /** @throws RevisionRollbackError 서버 볼트가 되돌아감. 사용자가 확인하면 acceptRollback으로 다시 부른다 */
    async login(email: string, password: string, options: RollbackOption = {}): Promise<void> {
      const normalized = normalizeEmail(email)
      const startedAt = beginSignIn(normalized)
      await signIn(normalized, startedAt, await auth.login(email, password), options)
    },

    /** 키와 평문 볼트를 버린다. 진행 중인 잠금 해제·저장도 취소한다 */
    lock(): void {
      epoch += 1
      if (unlocked === null) return
      unlocked = null
      notify(lockListeners)
    },

    /**
     * 저장해 둔 암호화 볼트를 복호화해 비밀번호를 확인하므로 오프라인에서도 동작한다.
     * 확인 뒤 바로 서버 볼트를 받아 오며, 받아 오지 못하면(네트워크·서버 오류) 저장해 둔 볼트로 연다
     * @returns offline: 서버 볼트를 받아 오지 못했는지
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
      let fresh: SyncedVault | null = null
      try {
        fresh = await sync.load(options)
        // 되돌림을 받아들였는데 서버 볼트가 없으면(유실) 이 기기의 볼트를 다시 올린다
        if (fresh.payload === null) fresh = await sync.save(fresh, cached.data)
      } catch (error) {
        if (error instanceof RevisionRollbackError) throw error
        if (error instanceof ServerError && error.code === 'UNAUTHORIZED') {
          clear()
          throw new SessionExpiredError()
        }
        // 그 밖의 실패(오프라인, 서버 오류, 응답 형식 오류)는 저장해 둔 볼트로 연다
      }
      ensureCurrent(startedAt)
      open(locked.email, locked.accessToken, locked.kdf, sync, fresh ?? cached)
      return { offline: fresh === null }
    },

    /** @throws SessionStateError(잠김·취소), 그 밖에 VaultSync.save의 오류 */
    async save(data: VaultData): Promise<void> {
      if (account === null || unlocked === null) throw new SessionStateError('잠금 해제 상태가 아님')
      const { email, accessToken, kdf } = account
      const { sync, vault } = unlocked
      const startedAt = epoch
      const saved = await sync.save(vault, data)
      ensureCurrent(startedAt)
      open(email, accessToken, kdf, sync, saved)
    },

    /**
     * 이 기기의 세션을 먼저 지우고(볼트가 열려 있었으면 잠금 알림), 서버에 토큰 폐기를 요청한다.
     * 서버 요청이 실패하면 오류를 그대로 알린다. 이 기기는 이미 로그아웃된 상태다
     */
    async logout(): Promise<void> {
      const token = account?.accessToken
      clear()
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
  }
}

export type VaultSession = ReturnType<typeof createVaultSession>
