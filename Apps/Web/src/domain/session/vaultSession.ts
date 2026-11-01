// 로그인·가입·잠금·잠금 해제·로그아웃 상태 관리 (SEC-002, SEC-003)
// 잠기면 암호화 키와 평문 볼트를 버리고, 이메일·KDF 값·토큰·암호화 볼트만 남긴다
// 서버에 닿지 못한 저장은 동기화 대기로 두고 나중에 다시 보낸다 (VLT-001)
import { MasterPasswordChangedError, normalizeEmail, type AuthResult, type AuthService, type UnlockKdf } from '../auth/authService'
import { fromBase64 } from '../crypto/base64'
import { deriveKeys } from '../crypto/keys'
import { VaultDecryptError, openVault, type EncryptedVaultPayload } from '../crypto/vaultCipher'
import { createDevicePersistence } from '../device/devicePersistence'
import { DEVICE_RECORD_VERSION, DeviceStorageError, parseDeviceRecord, type DeviceRecord, type DeviceStore } from '../device/deviceRecord'
import { NetworkError, ServerError } from '../server/serverErrors'
import { mergeVaults } from '../vault/mergeVaults'
import { isReadOnly, parseVaultData, type VaultData } from '../vault/vaultData'
import { RevisionRollbackError, VaultSyncConflictError, isTransientSyncError, type RevisionMemory, type SyncedVault, type VaultSync } from '../vault/vaultSync'

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
  | {
      readonly status: 'locked'
      readonly email: string
      /** 서버에 올리지 못한 변경 사항이 암호화된 채 남아 있음 (VLT-001) */
      readonly pendingSync: boolean
    }
  | {
      readonly status: 'unlocked'
      readonly email: string
      readonly vault: SyncedVault
      /** 서버에 올리지 못한 변경 사항이 있음 (VLT-001) */
      readonly pendingSync: boolean
      /** 세션 토큰이 있음. 없으면(오프라인으로 다시 열었을 때) 비밀번호를 다시 입력받아야 동기화한다 (SEC-003) */
      readonly signedIn: boolean
      /** 마지막 기기 저장이 성공함. 실패하면 새로고침 때 올리지 못한 변경 사항이 사라질 수 있다 (VLT-001) */
      readonly storedOnDevice: boolean
    }

export interface VaultSessionDeps {
  readonly auth: AuthService
  /** revisionMemory는 세션이 넘기는 이번 로그인 전용 창이다. 로그인이 바뀐 뒤 늦게 온 응답은 기준을 바꾸지 못한다 */
  readonly vaultSyncFor: (accessToken: string, encryptionKey: CryptoKey, revisionMemory: RevisionMemory) => VaultSync
  /** 되돌림 감지 기준. 로그아웃·계정 전환 때 지운다 */
  readonly revisionMemory: RevisionMemory
  /** 한 계정의 기록을 두는 기기 저장소 (ADR-0019) */
  readonly deviceStore: DeviceStore
}

