import { describe, expect, it, vi } from 'vitest'
import { parseBackup, type VaultBackup } from '../../domain/backup/backupFile'
import { createSingleTabFallback } from '../../domain/tabs/inMemoryTabs'
import { NetworkError, ServerError } from '../../domain/server/serverErrors'
import { WrongMasterPasswordError, type SessionState } from '../../domain/session/sessionTypes'
import { InvalidVaultDataError, type VaultData } from '../../domain/vault/vaultData'
import { RevisionRollbackError } from '../../domain/vault/vaultSync'
import { FakeKotlinThrowable, SIGNED_OUT_STATE, createFakeKotlin } from '../../test/fakeKotlinModule'
import { InvalidBaseUrlError } from '../http/baseUrl'
import { createKotlinSession } from './kotlinSession'
import type { JsSessionState } from './kotlinTypes'

const VAULT: VaultData = {
  version: 1,
  updatedAt: '2026-11-13T00:00:00Z',
  items: [{ id: 'a', serviceName: 'GitHub', username: 'me', password: 'pw', createdAt: '2026-11-13T00:00:00Z', updatedAt: '2026-11-13T00:00:00Z' }],
}
const PAYLOAD = { version: 1, cipher: 'AES-256-GCM', iv: 'aXY=', ciphertext: 'Y2lwaGVy', tag: 'dGFn' }
const LOCKED_STATE: JsSessionState = { ...SIGNED_OUT_STATE, status: 'locked', email: 'user@example.com', pendingSync: true }
const UNLOCKED_STATE: JsSessionState = {
  ...SIGNED_OUT_STATE,
  status: 'unlocked',
  email: 'user@example.com',
  signedIn: true,
  revision: 3,
  vaultJson: JSON.stringify(VAULT),
  payloadJson: JSON.stringify(PAYLOAD),
}

const KDF = { salt: 'AAAAAAAAAAAAAAAAAAAAAA==', iterations: 600_000 }

function setup(initial: JsSessionState = SIGNED_OUT_STATE, apiBaseUrl = 'https://api.example.com') {
  const fake = createFakeKotlin(initial)
  const deps = { apiBaseUrl, deviceStore: { load: vi.fn(), save: vi.fn(), clear: vi.fn() }, tabs: createSingleTabFallback(), tabMemory: { get: vi.fn(() => null), set: vi.fn(), clear: vi.fn() } }
  const session = createKotlinSession(fake.module, deps)
  return { ...fake, deps, kotlin: fake.session, web: session }
}

describe('createKotlinSession 조립', () => {
  it('서버 주소와 세 포트(JSON 변환 어댑터)를 Kotlin에 넘긴다', () => {
    const { module } = setup()

    expect(module.createKotlinSession).toHaveBeenCalledOnce()
    const [apiBaseUrl, deviceStore, tabs, tabMemory] = module.createKotlinSession.mock.calls[0] ?? []
    expect(apiBaseUrl).toBe('https://api.example.com')
    expect(deviceStore).toMatchObject({ load: expect.any(Function), save: expect.any(Function), clear: expect.any(Function) })
    expect(tabs).toMatchObject({ lockRun: expect.any(Function), post: expect.any(Function), subscribe: expect.any(Function) })
    expect(tabMemory).toMatchObject({ get: expect.any(Function), set: expect.any(Function), clear: expect.any(Function) })
  })

  it('서버 주소가 없으면(undefined, null) null로 넘긴다', () => {
    const fake = createFakeKotlin()
    const ports = { deviceStore: { load: vi.fn(), save: vi.fn(), clear: vi.fn() }, tabs: createSingleTabFallback(), tabMemory: { get: vi.fn(() => null), set: vi.fn(), clear: vi.fn() } }

    createKotlinSession(fake.module, { ...ports, apiBaseUrl: undefined })
    createKotlinSession(fake.module, { ...ports, apiBaseUrl: null })

    expect(fake.module.createKotlinSession.mock.calls.map((call) => call[0])).toEqual([null, null])
  })

  it('서버 주소가 규칙에 맞지 않아 Kotlin이 던지면 웹 InvalidBaseUrlError로 바꿔 던진다', () => {
    const fake = createFakeKotlin()
    fake.module.createKotlinSession.mockImplementation(() => {
      throw new FakeKotlinThrowable('InvalidBaseUrlException')
    })

    expect(() => createKotlinSession(fake.module, { apiBaseUrl: 'http://evil.example', deviceStore: { load: vi.fn(), save: vi.fn(), clear: vi.fn() }, tabs: createSingleTabFallback(), tabMemory: { get: vi.fn(() => null), set: vi.fn(), clear: vi.fn() } })).toThrow(InvalidBaseUrlError)
  })
})

