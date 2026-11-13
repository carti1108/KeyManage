// 실제 Kotlin 빌드 결과(keymanage-web-session)에 대고 어댑터를 확인한다 (domain-migration-plan 10.2).
// 서버는 가짜 fetch, 기기 저장소·탭·탭 기억은 메모리다. Kotlin 빌드가 없으면 npm run kotlin을 먼저 돌린다
import * as kotlin from 'keymanage-web-session'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DeviceRecord, DeviceStore } from '../../domain/device/deviceRecord'
import { fromBase64 } from '../../domain/crypto/base64'
import { createInMemoryTabHub } from '../../domain/tabs/inMemoryTabs'
import { ServerError } from '../../domain/server/serverErrors'
import { WrongMasterPasswordError } from '../../domain/session/sessionTypes'
import { addItem } from '../../domain/vault/vaultData'
import { createKotlinSession } from './kotlinSession'

const EMAIL = 'user@example.com'
const PASSWORD = 'correct horse battery staple'
const BASE = 'https://vault.example.com'

interface Account {
  authHash: string
  salt: string
  iterations: number
}

/** api-spec 1장의 응답 모양만 흉내 낸 메모리 서버 */
function fakeServer() {
  const accounts = new Map<string, Account>()
  const tokens = new Set<string>()
  let vault: { revision: number; payload: unknown } | null = null
  let issued = 0
  let lastChange: Record<string, unknown> | null = null
  let failNext: { status: number; code: string; retryAfter?: string } | null = null
  const json = (status: number, body: unknown, headers: Record<string, string> = {}) =>
    new Response(body === null ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } })
  const error = (status: number, code: string, headers: Record<string, string> = {}) =>
    json(status, { code, message: 'm', timestamp: '2026-11-13T00:00:00Z' }, headers)

  const fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const request = input instanceof Request ? input : new Request(input, init)
    const path = new URL(request.url).pathname
    const text = await request.text()
    const body = text === '' ? {} : (JSON.parse(text) as Record<string, unknown>)
    const token = request.headers.get('Authorization')?.replace('Bearer ', '') ?? ''
    if (failNext) {
      const { status, code, retryAfter } = failNext
      failNext = null
      return error(status, code, retryAfter ? { 'Retry-After': retryAfter } : {})
    }
    const session = () => {
      issued += 1
      const accessToken = `token-${issued}`
      tokens.add(accessToken)
      return { accessToken, expiresAt: '2026-12-01T00:00:00Z' }
    }
    switch (path) {
      case '/api/v1/auth/prelogin': {
        const account = accounts.get(String(body.email))
        return json(200, { kdf: 'PBKDF2-HMAC-SHA256', iterations: account?.iterations ?? 600_000, salt: account?.salt ?? 'AAAAAAAAAAAAAAAAAAAAAA==' })
      }
      case '/api/v1/auth/register':
        accounts.set(String(body.email), { authHash: String(body.authHash), salt: String(body.kdfSalt), iterations: Number(body.kdfIterations) })
        return json(201, session())
      case '/api/v1/auth/login': {
        const account = accounts.get(String(body.email))
        if (!account || account.authHash !== body.authHash) return error(401, 'AUTH_FAILED')
        return json(200, session())
      }
      case '/api/v1/auth/change-password': {
        if (!tokens.has(token)) return error(401, 'UNAUTHORIZED')
        const [email, account] = [...accounts].find(([, candidate]) => candidate.authHash === body.currentAuthHash) ?? []
        if (email === undefined || account === undefined) return error(401, 'AUTH_FAILED')
        const kdf = body.kdf as { iterations: number; salt: string }
        const sent = body.vault as { expectedRevision: number; payload: unknown }
        if ((vault?.revision ?? 0) !== sent.expectedRevision) return error(409, 'VAULT_CONFLICT')
        lastChange = body
        accounts.set(email, { authHash: String(body.newAuthHash), salt: kdf.salt, iterations: kdf.iterations })
        vault = { revision: sent.expectedRevision + 1, payload: sent.payload }
        return json(200, { ...session(), revision: vault.revision })
      }
      case '/api/v1/auth/logout':
        tokens.delete(token)
        return new Response(null, { status: 204 })
      case '/api/v1/vault':
        if (!tokens.has(token)) return error(401, 'UNAUTHORIZED')
        if (request.method === 'GET') return vault ? json(200, vault) : error(404, 'VAULT_NOT_FOUND')
        if ((vault?.revision ?? 0) !== body.expectedRevision) return error(409, 'VAULT_CONFLICT')
        vault = { revision: (vault?.revision ?? 0) + 1, payload: body.payload }
        return json(200, { revision: vault.revision })
      default:
        return error(404, 'NOT_FOUND')
    }
  })
  return {
    fetch,
    failNextWith(status: number, code: string, retryAfter?: string) {
      failNext = { status, code, retryAfter }
    },
    vaultRevision: () => vault?.revision ?? 0,
    lastChange: () => lastChange,
  }
}

