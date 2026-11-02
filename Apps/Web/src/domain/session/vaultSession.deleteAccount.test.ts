// @vitest-environment node
// 계정 삭제: 비밀번호로 만든 인증 해시를 보내 서버에서 지우고, 이 기기의 기록과 탭 기억을 지워 모든 탭을 로그아웃한다 (SEC-005, ADR-0024)
import { describe, expect, it, vi } from 'vitest'
import { FakeAuthApi, FakeDeviceStore, FakeVaultApi, NOW, PASSWORD, fields } from '../../test/sessionFixtures'
import { MasterPasswordChangedError, createAuthService } from '../auth/authService'
import { fromBase64, toBase64 } from '../crypto/base64'
import { deriveKeys } from '../crypto/keys'
import { keysOf, vectors } from '../crypto/testVectors'
import { sealVault } from '../crypto/vaultCipher'
import type { RememberedKdf, TabMemory } from '../device/tabMemory'
import { NetworkError, ServerError } from '../server/serverErrors'
import { createInMemoryTabHub, createSingleTabFallback } from '../tabs/inMemoryTabs'
import type { Tabs } from '../tabs/tabs'
import type { VaultApi } from '../vault/vaultApi'
import { addItem, type VaultData } from '../vault/vaultData'
import { createRevisionMemory, createVaultSync } from '../vault/vaultSync'
import {
  AccountDeletedDeviceNotClearedError,
  AccountDeletionOfflineError,
  SessionExpiredError,
  SessionStateError,
  WrongMasterPasswordError,
  createVaultSession,
  type VaultSession,
} from './vaultSession'

const EMAIL = 'user@example.com'

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