describe('상태 변환', () => {
  it('로그인 전 상태', () => {
    expect(setup().web.state()).toEqual({ status: 'signedOut' })
  })

  it('잠금 상태는 이메일과 동기화 대기 여부만 담는다', () => {
    expect(setup(LOCKED_STATE).web.state()).toEqual({ status: 'locked', email: 'user@example.com', pendingSync: true })
  })

  it('잠금 해제 상태는 볼트 JSON을 웹 볼트로 읽고 revision, readOnly, payload를 붙인다', () => {
    const state = setup({ ...UNLOCKED_STATE, readOnly: true, pendingSync: true, storedOnDevice: false }).web.state()

    expect(state).toEqual({
      status: 'unlocked',
      email: 'user@example.com',
      vault: { revision: 3, data: VAULT, readOnly: true, payload: PAYLOAD },
      pendingSync: true,
      signedIn: true,
      storedOnDevice: false,
    })
  })

  it('서버에 볼트가 없으면 payload는 null이다', () => {
    const state = setup({ ...UNLOCKED_STATE, payloadJson: null }).web.state()

    expect(state).toMatchObject({ status: 'unlocked', vault: { payload: null } })
  })

  it('모르는 필드가 있는 볼트도 그대로 읽는다', () => {
    const vault = { ...VAULT, future: { x: 1 } }

    const state = setup({ ...UNLOCKED_STATE, vaultJson: JSON.stringify(vault) }).web.state()

    expect(state).toMatchObject({ vault: { data: vault } })
  })

  it('같은 내용이면 같은 상태 객체를 돌려준다 (Kotlin이 새 객체를 줘도)', () => {
    const { web, kotlin } = setup(UNLOCKED_STATE)
    const first = web.state()
    kotlin.state.mockReturnValue({ ...UNLOCKED_STATE })

    const second = web.state()

    expect(second).toBe(first)
  })

  it('내용이 바뀌면 새 상태 객체를 돌려주고, 안 바뀐 상태는 다시 같은 객체다', () => {
    const { web, kotlin } = setup(UNLOCKED_STATE)
    const first = web.state()
    kotlin.state.mockReturnValue({ ...UNLOCKED_STATE, revision: 4 })

    const changed = web.state()
    const again = web.state()

    expect(changed).not.toBe(first)
    expect(changed).toMatchObject({ vault: { revision: 4 } })
    expect(again).toBe(changed)
  })

  it.each([
    ['pendingSync', { pendingSync: true }],
    ['signedIn', { signedIn: false }],
    ['storedOnDevice', { storedOnDevice: false }],
    ['readOnly', { readOnly: true }],
    ['payloadJson', { payloadJson: null }],
    ['vaultJson', { vaultJson: JSON.stringify({ ...VAULT, updatedAt: '2026-11-14T00:00:00Z' }) }],
    ['email', { email: 'other@example.com' }],
  ] satisfies [string, Partial<JsSessionState>][])('%s가 바뀌어도 새 상태 객체를 만든다', (_name, change) => {
    const { web, kotlin } = setup(UNLOCKED_STATE)
    const first = web.state()
    kotlin.state.mockReturnValue({ ...UNLOCKED_STATE, ...change })

    expect(web.state()).not.toBe(first)
  })

  it('잠금과 잠금 해제를 오가면 상태 종류가 따라 바뀐다', () => {
    const { web, kotlin } = setup(UNLOCKED_STATE)
    const states: SessionState['status'][] = []

    for (const next of [LOCKED_STATE, UNLOCKED_STATE, SIGNED_OUT_STATE]) {
      kotlin.state.mockReturnValue(next)
      states.push(web.state().status)
    }

    expect(states).toEqual(['locked', 'unlocked', 'signedOut'])
  })

  it('Kotlin 상태가 약속과 다르면(모르는 status, 이메일·볼트 없음) 던진다', () => {
    expect(() => setup({ ...SIGNED_OUT_STATE, status: 'mystery' }).web.state()).toThrow(TypeError)
    expect(() => setup({ ...LOCKED_STATE, email: null }).web.state()).toThrow(TypeError)
    expect(() => setup({ ...UNLOCKED_STATE, vaultJson: null }).web.state()).toThrow(TypeError)
  })

  it('볼트 JSON이 규격에 맞지 않으면 InvalidVaultDataError다', () => {
    expect(() => setup({ ...UNLOCKED_STATE, vaultJson: '{"version":0}' }).web.state()).toThrow(InvalidVaultDataError)
  })
})

