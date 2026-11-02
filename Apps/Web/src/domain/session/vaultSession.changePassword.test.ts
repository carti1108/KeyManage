// @vitest-environment node
// 마스터 비밀번호 변경: 새 키로 볼트를 다시 암호화해 한 요청으로 바꾸고 다른 기기·탭은 로그인 화면으로 보낸다 (SEC-004, ADR-0023)
import { describe, expect, it, vi } from 'vitest'
import { FakeAuthApi, FakeDeviceStore, FakeVaultApi, NOW, PASSWORD, fields, restart } from '../../test/sessionFixtures'
import { MasterPasswordChangedError, createAuthService } from '../auth/authService'
import { fromBase64 } from '../crypto/base64'
import { deriveKeys } from '../crypto/keys'
import { keysOf, vectors } from '../crypto/testVectors'
import { openVault, sealVault, type EncryptedVaultPayload } from '../crypto/vaultCipher'
import { ServerError } from '../server/serverErrors'
import { createInMemoryTabHub, createSingleTabFallback } from '../tabs/inMemoryTabs'
import type { Tabs } from '../tabs/tabs'
import type { VaultApi } from '../vault/vaultApi'
import { activeItems, addItem, parseVaultData, type VaultData } from '../vault/vaultData'
import { ReadOnlyVaultError, VaultSyncConflictError, createRevisionMemory, createVaultSync } from '../vault/vaultSync'
import {
  NewMasterPasswordTooShortError,
  PasswordChangeOfflineError,
  SameMasterPasswordError,
  SessionExpiredError,
  SessionStateError,
  WrongMasterPasswordError,
  createVaultSession,
  type VaultSession,
} from './vaultSession'

const EMAIL = 'user@example.com'
const NEW_PASSWORD = 'a brand new master password'

interface Server {
  readonly authApi: FakeAuthApi
  readonly vaultApi: FakeVaultApi
}

function newServer(): Server {
  const authApi = new FakeAuthApi()
  const vaultApi = new FakeVaultApi()
  authApi.vaultApi = vaultApi
  return { authApi, vaultApi }
}

/** 실제 서버처럼 폐기된 토큰의 볼트 요청을 401로 거절하고, 볼트 요청에 쓴 토큰을 기록한다 */
function tokenChecked(server: Server, token: string, used: string[]): VaultApi {
  const check = () => {
    used.push(token)
    if (server.authApi.revoked.has(token)) throw new ServerError(401, 'UNAUTHORIZED')
  }
  return {
    get: async () => {
      check()
      return server.vaultApi.get()
    },
    save: async (expectedRevision, payload) => {
      check()
      return server.vaultApi.save(expectedRevision, payload)
    },
  }
}

/** 한 기기(또는 탭). 같은 서버·기기 저장소·탭 허브를 넘기면 같은 브라우저의 다른 탭이 된다 */
function device(server: Server = newServer(), deviceStore = new FakeDeviceStore(), tabs: Tabs = createSingleTabFallback()) {
  const tokens: string[] = []
  const syncErrors: unknown[] = []
  const session = createVaultSession({
    auth: createAuthService(server.authApi),
    vaultSyncFor: (token, encryptionKey, revisionMemory) =>
      createVaultSync({ api: tokenChecked(server, token, tokens), encryptionKey, now: () => NOW, revisionMemory }),
    revisionMemory: createRevisionMemory(),
    deviceStore,
    tabs,
  })
  session.onSyncChange((error) => syncErrors.push(error))
  return { session, ...server, server, deviceStore, tokens, syncErrors }
}

async function signedIn(server?: Server) {
  const ctx = device(server)
  await ctx.session.login(EMAIL, PASSWORD)
  return ctx
}

function unlockedData(session: VaultSession): VaultData {
  const state = session.state()
  if (state.status !== 'unlocked') throw new Error('잠금 해제 상태가 아님')
  return state.vault.data
}

function itemIds(data: VaultData): string[] {
  return activeItems(data)
    .map((item) => item.id)
    .sort()
}

function saveItem(session: VaultSession, id: string) {
  return session.save(addItem(unlockedData(session), { ...fields, serviceName: id }, NOW, id))
}

