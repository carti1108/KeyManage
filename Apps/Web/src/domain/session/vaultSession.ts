// 로그인·가입·잠금·잠금 해제·로그아웃 상태 관리 (SEC-002, SEC-003)
// 잠기면 암호화 키와 평문 볼트를 버리고, 이메일·KDF 값·토큰·암호화 볼트만 남긴다
import { normalizeEmail, type AuthResult, type AuthService, type UnlockKdf } from '../auth/authService'
import { fromBase64 } from '../crypto/base64'
import { deriveKeys } from '../crypto/keys'
import { VaultDecryptError, openVault, type EncryptedVaultPayload } from '../crypto/vaultCipher'
import { NetworkError } from '../server/serverErrors'
import { isReadOnly, parseVaultData, type VaultData } from '../vault/vaultData'
import type { SyncedVault, VaultSync } from '../vault/vaultSync'

/** 마스터 비밀번호가 틀려 저장해 둔 볼트를 열 수 없음 */
export class WrongMasterPasswordError extends Error {
  constructor() {
    super('마스터 비밀번호가 올바르지 않음')
    this.name = 'WrongMasterPasswordError'
  }
}

/** 지금 상태에서는 할 수 없는 동작 (예: 잠긴 상태에서 저장) */
export class SessionStateError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'SessionStateError'
  }
}

export type SessionState =
  | { readonly status: 'signedOut' }
  | { readonly status: 'locked'; readonly email: string }
  | { readonly status: 'unlocked'; readonly email: string; readonly vault: SyncedVault }

export interface VaultSessionDeps {
  readonly auth: AuthService
  readonly vaultSyncFor: (accessToken: string, encryptionKey: CryptoKey) => VaultSync
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

export function createVaultSession({ auth, vaultSyncFor }: VaultSessionDeps) {
  let account: Account | null = null
  let unlocked: Unlocked | null = null
  const lockListeners = new Set<() => void>()
  const unlockListeners = new Set<() => void>()

  function notify(listeners: Set<() => void>) {
    for (const listener of listeners) listener()
  }

  function open(email: string, accessToken: string, kdf: UnlockKdf, sync: VaultSync, vault: SyncedVault) {
    // 모든 계정이 볼트를 가지므로 여기서 payload가 없을 수 없다 (SEC-003)
    if (vault.payload === null) throw new SessionStateError('볼트 없이 잠금을 해제할 수 없음')
    const wasLocked = unlocked === null
    account = { email, accessToken, kdf, payload: vault.payload, revision: vault.revision }
    unlocked = { sync, vault }
    if (wasLocked) notify(unlockListeners)
  }

  async function signIn(email: string, result: AuthResult) {
    const sync = vaultSyncFor(result.session.accessToken, result.encryptionKey)
    let vault = await sync.load()
    // 볼트가 없으면(가입 직후, 가입 때 저장 실패) 열기 전에 빈 볼트를 저장한다. 잠금 해제 확인에 필요하다 (SEC-003)
    if (vault.payload === null) vault = await sync.save(vault, vault.data)
    open(email, result.session.accessToken, result.kdf, sync, vault)
  }

  function requireUnlocked(): { account: Account; unlocked: Unlocked } {
    if (account === null || unlocked === null) throw new SessionStateError('잠금 해제 상태가 아님')
    return { account, unlocked }
  }

  return {
    state(): SessionState {
      if (account === null) return { status: 'signedOut' }
      if (unlocked === null) return { status: 'locked', email: account.email }
      return { status: 'unlocked', email: account.email, vault: unlocked.vault }
    },

    async register(email: string, password: string): Promise<void> {
      await signIn(normalizeEmail(email), await auth.register(email, password))
    },

    async login(email: string, password: string): Promise<void> {
      await signIn(normalizeEmail(email), await auth.login(email, password))
    },

    /** 키와 평문 볼트를 버린다. 잠금 해제 상태일 때만 알림을 보낸다 */
    lock(): void {
      if (unlocked === null) return
      unlocked = null
      notify(lockListeners)
    },

    /**
     * 저장해 둔 암호화 볼트를 복호화해 비밀번호를 확인하므로 오프라인에서도 동작한다.
     * 확인 뒤에는 바로 서버 볼트를 받아 오고, 서버에 닿지 못하면 저장해 둔 볼트로 연다
     * @param options.acceptRollback 서버 볼트 되돌림 경고를 사용자가 확인했을 때만 true (VLT-001)
     * @returns offline: 서버 볼트를 받아 오지 못했는지
     * @throws WrongMasterPasswordError, SessionStateError, RevisionRollbackError(잠긴 상태 유지)
     */
    async unlock(password: string, options: { acceptRollback?: boolean } = {}): Promise<{ offline: boolean }> {
      if (account === null) throw new SessionStateError('로그인 전에는 잠금을 해제할 수 없음')
      if (unlocked !== null) return { offline: false }
      const locked = account

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

      const sync = vaultSyncFor(locked.accessToken, keys.encryptionKey)
      const cached: SyncedVault = { revision: locked.revision, data, readOnly: isReadOnly(data), payload: locked.payload }
      try {
        open(locked.email, locked.accessToken, locked.kdf, sync, await sync.load(options))
        return { offline: false }
      } catch (error) {
        if (!(error instanceof NetworkError)) throw error
        open(locked.email, locked.accessToken, locked.kdf, sync, cached)
        return { offline: true }
      }
    },

    /** @throws SessionStateError, 그 밖에 VaultSync.save의 오류 */
    async save(data: VaultData): Promise<void> {
      const current = requireUnlocked()
      const saved = await current.unlocked.sync.save(current.unlocked.vault, data)
      open(current.account.email, current.account.accessToken, current.account.kdf, current.unlocked.sync, saved)
    },

    /** 서버 요청이 실패해도 이 기기에서는 로그아웃한다. 볼트가 열려 있었으면 잠금 알림도 보낸다 */
    async logout(): Promise<void> {
      const token = account?.accessToken
      const wasUnlocked = unlocked !== null
      account = null
      unlocked = null
      if (wasUnlocked) notify(lockListeners)
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