describe('메서드 위임', () => {
  it('register와 login은 인자를 넘기고, login은 acceptRollback을 기본 false로 넘긴다', async () => {
    const { web, kotlin } = setup()

    await web.register('a@b.c', 'pw')
    await web.login('a@b.c', 'pw')
    await web.login('a@b.c', 'pw', { acceptRollback: true })

    expect(kotlin.register).toHaveBeenCalledWith('a@b.c', 'pw')
    expect(kotlin.login).toHaveBeenNthCalledWith(1, 'a@b.c', 'pw', false)
    expect(kotlin.login).toHaveBeenNthCalledWith(2, 'a@b.c', 'pw', true)
  })

  it('lock은 범위를 넘기고 기본은 allTabs다', () => {
    const { web, kotlin } = setup()

    web.lock()
    web.lock('thisTab')

    expect(kotlin.lock).toHaveBeenNthCalledWith(1, 'allTabs')
    expect(kotlin.lock).toHaveBeenNthCalledWith(2, 'thisTab')
  })

  it('unlock은 offline 여부를 객체로 돌려주고, acceptRollback을 넘긴다', async () => {
    const { web, kotlin } = setup()
    kotlin.unlock.mockResolvedValueOnce(true)

    const offline = await web.unlock('pw')
    const online = await web.unlock('pw', { acceptRollback: true })

    expect(offline).toEqual({ offline: true })
    expect(online).toEqual({ offline: false })
    expect(kotlin.unlock).toHaveBeenNthCalledWith(1, 'pw', false)
    expect(kotlin.unlock).toHaveBeenNthCalledWith(2, 'pw', true)
  })

  it('reconnect는 비밀번호와 acceptRollback을 넘긴다', async () => {
    const { web, kotlin } = setup()

    await web.reconnect('pw', { acceptRollback: true })

    expect(kotlin.reconnect).toHaveBeenCalledWith('pw', true)
  })

  it('save는 볼트를 JSON 문자열로 넘긴다 (모르는 필드 포함)', async () => {
    const { web, kotlin } = setup()
    const data = { ...VAULT, future: [1, 2] }

    await web.save(data)

    expect(JSON.parse(kotlin.save.mock.calls[0]?.[0] ?? 'null')).toEqual(data)
  })

  it('exportBackup은 Kotlin이 만든 JSON 텍스트와 현지 날짜 파일 이름을 준다', async () => {
    const { web, kotlin } = setup()
    kotlin.exportBackup.mockResolvedValueOnce('{"format":"keymanage-backup"}')
    const now = new Date(2026, 10, 13, 9, 30)

    const file = await web.exportBackup(now)

    expect(file).toEqual({ name: 'keymanage-backup-20261113.json', text: '{"format":"keymanage-backup"}' })
    expect(kotlin.exportBackup).toHaveBeenCalledWith(now.getTime())
  })

  it('restoreBackup은 백업을 웹이 읽는 파일 형식 JSON으로 되돌려 넘기고 항목 수를 돌려준다', async () => {
    const { web, kotlin } = setup()
    kotlin.restoreBackup.mockResolvedValueOnce(5)
    const backup: VaultBackup = { formatVersion: 1, createdAt: '2026-11-13T00:00:00Z', kdf: KDF, vault: PAYLOAD as VaultBackup['vault'] }
    const now = new Date('2026-11-14T00:00:00Z')

    const restored = await web.restoreBackup(backup, 'pw', now)

    expect(restored).toBe(5)
    const [json, password, nowMs] = kotlin.restoreBackup.mock.calls[0] ?? []
    expect(password).toBe('pw')
    expect(nowMs).toBe(now.getTime())
    expect(parseBackup(json ?? '')).toEqual(backup)
    expect(JSON.parse(json ?? '{}')).toMatchObject({ format: 'keymanage-backup', kdf: { name: 'PBKDF2-HMAC-SHA256', ...KDF } })
  })

  it('changeMasterPassword, deleteAccount, logout, restore, retryPendingSync, pendingOnDevice를 위임한다', async () => {
    const { web, kotlin } = setup()
    kotlin.pendingOnDevice.mockResolvedValueOnce(true)

    await web.changeMasterPassword('old', 'new')
    await web.deleteAccount('pw')
    await web.logout()
    await web.restore()
    await web.retryPendingSync()

    expect(kotlin.changeMasterPassword).toHaveBeenCalledWith('old', 'new')
    expect(kotlin.deleteAccount).toHaveBeenCalledWith('pw')
    expect(kotlin.logout).toHaveBeenCalledOnce()
    expect(kotlin.restore).toHaveBeenCalledOnce()
    expect(kotlin.retryPendingSync).toHaveBeenCalledOnce()
    expect(await web.pendingOnDevice()).toBe(true)
  })

  it('hasUnsavedChanges는 Kotlin 값을 그대로 준다', () => {
    const { web, kotlin } = setup()
    kotlin.hasUnsavedChanges.mockReturnValue(true)

    expect(web.hasUnsavedChanges()).toBe(true)
  })
})