/** 서버의 KDF 값과 비밀번호로 페이로드를 열어 항목 id를 준다 */
async function openedIds(payload: EncryptedVaultPayload | null | undefined, kdf: { salt: string; iterations: number }, password: string) {
  if (payload == null) throw new Error('볼트가 없음')
  const { encryptionKey } = await deriveKeys(password, fromBase64(kdf.salt), kdf.iterations)
  return itemIds(parseVaultData(await openVault(encryptionKey, payload)))
}

function gate(target: { changeGate: Promise<void> | null }) {
  let release!: () => void
  target.changeGate = new Promise<void>((resolve) => (release = resolve))
  return () => {
    target.changeGate = null
    release()
  }
}

describe('마스터 비밀번호 변경 전 검사 (SEC-004 규칙 1·2)', () => {
  it('새 비밀번호가 12자보다 짧으면 서버에 보내지 않고 거절한다', async () => {
    const { session, authApi } = await signedIn()

    await expect(session.changeMasterPassword(PASSWORD, 'short pw 11')).rejects.toBeInstanceOf(NewMasterPasswordTooShortError)
    expect(authApi.changes).toHaveLength(0)
  })

  it('새 비밀번호가 지금 비밀번호와 같으면 서버에 보내지 않고 거절한다', async () => {
    const { session, authApi } = await signedIn()

    await expect(session.changeMasterPassword(PASSWORD, PASSWORD)).rejects.toBeInstanceOf(SameMasterPasswordError)
    expect(authApi.changes).toHaveLength(0)
  })

  it('세션 없이 연 볼트(오프라인)에서는 서버에 보내지 않고 거절한다', async () => {
    const first = await signedIn()
    first.authApi.offline = true
    const reopened = device(first.server, first.deviceStore)
    await reopened.session.restore()
    await reopened.session.unlock(PASSWORD)
    first.authApi.offline = false

    await expect(reopened.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)).rejects.toBeInstanceOf(PasswordChangeOfflineError)
    expect(first.authApi.changes).toHaveLength(0)
  })

  it('읽기 전용 볼트에서는 거절한다', async () => {
    const server = newServer()
    const { encryptionKey } = await keysOf(vectors.keyDerivationCases[0].name)
    server.vaultApi.payload = await sealVault(encryptionKey, JSON.stringify({ version: 2, updatedAt: '2026-10-30T00:00:00Z', items: [] }))
    server.vaultApi.revision = 1
    const { session, authApi } = await signedIn(server)

    await expect(session.changeMasterPassword(PASSWORD, NEW_PASSWORD)).rejects.toBeInstanceOf(ReadOnlyVaultError)
    expect(authApi.changes).toHaveLength(0)
  })

  it('잠긴 상태에서는 거절한다', async () => {
    const { session } = await signedIn()
    session.lock()

    await expect(session.changeMasterPassword(PASSWORD, NEW_PASSWORD)).rejects.toBeInstanceOf(SessionStateError)
  })

  it('현재 비밀번호가 틀리면 서버에 보내지 않고 WrongMasterPasswordError', async () => {
    const { session, authApi } = await signedIn()

    await expect(session.changeMasterPassword('not the password', NEW_PASSWORD)).rejects.toBeInstanceOf(WrongMasterPasswordError)
    expect(authApi.changes).toHaveLength(0)
  })
})