function memoryDeviceStore(): DeviceStore & { record: () => DeviceRecord | null } {
  let stored: DeviceRecord | null = null
  return {
    load: async () => stored,
    save: async (record) => {
      stored = record
    },
    clear: async () => {
      stored = null
    },
    record: () => stored,
  }
}

function newSession(deviceStore = memoryDeviceStore()) {
  const tabs = createInMemoryTabHub().connect()
  let remembered: ReturnType<Parameters<typeof createKotlinSession>[1]['tabMemory']['get']> = null
  const tabMemory = {
    get: () => remembered,
    set: (value: NonNullable<typeof remembered>) => {
      remembered = value
    },
    clear: () => {
      remembered = null
    },
  }
  return { session: createKotlinSession(kotlin, { apiBaseUrl: BASE, deviceStore, tabs, tabMemory }), deviceStore }
}

const fields = { serviceName: 'GitHub', username: 'octocat', password: 'pw-1' }

describe('Kotlin 세션 계약', { timeout: 120_000 }, () => {
  let server: ReturnType<typeof fakeServer>
  beforeEach(() => {
    server = fakeServer()
    vi.stubGlobal('fetch', server.fetch)
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('가입 → 저장 → 잠금 → 잠금 해제를 Kotlin 세션으로 돈다', async () => {
    const { session, deviceStore } = newSession()
    const locks = vi.fn()
    session.onLock(locks)

    await session.register(EMAIL, PASSWORD)
    const opened = session.state()
    if (opened.status !== 'unlocked') throw new Error(`잠금 해제 상태가 아님: ${opened.status}`)
    expect(session.state()).toBe(opened)
    await session.save(addItem(opened.vault.data, fields, new Date('2026-11-13T00:00:00Z'), 'a'))
    expect(server.vaultRevision()).toBe(2)
    expect(deviceStore.record()?.email).toBe(EMAIL)

    session.lock()
    expect(session.state()).toEqual({ status: 'locked', email: EMAIL, pendingSync: false })
    expect(locks).toHaveBeenCalledTimes(1)
    await expect(session.unlock(PASSWORD)).resolves.toEqual({ offline: false })

    const reopened = session.state()
    if (reopened.status !== 'unlocked') throw new Error('다시 열지 못함')
    expect(reopened.vault.data.items.map((item) => item.id)).toEqual(['a'])
  })

  it('틀린 비밀번호와 서버 오류를 웹 오류 클래스로 바꾼다', async () => {
    const { session } = newSession()
    await session.register(EMAIL, PASSWORD)
    session.lock()

    await expect(session.unlock('wrong password 1234')).rejects.toBeInstanceOf(WrongMasterPasswordError)

    await session.logout()
    server.failNextWith(429, 'TOO_MANY_ATTEMPTS', '42')
    const error = await session.login(EMAIL, PASSWORD).catch((caught: unknown) => caught)
    expect(error).toBeInstanceOf(ServerError)
    expect(error).toMatchObject({ status: 429, code: 'TOO_MANY_ATTEMPTS', retryAfterSeconds: 42 })
  })

  it('기기 기록에서 잠긴 상태로 다시 연다', async () => {
    const first = newSession()
    await first.session.register(EMAIL, PASSWORD)

    const next = newSession(first.deviceStore)
    await next.session.restore()

    expect(next.session.state()).toEqual({ status: 'locked', email: EMAIL, pendingSync: false })
  })

  // SEC-004: 비밀번호를 바꿀 때 새 salt는 16바이트이고 반복 횟수는 지금 값을 유지한다
  it('비밀번호를 바꾸면 새 16바이트 salt와 새 인증 해시로 서버에 보낸다', async () => {
    const { session } = newSession()
    await session.register(EMAIL, PASSWORD)
    const registered = session.state()
    if (registered.status !== 'unlocked') throw new Error('가입 뒤 열려 있지 않음')

    await session.changeMasterPassword(PASSWORD, 'another correct horse battery')

    const request = server.lastChange() as { currentAuthHash: string; newAuthHash: string; kdf: { iterations: number; salt: string } } | null
    if (request === null) throw new Error('비밀번호 변경 요청이 없음')
    expect(fromBase64(request.kdf.salt)).toHaveLength(16)
    expect(request.kdf.iterations).toBe(600_000)
    expect(request.newAuthHash).not.toBe(request.currentAuthHash)
    expect(session.state().status).toBe('unlocked')
  })
})