describe('오류 변환', () => {
  it('reject된 Kotlin 예외를 웹 오류로 바꿔 던진다', async () => {
    const { web, kotlin } = setup()
    kotlin.unlock.mockRejectedValueOnce(new FakeKotlinThrowable('WrongMasterPasswordException'))

    await expect(web.unlock('wrong')).rejects.toBeInstanceOf(WrongMasterPasswordError)
  })

  it('서버 오류의 status, code, retryAfterSeconds를 보존한다', async () => {
    const { web, kotlin } = setup()
    kotlin.login.mockRejectedValueOnce(new FakeKotlinThrowable('ServerError', { status: 429, code: 'TOO_MANY_ATTEMPTS', retryAfterSeconds: 12 }))

    const error = await web.login('a@b.c', 'pw').catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ServerError)
    expect(error).toMatchObject({ status: 429, code: 'TOO_MANY_ATTEMPTS', retryAfterSeconds: 12 })
  })

  it('되돌림 오류의 revision 값을 보존한다', async () => {
    const { web, kotlin } = setup()
    kotlin.unlock.mockRejectedValueOnce(new FakeKotlinThrowable('RevisionRollbackException', { detailJson: '{"lastSeenRevision":9,"serverRevision":2}' }))

    const error = await web.unlock('pw').catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(RevisionRollbackError)
    expect(error).toMatchObject({ lastSeenRevision: 9, serverRevision: 2 })
  })

  it('동기로 던지는 Kotlin 예외도 바꾼다', () => {
    const { web, kotlin } = setup()
    kotlin.lock.mockImplementationOnce(() => {
      throw new FakeKotlinThrowable('NetworkError')
    })

    expect(() => web.lock()).toThrow(NetworkError)
  })

  it('모든 비동기 메서드가 reject를 바꾼다', async () => {
    const { web, kotlin } = setup()
    const failure = () => new FakeKotlinThrowable('SessionExpiredException')
    for (const method of ['register', 'login', 'unlock', 'reconnect', 'save', 'exportBackup', 'restoreBackup', 'changeMasterPassword', 'deleteAccount', 'restore', 'retryPendingSync', 'logout', 'pendingOnDevice'] as const) {
      kotlin[method].mockRejectedValueOnce(failure())
    }
    const backup: VaultBackup = { formatVersion: 1, createdAt: '2026-11-13T00:00:00Z', kdf: KDF, vault: PAYLOAD as VaultBackup['vault'] }
    const calls = [
      web.register('a@b.c', 'pw'),
      web.login('a@b.c', 'pw'),
      web.unlock('pw'),
      web.reconnect('pw'),
      web.save(VAULT),
      web.exportBackup(new Date()),
      web.restoreBackup(backup, 'pw', new Date()),
      web.changeMasterPassword('a', 'b'),
      web.deleteAccount('pw'),
      web.restore(),
      web.retryPendingSync(),
      web.logout(),
      web.pendingOnDevice(),
    ]

    const results = await Promise.allSettled(calls)

    for (const result of results) {
      expect(result).toMatchObject({ status: 'rejected', reason: expect.objectContaining({ name: 'SessionExpiredError' }) })
    }
  })

  it('Kotlin 예외가 아닌 reject(포트 쪽 브라우저 오류)는 원래 값을, 모르는 kind는 cause가 원본인 Error를 던진다', async () => {
    const { web, kotlin } = setup()
    const browserFailure = new Error('브라우저 오류')
    const unknown = new FakeKotlinThrowable('SomethingNewException')
    kotlin.restore.mockRejectedValueOnce(browserFailure)
    kotlin.logout.mockRejectedValueOnce(unknown)

    await expect(web.restore()).rejects.toBe(browserFailure)
    await expect(web.logout()).rejects.toMatchObject({ message: 'SomethingNewException', cause: unknown })
  })
})