describe('마스터 비밀번호 변경 요청 (SEC-004 규칙 4·5)', () => {
  it('새 salt 16바이트와 지금 반복 횟수로 만든 키·인증 해시와 다시 암호화한 볼트를 한 요청으로 보낸다', async () => {
    const { session, authApi, vaultApi } = await signedIn()
    const oldKdf = authApi.kdf
    const oldHash = authApi.authHash
    await saveItem(session, 'a')

    await session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

    const [{ accessToken, request }] = authApi.changes
    expect(accessToken).toBe('token-1')
    expect(request.currentAuthHash).toBe(oldHash)
    expect(request.newAuthHash).not.toBe(oldHash)
    expect(fromBase64(request.kdf.salt)).toHaveLength(16)
    expect(request.kdf.salt).not.toBe(oldKdf.salt)
    expect(request.kdf.iterations).toBe(oldKdf.iterations)
    expect(request.vault.expectedRevision).toBe(2)
    expect(vaultApi.revision).toBe(3)
    expect(await openedIds(vaultApi.payload, authApi.kdf, NEW_PASSWORD)).toEqual(['a'])
  })

  it('동기화 대기 중인 변경까지 새 키로 다시 암호화해 올리고 대기 표시를 지운다', async () => {
    const { session, authApi, vaultApi } = await signedIn()
    vaultApi.offline = true
    await saveItem(session, 'pending')
    vaultApi.offline = false
    const saves = vaultApi.saves

    await session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

    expect(await openedIds(vaultApi.payload, authApi.kdf, NEW_PASSWORD)).toEqual(['pending'])
    expect(vaultApi.saves).toBe(saves)
    expect(session.state()).toEqual(expect.objectContaining({ status: 'unlocked', pendingSync: false }))
  })

  it('서버가 현재 인증 해시를 거절하면 WrongMasterPasswordError이고 옛 비밀번호로 계속 쓴다', async () => {
    const { session, authApi } = await signedIn()
    authApi.changeError = new ServerError(401, 'AUTH_FAILED')

    await expect(session.changeMasterPassword(PASSWORD, NEW_PASSWORD)).rejects.toBeInstanceOf(WrongMasterPasswordError)

    expect(session.state().status).toBe('unlocked')
    session.lock()
    await session.unlock(PASSWORD)
  })

  it('다른 기기가 먼저 저장해 충돌하면 VaultSyncConflictError이고 아무것도 바뀌지 않는다', async () => {
    const { session, authApi, vaultApi, deviceStore } = await signedIn()
    const kdfBefore = deviceStore.record?.kdf
    vaultApi.revision += 1

    await expect(session.changeMasterPassword(PASSWORD, NEW_PASSWORD)).rejects.toBeInstanceOf(VaultSyncConflictError)

    expect(authApi.kdf.salt).toBe(vectors.keyDerivationCases[0].salt)
    expect(deviceStore.record?.kdf).toEqual(kdfBefore)
    session.lock()
    await session.unlock(PASSWORD)
  })

  it('시도 제한(429)은 ServerError TOO_MANY_ATTEMPTS로 그대로 알린다', async () => {
    const { session, authApi } = await signedIn()
    authApi.changeError = new ServerError(429, 'TOO_MANY_ATTEMPTS', 900)

    await expect(session.changeMasterPassword(PASSWORD, NEW_PASSWORD)).rejects.toEqual(expect.objectContaining({ code: 'TOO_MANY_ATTEMPTS' }))
    expect(session.state().status).toBe('unlocked')
  })

  it('세션이 만료됐으면 SessionExpiredError로 로그아웃한다', async () => {
    const { session, authApi } = await signedIn()
    authApi.changeError = new ServerError(401, 'UNAUTHORIZED')

    await expect(session.changeMasterPassword(PASSWORD, NEW_PASSWORD)).rejects.toBeInstanceOf(SessionExpiredError)
    expect(session.state().status).toBe('signedOut')
  })
})