/** 실제 서버처럼 폐기된 토큰의 볼트 요청을 401로 거절한다 */
function tokenChecked(server: Server, token: string): VaultApi {
  const check = () => {
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

class FakeTabMemory implements TabMemory {
  value: RememberedKdf | null = null
  get() {
    return this.value
  }
  set(value: RememberedKdf) {
    this.value = value
  }
  clear() {
    this.value = null
  }
}

/** 한 기기(또는 탭). 같은 서버·기기 저장소·탭 허브를 넘기면 같은 브라우저의 다른 탭이 된다 */
function device(server: Server = newServer(), deviceStore = new FakeDeviceStore(), tabs: Tabs = createSingleTabFallback(), tabMemory = new FakeTabMemory()) {
  const syncErrors: unknown[] = []
  const session = createVaultSession({
    auth: createAuthService(server.authApi),
    vaultSyncFor: (token, encryptionKey, revisionMemory) => createVaultSync({ api: tokenChecked(server, token), encryptionKey, now: () => NOW, revisionMemory }),
    revisionMemory: createRevisionMemory(),
    deviceStore,
    tabs,
    tabMemory,
  })
  session.onSyncChange((error) => syncErrors.push(error))
  return { session, ...server, server, deviceStore, tabMemory, syncErrors }
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

function saveItem(session: VaultSession, id: string) {
  return session.save(addItem(unlockedData(session), { ...fields, serviceName: id }, NOW, id))
}

async function expectedAuthHash(): Promise<string> {
  const { authHash } = await deriveKeys(PASSWORD, fromBase64(vectors.keyDerivationCases[0].salt), vectors.kdf.iterations)
  return toBase64(authHash)
}

function gate(target: { deleteGate: Promise<void> | null }) {
  let release!: () => void
  target.deleteGate = new Promise<void>((resolve) => (release = resolve))
  return () => {
    target.deleteGate = null
    release()
  }
}

describe('계정 삭제 전 검사 (SEC-005 규칙 1)', () => {
  it('세션 없이 연 볼트(오프라인)에서는 서버에 보내지 않고 AccountDeletionOfflineError', async () => {
    const first = await signedIn()
    first.authApi.offline = true
    const reopened = device(first.server, first.deviceStore)
    await reopened.session.restore()
    await reopened.session.unlock(PASSWORD)
    first.authApi.offline = false

    await expect(reopened.session.deleteAccount(PASSWORD)).rejects.toBeInstanceOf(AccountDeletionOfflineError)
    expect(first.authApi.deletions).toHaveLength(0)
    expect(reopened.session.state().status).toBe('unlocked')
  })

  it('잠긴 상태에서는 거절한다', async () => {
    const { session, authApi } = await signedIn()
    session.lock()

    await expect(session.deleteAccount(PASSWORD)).rejects.toBeInstanceOf(SessionStateError)
    expect(authApi.deletions).toHaveLength(0)
  })

  it('비밀번호가 틀리면 서버에 보내지 않고 WrongMasterPasswordError이고 볼트는 그대로 열려 있다', async () => {
    const { session, authApi, deviceStore } = await signedIn()

    await expect(session.deleteAccount('not the master password')).rejects.toBeInstanceOf(WrongMasterPasswordError)
    expect(authApi.deletions).toHaveLength(0)
    expect(session.state().status).toBe('unlocked')
    expect(deviceStore.record?.email).toBe(EMAIL)
  })

  it('읽기 전용 볼트에서도 지울 수 있다', async () => {
    const server = newServer()
    const { encryptionKey } = await keysOf(vectors.keyDerivationCases[0].name)
    server.vaultApi.payload = await sealVault(encryptionKey, JSON.stringify({ version: 2, updatedAt: '2026-10-30T00:00:00Z', items: [] }))
    server.vaultApi.revision = 1
    const { session, authApi } = await signedIn(server)

    await session.deleteAccount(PASSWORD)

    expect(authApi.deleted).toBe(true)
    expect(session.state().status).toBe('signedOut')
  })
})

describe('계정 삭제 요청 (SEC-005 규칙 3, api-spec 2.5.6)', () => {
  it('지금 토큰과 이 계정의 KDF 값으로 만든 인증 해시를 보낸다', async () => {
    const { session, authApi } = await signedIn()

    await session.deleteAccount(PASSWORD)

    expect(authApi.deletions).toEqual([{ accessToken: 'token-1', request: { authHash: await expectedAuthHash() } }])
  })

  it('서버가 인증 해시를 거절하면 WrongMasterPasswordError이고 로그인 상태와 기기 기록은 그대로다', async () => {
    const { session, authApi, deviceStore } = await signedIn()
    authApi.deleteError = new ServerError(401, 'AUTH_FAILED')

    await expect(session.deleteAccount(PASSWORD)).rejects.toBeInstanceOf(WrongMasterPasswordError)
    expect(session.state().status).toBe('unlocked')
    expect(deviceStore.record?.email).toBe(EMAIL)
  })

  it('시도 제한(429)은 ServerError TOO_MANY_ATTEMPTS로 그대로 알리고 상태는 그대로다', async () => {
    const { session, authApi } = await signedIn()
    authApi.deleteError = new ServerError(429, 'TOO_MANY_ATTEMPTS', 900)

    await expect(session.deleteAccount(PASSWORD)).rejects.toEqual(expect.objectContaining({ code: 'TOO_MANY_ATTEMPTS' }))
    expect(session.state().status).toBe('unlocked')
  })

  it('응답을 받지 못하면 결과를 알 수 없으니 오류를 그대로 알리고 상태는 그대로 둔다 (예외 2)', async () => {
    const { session, authApi, deviceStore } = await signedIn()
    authApi.deleteError = new NetworkError()

    await expect(session.deleteAccount(PASSWORD)).rejects.toBeInstanceOf(NetworkError)
    expect(session.state().status).toBe('unlocked')
    expect(deviceStore.record?.email).toBe(EMAIL)
  })

  it('토큰이 무효(401 UNAUTHORIZED)면 세션 만료로 로그아웃한다', async () => {
    const { session, authApi, syncErrors } = await signedIn()
    authApi.deleteError = new ServerError(401, 'UNAUTHORIZED')

    await expect(session.deleteAccount(PASSWORD)).rejects.toBeInstanceOf(SessionExpiredError)
    expect(session.state().status).toBe('signedOut')
    expect(syncErrors.some((error) => error instanceof SessionExpiredError)).toBe(true)
  })

  it('앞서 보낸 저장이 끝난 뒤에 요청을 보낸다', async () => {
    const { session, authApi, vaultApi } = await signedIn()
    let release!: () => void
    vaultApi.gate = new Promise<void>((resolve) => (release = resolve))
    const saving = saveItem(session, 'a')
    await vi.waitFor(() => expect(session.state()).toEqual(expect.objectContaining({ pendingSync: true })))

    const deleting = session.deleteAccount(PASSWORD)
    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(authApi.deletions).toHaveLength(0)
    vaultApi.gate = null
    release()

    await saving
    await deleting
    expect(authApi.deletions).toHaveLength(1)
    expect(session.state().status).toBe('signedOut')
  }, 20_000)
})

describe('계정 삭제 뒤 이 기기 정리 (SEC-005 규칙 5)', () => {
  it('이 기기의 기록과 탭 기억을 지우고 로그아웃 상태가 된다', async () => {
    const { session, deviceStore, tabMemory } = await signedIn()
    expect(deviceStore.record?.email).toBe(EMAIL)
    expect(tabMemory.value?.email).toBe(EMAIL)

    await session.deleteAccount(PASSWORD)

    expect(session.state().status).toBe('signedOut')
    expect(deviceStore.stored).toBeUndefined()
    expect(tabMemory.value).toBeNull()
  })

  it('지운 계정의 토큰을 다시 쓰지 않고 새로 열어도 잠금 화면이 아니라 로그인 전 상태다', async () => {
    const first = await signedIn()

    await first.session.deleteAccount(PASSWORD)

    const reopened = device(first.server, first.deviceStore)
    await reopened.session.restore()
    expect(reopened.session.state().status).toBe('signedOut')
  })

  it('같은 브라우저의 다른 탭도 로그아웃한다', async () => {
    const hub = createInMemoryTabHub()
    const server = newServer()
    const deviceStore = new FakeDeviceStore()
    const a = device(server, deviceStore, hub.connect())
    const b = device(server, deviceStore, hub.connect())
    await a.session.login(EMAIL, PASSWORD)
    await b.session.restore()
    await b.session.unlock(PASSWORD)

    await a.session.deleteAccount(PASSWORD)

    await vi.waitFor(() => expect(b.session.state().status).toBe('signedOut'))
    expect(b.tabMemory.value).toBeNull()
    expect(deviceStore.stored).toBeUndefined()
  }, 20_000)

  it('기기 기록을 지우지 못하면 계정은 지워졌고 로그아웃한 채 AccountDeletedDeviceNotClearedError로 알린다', async () => {
    const { session, authApi, deviceStore } = await signedIn()
    const deleting = session.deleteAccount(PASSWORD)
    deviceStore.failing = true

    await expect(deleting).rejects.toBeInstanceOf(AccountDeletedDeviceNotClearedError)
    expect(authApi.deleted).toBe(true)
    expect(session.state().status).toBe('signedOut')
  })

  it('요청을 기다리는 사이 잠갔어도 삭제가 끝나면 기록을 지우고 로그아웃한다', async () => {
    const { session, authApi, deviceStore } = await signedIn()
    const release = gate(authApi)
    const deleting = session.deleteAccount(PASSWORD)
    await vi.waitFor(() => expect(authApi.deletions).toHaveLength(1))

    session.lock()
    release()

    await deleting
    expect(session.state().status).toBe('signedOut')
    expect(deviceStore.stored).toBeUndefined()
  }, 20_000)

  it('같은 이메일로 다시 가입하면 빈 볼트로 시작한다 (SEC-005 규칙 7)', async () => {
    const { session } = await signedIn()
    await saveItem(session, 'a')

    await session.deleteAccount(PASSWORD)
    await session.register(EMAIL, PASSWORD)

    expect(unlockedData(session).items).toEqual([])
  })
})

describe('다른 기기에서 삭제된 뒤 (SEC-005 규칙 6, 예외 1)', () => {
  it('다른 기기는 다음 요청에서 세션 만료를 받고 로그인 화면으로 간다', async () => {
    const server = newServer()
    const other = await signedIn(server)
    const mine = await signedIn(server)

    await mine.session.deleteAccount(PASSWORD)

    await expect(saveItem(other.session, 'late')).rejects.toBeInstanceOf(SessionExpiredError)
    expect(other.session.state().status).toBe('signedOut')
    expect(server.vaultApi.payload).toBeNull()
  }, 20_000)

  it('다른 기기가 남은 기기 기록으로 다시 열면 비밀번호가 바뀐 경우와 같은 안내를 받는다', async () => {
    const server = newServer()
    const other = await signedIn(server)
    const mine = await signedIn(server)

    await mine.session.deleteAccount(PASSWORD)

    const reopened = device(server, other.deviceStore)
    await reopened.session.restore()
    await expect(reopened.session.unlock(PASSWORD)).rejects.toBeInstanceOf(MasterPasswordChangedError)
    expect(reopened.session.state().status).toBe('signedOut')
  }, 20_000)
})

describe('계정 삭제와 겹친 다른 탭 로그아웃 (SEC-005 규칙 5)', () => {
  it('서버가 지운 뒤 다른 탭 로그아웃으로 이미 로그아웃됐어도 삭제는 완료로 끝난다', async () => {
    const hub = createInMemoryTabHub()
    const server = newServer()
    const deviceStore = new FakeDeviceStore()
    const a = device(server, deviceStore, hub.connect())
    const b = device(server, deviceStore, hub.connect())
    await a.session.login(EMAIL, PASSWORD)
    await b.session.restore()
    await b.session.unlock(PASSWORD)
    const release = gate(server.authApi)
    const deleting = a.session.deleteAccount(PASSWORD)
    await vi.waitFor(() => expect(server.authApi.deletions).toHaveLength(1))

    await b.session.logout()
    await vi.waitFor(() => expect(a.session.state().status).toBe('signedOut'))
    release()

    await expect(deleting).resolves.toBeUndefined()
    expect(server.authApi.deleted).toBe(true)
    expect(a.session.state().status).toBe('signedOut')
    expect(deviceStore.stored).toBeUndefined()
  }, 20_000)
})