// 잠겨도 남는 값. 비밀이 아니거나(KDF 값) 암호화돼 있다(볼트)
interface Account {
  readonly email: string
  /** 기기 기록으로 다시 열었고 아직 로그인하지 못했으면 null (ADR-0019) */
  readonly accessToken: string | null
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

function sameKdf(a: UnlockKdf, b: UnlockKdf): boolean {
  return a.salt === b.salt && a.iterations === b.iterations
}

function samePayload(a: EncryptedVaultPayload, b: EncryptedVaultPayload): boolean {
  return a.version === b.version && a.cipher === b.cipher && a.iv === b.iv && a.ciphertext === b.ciphertext && a.tag === b.tag
}

function sameRecord(a: DeviceRecord, b: DeviceRecord): boolean {
  return (
    a.email === b.email &&
    sameKdf(a.kdf, b.kdf) &&
    samePayload(a.payload, b.payload) &&
    a.revision === b.revision &&
    a.lastSeenRevision === b.lastSeenRevision &&
    a.pendingSync === b.pendingSync
  )
}

// 세션 토큰 없이 연 볼트의 동기화 객체에 넘기는 값. pushPending·syncAfterUnlock이 토큰 없이는 요청하지 않는다
const NO_TOKEN = ''

export function createVaultSession({ auth, vaultSyncFor, revisionMemory, deviceStore }: VaultSessionDeps) {
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
  // 충돌이 아닌 분명한 거절을 받으면 잠금 해제·저장·로그인 전까지 자동으로 다시 보내지 않는다
  let retryBlocked = false
  const lockListeners = new Set<() => void>()
  const unlockListeners = new Set<() => void>()
  const syncListeners = new Set<SyncChangeListener>()
  const device = createDevicePersistence(deviceStore, (error) => notifySync(error))
  // 기기 기록의 계정과 KDF 값. 같은 이메일로 로그인할 때 KDF 값이 바뀌었는지 본다 (ADR-0019)
  let deviceIdentity: { readonly email: string; readonly kdf: UnlockKdf } | null = null
  let lastWritten: DeviceRecord | null = null
  // 기기 기록을 읽었는지. 시작할 때 읽지 못했으면 처음 쓰기 전에 다시 읽어 덮어쓰지 않게 한다 (ADR-0020)
  let deviceRead = false

  function notify(listeners: Set<() => void>) {
    for (const listener of listeners) listener()
  }

  function notifySync(error: unknown) {
    for (const listener of syncListeners) listener(error)
  }

  function ensureCurrent(startedAt: number) {
    if (epoch !== startedAt) throw new SessionStateError('기다리는 사이 잠금 또는 로그아웃되어 작업을 취소함')
  }

  /** 이번 로그인에서만 기준을 바꾸는 되돌림 기억. 로그아웃·다른 로그인 뒤 늦게 온 응답은 무시한다 */
  function memoryForThisSignIn(): RevisionMemory {
    const owner = generation
    return {
      get: () => revisionMemory.get(),
      raise: (revision) => {
        if (generation === owner) revisionMemory.raise(revision)
      },
      reset: (revision) => {
        if (generation === owner) revisionMemory.reset(revision)
      },
    }
  }

  function syncFor(accessToken: string, encryptionKey: CryptoKey): VaultSync {
    return vaultSyncFor(accessToken, encryptionKey, memoryForThisSignIn())
  }

  /** 쓰지 않게 된 토큰을 서버에서 폐기한다. 실패해도 토큰 만료(30일)로 정리된다 (SEC-002) */
  function revoke(accessToken: string) {
    auth.logout(accessToken).catch(() => {
      // 폐기 요청 실패는 사용자에게 알릴 일이 아니다. 토큰은 만료로 정리된다
    })
  }

  /** 기다리는 사이 잠금·로그아웃됐으면 새로 받은 토큰을 폐기하고 취소한다 */
  function ensureCurrentOrRevoke(startedAt: number, newToken: string | null) {
    if (epoch === startedAt) return
    if (newToken !== null) revoke(newToken)
    ensureCurrent(startedAt)
  }

  /** 계정 기록을 기기에 쓴다. 다른 계정의 기록은 덮어써 지워진다 (한 계정만, ADR-0019). 실패는 알리고 거절하지 않는다 */
  async function persist(): Promise<void> {
    if (account === null) return
    const { email, kdf, payload, revision, pendingSync } = account
    deviceIdentity = { email, kdf }
    const record: DeviceRecord = { version: DEVICE_RECORD_VERSION, email, kdf, payload, revision, lastSeenRevision: revisionMemory.get(), pendingSync }
    // 볼트는 5MB까지 크므로 바뀐 것이 없으면(잠금 해제 직후 같은 볼트 등) 다시 쓰지 않는다
    if (!device.hasFailed() && lastWritten !== null && sameRecord(lastWritten, record)) return
    lastWritten = record
    await device.save(record)
  }

  /** @returns 기기 기록 쓰기. 실패해도 거절하지 않는다 */
  function open(email: string, accessToken: string | null, kdf: UnlockKdf, sync: VaultSync, vault: SyncedVault, pendingSync = false): Promise<void> {
    // 모든 계정이 볼트를 가지므로 여기서 payload가 없을 수 없다 (SEC-003)
    if (vault.payload === null) throw new SessionStateError('볼트 없이 잠금을 해제할 수 없음')
    const wasLocked = unlocked === null
    account = { email, accessToken, kdf, payload: vault.payload, revision: vault.revision, pendingSync }
    unlocked = { sync, vault, pendingSync }
    if (wasLocked) notify(unlockListeners)
    return persist()
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
    // 세션 없이 열었으면 비밀번호를 다시 입력받아 로그인할 때까지 보내지 않는다 (SEC-003)
    if (account.accessToken === null) return 'stillPending'
    const startedAt = epoch
    const startedGeneration = generation
    const { email, accessToken, kdf } = account
    const { sync, vault } = unlocked
    let saved: SyncedVault
    try {
      saved = await sync.save(vault, vault.data)
    } catch (error) {
      if (isTransientSyncError(error)) return 'stillPending'
      // 그사이 로그아웃·다른 로그인이 있었으면 이전 세션의 실패를 지금 세션에 알리지 않는다
      if (generation !== startedGeneration) return 'idle'
      if (isUnauthorized(error)) throw expire()
      // 잠금 전에 보낸 요청의 늦은 거절이 다시 연 세션의 재전송을 막지 않게 한다
      if (epoch === startedAt && !(error instanceof VaultSyncConflictError)) retryBlocked = true
      throw error
    }
    if (generation !== startedGeneration || account === null) return 'idle'
    if (unlocked === null) {
      // 보내는 사이 잠겼다. 잠기기 전에 더 고친 것이 없으면 올린 볼트를 잠긴 상태에 남긴다
      if (account.payload === vault.payload && saved.payload !== null) {
        account = { ...account, payload: saved.payload, revision: saved.revision, pendingSync: false }
        await persist()
        return 'synced'
      }
      return 'idle'
    }
    // 보내는 사이 또 고쳤으면 새 수정은 다음 차례에 보낸다. 서버가 앞서 있으니 병합 절차를 탄다
    if (epoch !== startedAt || unlocked.vault !== vault) return 'stillPending'
    await open(email, accessToken, kdf, sync, saved)
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

  /**
   * 세션 만료 전에 남긴 대기 내용을 서버 볼트와 병합해 올린다. 서버에 닿지 못하면 병합한 볼트를 대기로 연다.
   * 대기 내용은 볼트가 열린 뒤에야 지운다. 충돌·되돌림이면 로그인을 실패시키고 남겨 다음 로그인 때 다시 올린다.
   * 서버가 받을 수 없는 대기 내용(읽기 전용, 용량 초과 등)이면 서버 볼트로 열고 dropped로 알린다
   */
  async function adoptOrphan(email: string, encryptionKey: CryptoKey, sync: VaultSync, vault: SyncedVault) {
    const pending = orphan
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
   * 시작할 때 기기 기록을 읽지 못했으면(열기 시간 초과 등) 지금 다시 읽는다. 같은 계정의 대기 내용이 있으면
   * 세션 만료로 남긴 것처럼 받아 병합해 올리고, 다른 계정의 기록이면 아래에서 지운다 (ADR-0020)
   */
  async function readDeviceIfUnread(email: string) {
    if (deviceRead) return
    let record: DeviceRecord | null
    try {
      record = parseDeviceRecord(await deviceStore.load())
    } catch {
      // 또 읽지 못하면 그대로 진행한다. 쓰기도 실패할 가능성이 크며, 실패는 쓸 때 알린다
      return
    }
    deviceRead = true
    if (record === null) return
    deviceIdentity = { email: record.email, kdf: record.kdf }
    if (record.email !== email) return
    revisionMemory.raise(record.lastSeenRevision)
    if (record.pendingSync && orphan === null) orphan = { email, payload: record.payload, revision: record.revision }
  }

  async function signIn(email: string, startedAt: number, result: AuthResult, options: RollbackOption) {
    ensureCurrent(startedAt)
    await readDeviceIfUnread(email)
    ensureCurrent(startedAt)
    if (deviceIdentity !== null && deviceIdentity.email !== email) {
      // 다른 계정의 인증이 성공했다. 볼트를 준비하지 못하더라도 이전 계정의 기록을 남기지 않는다 (ADR-0019)
      deviceIdentity = null
      lastWritten = null
      await device.clear()
      ensureCurrent(startedAt)
    } else if (deviceIdentity?.email === email && !sameKdf(deviceIdentity.kdf, result.kdf)) {
      // 비밀번호가 바뀌었다. 옛 키의 대기 내용은 열 수 없으니 버린다. 되돌림 검사는 그대로 해서,
      // 서버가 KDF 값만 바꿔 유실을 감추고 기기 사본을 덮어쓰게 하지 못한다. 기기 사본은 볼트를 연 뒤에야 바뀐다 (ADR-0019)
      orphan = null
    }
    const sync = syncFor(result.session.accessToken, result.encryptionKey)
    const prepared = await prepareVault(sync, options)
    ensureCurrent(startedAt)
    const adopted: { vault: SyncedVault; pendingSync: boolean; dropped?: unknown } = await adoptOrphan(email, result.encryptionKey, sync, prepared)
    ensureCurrent(startedAt)
    await open(email, result.session.accessToken, result.kdf, sync, adopted.vault, adopted.pendingSync)
    // 올렸거나, 이 볼트의 대기로 옮겼거나, 서버가 받을 수 없어 버렸다. 다른 계정의 대기 내용이면 그 계정이 열렸으므로 버린다 (VLT-001)
    orphan = null
    if (adopted.dropped !== undefined) notifySync(adopted.dropped)
  }

  function beginSignIn(email: string): number {
    clear()
    // 다른 계정의 revision을 기준으로 되돌림을 판정하지 않도록 계정이 바뀌면 지운다
    if (lastEmail !== email) revisionMemory.reset(0)
    lastEmail = email
    retryBlocked = false
    return epoch
  }

  /**
   * 잠금 해제 직후 서버와 맞춘다. 대기 내용이 있으면 서버 볼트로 덮지 않고 병합해 올린다
   * @returns 서버와 맞춘 볼트. 서버에 닿지 못하거나 서버 오류·응답 형식 오류면 null
   * @throws RevisionRollbackError, SessionExpiredError, SessionStateError(그사이 로그아웃·다른 로그인)
   */
  async function syncAfterUnlock(sync: VaultSync, cached: SyncedVault, pendingSync: boolean, options: RollbackOption) {
    const startedGeneration = generation
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
        if (generation !== startedGeneration) throw new SessionStateError('기다리는 사이 로그아웃되어 작업을 취소함')
        throw expire()
      }
      return null
    }
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
  async function resumeSession(target: Account, authHash: Uint8Array): Promise<{ accessToken: string | null; failure?: unknown }> {
    const startedGeneration = generation
    try {
      return { accessToken: (await auth.resume(target.email, target.kdf, authHash)).accessToken }
    } catch (error) {
      if (error instanceof MasterPasswordChangedError) {
        // 기기 기록은 새 비밀번호로 로그인에 성공할 때 버린다 (ADR-0020)
        if (generation === startedGeneration) clear()
        throw error
      }
      // 그 밖의 실패여도 비밀번호는 이 기기에서 확인했으니 볼트는 연다. 사유는 부른 쪽이 알린다
      return { accessToken: null, failure: error }
    } finally {
      authHash.fill(0)
    }
  }

  return {
    state(): SessionState {
      if (account === null) return { status: 'signedOut' }
      if (unlocked === null) return { status: 'locked', email: account.email, pendingSync: account.pendingSync }
      return { status: 'unlocked', email: account.email, vault: unlocked.vault, pendingSync: unlocked.pendingSync, signedIn: account.accessToken !== null, storedOnDevice: !device.hasFailed() }
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

      const { keys, data } = await verifyPassword(password, locked)
      // 토큰이 있으면 인증 해시가 필요 없어 바로 지운다. 없으면(기기 기록으로 다시 열었으면) 바로 로그인하고 지운다 (SEC-003, ADR-0019)
      let accessToken = locked.accessToken
      if (accessToken === null) {
        const resumed = await resumeSession(locked, keys.authHash)
        accessToken = resumed.accessToken
        // 오프라인은 조용히 세션 없이 연다. 서버가 거절했으면(출처 거부 등) 사유를 알린다
        if (resumed.failure !== undefined && !isTransientSyncError(resumed.failure) && epoch === startedAt) notifySync(resumed.failure)
      }
      keys.authHash.fill(0)
      ensureCurrentOrRevoke(startedAt, locked.accessToken === null ? accessToken : null)
      // 받은 토큰은 되돌림 확인으로 잠긴 채 끝나도 남겨, 다시 풀 때 또 로그인하지 않게 한다
      if (locked.accessToken === null && accessToken !== null) account = { ...locked, accessToken }

      const sync = syncFor(accessToken ?? NO_TOKEN, keys.encryptionKey)
      const cached: SyncedVault = { revision: locked.revision, data, readOnly: isReadOnly(data), payload: locked.payload }
      retryBlocked = false
      const fresh = accessToken === null ? null : await syncAfterUnlock(sync, cached, locked.pendingSync, options)
      ensureCurrent(startedAt)
      await open(locked.email, accessToken, locked.kdf, sync, fresh ?? cached, fresh === null && locked.pendingSync)
      return { offline: fresh === null }
    },

    /**
     * 세션 없이 연 볼트에서 비밀번호를 다시 입력받아 로그인하고 서버와 맞춘다 (SEC-003, ADR-0019)
     * @throws WrongMasterPasswordError, 로그인하지 못한 실제 사유(NetworkError, ServerError 등), MasterPasswordChangedError(로그아웃됨),
     *   RevisionRollbackError, SessionExpiredError, SessionStateError
     */
    async reconnect(password: string, options: RollbackOption = {}): Promise<void> {
      if (account === null || unlocked === null) throw new SessionStateError('잠금 해제 상태가 아님')
      if (account.accessToken !== null) return
      const current = account
      const startedAt = epoch
      const { keys } = await verifyPassword(password, current)
      const { accessToken, failure } = await resumeSession(current, keys.authHash)
      // 왜 동기화하지 못하는지 알 수 있게 실제 사유(연결 실패, 서버 오류, 거절)를 그대로 낸다
      if (accessToken === null) throw failure ?? new NetworkError()
      ensureCurrentOrRevoke(startedAt, accessToken)
      const opened = unlocked
      if (opened === null) throw new SessionStateError('잠금 해제 상태가 아님')
      const sync = syncFor(accessToken, keys.encryptionKey)
      retryBlocked = false
      let fresh: SyncedVault | null
      try {
        // 서버와 맞추기 전에는 토큰을 세션에 넣지 않는다. 되돌림이면 세션 없이 두고 확인을 받는다
        fresh = await syncAfterUnlock(sync, opened.vault, opened.pendingSync, options)
      } catch (error) {
        revoke(accessToken)
        throw error
      }
      ensureCurrentOrRevoke(startedAt, accessToken)
      const latest = unlocked
      if (latest === null) throw new SessionStateError('잠금 해제 상태가 아님')
      // 다시 닿지 못했거나 맞추는 사이 또 고쳤으면 지금 볼트를 대기로 두고 보낸다. 서버가 앞서 있으면 병합 절차를 탄다
      if (fresh === null || latest.vault !== opened.vault) {
        const pending = latest.pendingSync || latest.vault !== opened.vault
        await open(current.email, accessToken, current.kdf, sync, latest.vault, pending)
        await enqueuePush()
        return
      }
      await open(current.email, accessToken, current.kdf, sync, fresh)
    },

    /**
     * 먼저 이 기기에 반영한 뒤 서버에 보낸다. 서버에 닿지 못하면 동기화 대기로 두고 성공으로 끝난다 (VLT-001)
     * @throws SessionStateError(잠김·취소), 보내기 전 검사 오류(ReadOnlyVaultError, VaultTooLargeError, InvalidVaultDataError),
     *   SessionExpiredError, 그 밖에 VaultSync.save의 분명한 거절. 분명한 거절이어도 변경 사항은 대기로 남는다
     */
    async save(data: VaultData): Promise<void> {
      if (account === null || unlocked === null) throw new SessionStateError('잠금 해제 상태가 아님')
      const { vault } = unlocked
      const startedAt = epoch
      const payload = await unlocked.sync.seal(data)
      ensureCurrent(startedAt)
      // 암호화하는 사이 다시 로그인했을 수 있으니 지금 계정의 토큰과 동기화 객체를 쓴다
      const latestAccount = account
      const latest = unlocked
      if (latestAccount === null || latest === null) throw new SessionStateError('잠금 해제 상태가 아님')
      const { email, accessToken, kdf } = latestAccount
      // 기준 revision은 마지막으로 서버와 맞춘 값 그대로다. 서버가 앞서 있으면 보낼 때 병합한다
      await open(email, accessToken, kdf, latest.sync, { revision: vault.revision, data, readOnly: false, payload }, true)
      retryBlocked = false
      await enqueuePush()
    },

    /**
     * 서버에 올리지 못한 변경 사항이 기기에도 아직 저장되지 않았는지(쓰는 중이거나 실패). 떠남 확인용 (VLT-001).
     * 잠겼거나 세션 만료로 로그아웃된 뒤 남긴 것도 센다
     */
    hasUnsavedChanges(): boolean {
      return (account?.pendingSync === true || orphan !== null) && device.isBehind()
    },

    /**
     * 기기 기록이 있으면 그 계정의 잠금 화면으로 시작한다 (SEC-003, ADR-0019). 앱을 열 때 한 번 부른다.
     * 기록이 없거나 규격에 맞지 않으면 로그인 전 상태로 둔다
     * @throws DeviceStorageError 기기 저장소를 읽지 못함
     */
    async restore(): Promise<void> {
      const record = parseDeviceRecord(await deviceStore.load())
      deviceRead = true
      if (record === null || account !== null) return
      lastWritten = record
      const { email, kdf, payload, revision, pendingSync } = record
      account = { email, accessToken: null, kdf, payload, revision, pendingSync }
      deviceIdentity = { email, kdf }
      lastEmail = email
      revisionMemory.reset(record.lastSeenRevision)
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
     * 이 기기의 세션과 기기 기록을 먼저 지우고(볼트가 열려 있었으면 잠금 알림), 서버에 토큰 폐기를 요청한다.
     * 동기화 대기 내용도 버린다. 서버 요청이 실패하면 오류를 그대로 알린다. 이 기기는 이미 로그아웃된 상태다
     * @throws DeviceStorageError 기기 기록을 지우지 못함(서버 폐기 오류보다 먼저 알린다), 그 밖에 서버 폐기 요청 오류
     */
    async logout(): Promise<void> {
      const token = account?.accessToken
      clear()
      orphan = null
      deviceIdentity = null
      revisionMemory.reset(0)
      lastEmail = null
      lastWritten = null
      // 이 기기의 기록도 지운다 (ADR-0019). 지우지 못했으면 서버 폐기 결과보다 먼저 알린다
      const cleared = await device.clear()
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