describe('마스터 비밀번호 변경 뒤 (SEC-004 규칙 7·8)', () => {
  it('새 토큰으로 바꾸고 이후 요청은 새 토큰으로 보낸다', async () => {
    const { session, tokens } = await signedIn()
    await session.changeMasterPassword(PASSWORD, NEW_PASSWORD)
    tokens.length = 0

    await saveItem(session, 'after')

    expect(tokens).toEqual(['token-2'])
    expect(session.state()).toEqual(expect.objectContaining({ status: 'unlocked', pendingSync: false, signedIn: true }))
  })

  it('잠근 뒤 새 비밀번호로 잠금을 해제하고 옛 비밀번호는 거절한다', async () => {
    const { session } = await signedIn()
    await saveItem(session, 'a')
    await session.changeMasterPassword(PASSWORD, NEW_PASSWORD)
    session.lock()

    await expect(session.unlock(PASSWORD)).rejects.toBeInstanceOf(WrongMasterPasswordError)
    await session.unlock(NEW_PASSWORD)

    expect(itemIds(unlockedData(session))).toEqual(['a'])
  })

  it('기기 기록을 새 KDF 값과 새 키 암호문으로 바꿔 새로고침 뒤에도 새 비밀번호로 연다', async () => {
    const first = await signedIn()
    await saveItem(first.session, 'a')
    await first.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

    expect(first.deviceStore.record?.kdf).toEqual({ salt: first.authApi.kdf.salt, iterations: first.authApi.kdf.iterations })
    expect(first.deviceStore.record?.pendingSync).toBe(false)
    const reopened = await restart(first)
    await reopened.session.unlock(NEW_PASSWORD)
    expect(itemIds(unlockedData(reopened.session))).toEqual(['a'])
  })

  it('요청을 기다리는 사이 이 탭에서 저장한 변경은 새 키로 대기에 남겨 다음 전송에 올린다', async () => {
    const { session, authApi, vaultApi } = await signedIn()
    const release = gate(authApi)
    const changing = session.changeMasterPassword(PASSWORD, NEW_PASSWORD)
    await vi.waitFor(() => expect(authApi.changes).toHaveLength(1))
    vaultApi.offline = true
    await saveItem(session, 'during')

    release()
    await changing
    expect(session.state()).toEqual(expect.objectContaining({ status: 'unlocked', pendingSync: true }))
    vaultApi.offline = false
    await session.retryPendingSync()

    expect(await openedIds(vaultApi.payload, authApi.kdf, NEW_PASSWORD)).toEqual(['during'])
    expect(session.state()).toEqual(expect.objectContaining({ pendingSync: false }))
  })

  it('요청을 기다리는 사이 잠갔으면 잠긴 채로 새 비밀번호를 받아들인다', async () => {
    const { session, authApi } = await signedIn()
    const release = gate(authApi)
    const changing = session.changeMasterPassword(PASSWORD, NEW_PASSWORD)
    await vi.waitFor(() => expect(authApi.changes).toHaveLength(1))
    session.lock()

    release()
    await changing.catch(() => undefined)

    expect(session.state().status).toBe('locked')
    await expect(session.unlock(PASSWORD)).rejects.toBeInstanceOf(WrongMasterPasswordError)
    await session.unlock(NEW_PASSWORD)
  })

  it('다른 기기는 다음 요청에서 세션 만료를 받고, 다시 열 때 옛 비밀번호면 비밀번호가 바뀌었다고 안내받는다', async () => {
    const server = newServer()
    const other = await signedIn(server)
    const mine = await signedIn(server)
    other.server.vaultApi.offline = true
    await saveItem(other.session, 'unsynced')
    other.server.vaultApi.offline = false

    await mine.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

    await other.session.retryPendingSync()
    expect(other.syncErrors.some((error) => error instanceof SessionExpiredError)).toBe(true)
    expect(other.session.state().status).toBe('signedOut')
    const reopened = device(server, other.deviceStore)
    await reopened.session.restore()
    await expect(reopened.session.unlock(PASSWORD)).rejects.toBeInstanceOf(MasterPasswordChangedError)
    expect(await openedIds(server.vaultApi.payload, server.authApi.kdf, NEW_PASSWORD)).toEqual([])
  })

  it('같은 브라우저의 다른 탭은 비밀번호 바뀜 안내로 로그인 화면에 가고 옛 KDF 값으로 기록을 덮어쓰지 않는다', async () => {
    const hub = createInMemoryTabHub()
    const server = newServer()
    const deviceStore = new FakeDeviceStore()
    const a = device(server, deviceStore, hub.connect())
    const b = device(server, deviceStore, hub.connect())
    await a.session.login(EMAIL, PASSWORD)
    await b.session.restore()
    await b.session.unlock(PASSWORD)

    await a.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

    await vi.waitFor(() => expect(b.session.state().status).toBe('signedOut'))
    expect(b.syncErrors.some((error) => error instanceof MasterPasswordChangedError)).toBe(true)
    expect(deviceStore.record?.kdf.salt).toBe(server.authApi.kdf.salt)
    await saveItem(a.session, 'a')
    expect(deviceStore.record?.kdf.salt).toBe(server.authApi.kdf.salt)
    expect(await openedIds(deviceStore.record?.payload, server.authApi.kdf, NEW_PASSWORD)).toEqual(['a'])
  })
})