describe('Kotlin Unit 결과', () => {
  // Kotlin Promise<Unit>은 undefined가 아니라 Kotlin Unit 객체로 resolve된다
  const KOTLIN_UNIT = Object.freeze({ toString: () => 'kotlin.Unit' })

  it('값이 없는 메서드는 Unit 객체로 resolve돼도 undefined로 resolve한다', async () => {
    const { web, kotlin } = setup()
    const unitMethods = [kotlin.register, kotlin.login, kotlin.reconnect, kotlin.save, kotlin.changeMasterPassword, kotlin.deleteAccount, kotlin.restore, kotlin.retryPendingSync, kotlin.logout]
    for (const method of unitMethods) (method as unknown as { mockResolvedValueOnce(value: unknown): void }).mockResolvedValueOnce(KOTLIN_UNIT)

    const results = await Promise.all([
      web.register('a@b.c', 'pw'),
      web.login('a@b.c', 'pw'),
      web.reconnect('pw'),
      web.save(VAULT),
      web.changeMasterPassword('old', 'new'),
      web.deleteAccount('pw'),
      web.restore(),
      web.retryPendingSync(),
      web.logout(),
    ])

    expect(results).toEqual(Array(9).fill(undefined))
    for (const result of results) expect(result).toBeUndefined()
  })
})

describe('리스너', () => {
  it('onLock, onUnlock, onRemoteChange는 등록한 함수를 Kotlin 이벤트마다 부른다', () => {
    const { web, listeners } = setup()
    const onLock = vi.fn()
    const onUnlock = vi.fn()
    const onRemote = vi.fn()
    web.onLock(onLock)
    web.onUnlock(onUnlock)
    web.onRemoteChange(onRemote)

    listeners.lock.forEach((listener) => listener())
    listeners.unlock.forEach((listener) => listener())
    listeners.remote.forEach((listener) => listener())

    expect(onLock).toHaveBeenCalledOnce()
    expect(onUnlock).toHaveBeenCalledOnce()
    expect(onRemote).toHaveBeenCalledOnce()
  })

  it('해제하면 Kotlin 등록을 풀고, 두 번 해제해도 한 번만 푼다', () => {
    const { web, listeners, unsubscribes } = setup()
    const offs = [web.onLock(vi.fn()), web.onUnlock(vi.fn()), web.onRemoteChange(vi.fn()), web.onSyncChange(vi.fn())]

    for (const off of offs) {
      off()
      off()
    }

    expect(unsubscribes).toHaveLength(4)
    for (const unsubscribe of unsubscribes) expect(unsubscribe).toHaveBeenCalledOnce()
    expect(listeners.lock.size + listeners.unlock.size + listeners.remote.size + listeners.sync.size).toBe(0)
  })

  it('onSyncChange는 null(모두 올림)을 그대로, 오류는 웹 오류로 바꿔 부른다', () => {
    const { web, listeners } = setup()
    const onSync = vi.fn()
    web.onSyncChange(onSync)

    listeners.sync.forEach((listener) => listener(null))
    listeners.sync.forEach((listener) => listener(new FakeKotlinThrowable('ServerError', { status: 403, code: 'ORIGIN_NOT_ALLOWED' }).info))
    listeners.sync.forEach((listener) => listener(new FakeKotlinThrowable('NetworkError')))

    expect(onSync).toHaveBeenNthCalledWith(1, null)
    expect(onSync.mock.calls[1]?.[0]).toBeInstanceOf(ServerError)
    expect(onSync.mock.calls[1]?.[0]).toMatchObject({ status: 403, code: 'ORIGIN_NOT_ALLOWED' })
    expect(onSync.mock.calls[2]?.[0]).toBeInstanceOf(NetworkError)
  })
})
