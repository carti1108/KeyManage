// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { NetworkError, ServerError, UnexpectedResponseError } from '../../domain/server/serverErrors'
import { InvalidBaseUrlError, createApiClient, resolveBaseUrl } from './apiClient'
import { createHttpAuthApi } from './httpAuthApi'

interface Recorded {
  url: string
  init: RequestInit
}

// 요청을 기록하고 정해 둔 응답(또는 예외)을 돌려주는 fetch
function fakeFetch(respond: () => Response | Promise<Response>) {
  const calls: Recorded[] = []
  const fetch = async (url: RequestInfo | URL, init: RequestInit = {}) => {
    calls.push({ url: String(url), init })
    return respond()
  }
  return { fetch: fetch as typeof globalThis.fetch, calls }
}

const json = (status: number, body: unknown, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } })

const errorBody = (code: string) => ({ code, message: '서버 문구', timestamp: '2026-10-29T00:00:00Z' })

describe('서버 주소 (ADR-0013, SEC-002)', () => {
  it('비어 있으면 같은 출처로 요청한다', () => {
    expect(resolveBaseUrl(undefined)).toBe('')
    expect(resolveBaseUrl('')).toBe('')
  })

  it('HTTPS 주소와 localhost의 HTTP 주소는 출처만 남겨 쓴다', () => {
    expect(resolveBaseUrl('https://vault.example.com/')).toBe('https://vault.example.com')
    expect(resolveBaseUrl('http://localhost:8080')).toBe('http://localhost:8080')
    expect(resolveBaseUrl('http://127.0.0.1:8080')).toBe('http://127.0.0.1:8080')
  })

  it('거절할 때 오류 메시지에 주소 원문(비밀번호가 섞였을 수 있음)을 넣지 않는다', () => {
    expect(() => resolveBaseUrl('https://user:secret@a.example')).toThrow(expect.objectContaining({ message: expect.not.stringContaining('secret') }))
  })

  it('localhost가 아닌 HTTP, 경로·쿼리가 붙은 주소, 깨진 주소는 거절한다', () => {
    for (const bad of ['http://vault.example.com', 'https://vault.example.com/api', 'https://a.example?x=1', 'https://*.example.com', 'https://user:pw@a.example', 'ftp://a.example', 'not a url']) {
      expect(() => resolveBaseUrl(bad), bad).toThrow(InvalidBaseUrlError)
    }
  })
})

describe('API 클라이언트 (api-spec 1장)', () => {
  it('JSON 본문과 Bearer 토큰을 /api/v1 아래로 보낸다', async () => {
    const { fetch, calls } = fakeFetch(() => json(200, { ok: true }))
    const client = createApiClient({ baseUrl: 'https://vault.example.com', fetch })

    await client.request('POST', '/vault', { body: { a: 1 }, accessToken: 'tok' })

    expect(calls[0].url).toBe('https://vault.example.com/api/v1/vault')
    expect(calls[0].init.method).toBe('POST')
    expect(calls[0].init.body).toBe('{"a":1}')
    expect(new Headers(calls[0].init.headers).get('Authorization')).toBe('Bearer tok')
    expect(new Headers(calls[0].init.headers).get('Content-Type')).toBe('application/json; charset=UTF-8')
    // 쿠키 같은 자격 증명은 보내지 않는다. 인증은 Bearer 토큰만 쓴다
    expect(calls[0].init.credentials).toBe('omit')
    // 리다이렉트로 인증 해시가 다른 곳에 가지 않게 따라가지 않는다
    expect(calls[0].init.redirect).toBe('error')
    // 응답이 없을 때 무한정 기다리지 않는다
    expect(calls[0].init.signal).toBeInstanceOf(AbortSignal)
  })

  it('오류 응답은 코드와 상태를 담은 ServerError로 바꾼다', async () => {
    for (const [status, code] of [[401, 'AUTH_FAILED'], [409, 'EMAIL_ALREADY_REGISTERED'], [403, 'REGISTRATION_DISABLED'], [403, 'ORIGIN_NOT_ALLOWED'], [500, 'INTERNAL_ERROR']] as const) {
      const { fetch } = fakeFetch(() => json(status, errorBody(code)))

      const error = await createApiClient({ baseUrl: '', fetch }).request('POST', '/auth/login').catch((e: unknown) => e)

      expect(error).toBeInstanceOf(ServerError)
      expect(error).toMatchObject({ status, code })
    }
  })

  it('429는 Retry-After(초)를 함께 담는다', async () => {
    const { fetch } = fakeFetch(() => json(429, errorBody('TOO_MANY_ATTEMPTS'), { 'Retry-After': '42' }))

    await expect(createApiClient({ baseUrl: '', fetch }).request('POST', '/auth/login')).rejects.toMatchObject({
      code: 'TOO_MANY_ATTEMPTS',
      retryAfterSeconds: 42,
    })
  })

  it('모르는 코드나 JSON이 아닌 오류 응답은 UNKNOWN으로 받는다', async () => {
    for (const response of [() => json(418, errorBody('TEAPOT')), () => new Response('<html>Bad Gateway</html>', { status: 502 })]) {
      const { fetch } = fakeFetch(response)

      await expect(createApiClient({ baseUrl: '', fetch }).request('GET', '/vault')).rejects.toMatchObject({ code: 'UNKNOWN' })
    }
  })

  it('서버에 닿지 못하면 NetworkError로 바꾼다', async () => {
    const { fetch } = fakeFetch(() => Promise.reject(new TypeError('Failed to fetch')))

    await expect(createApiClient({ baseUrl: '', fetch }).request('GET', '/vault')).rejects.toBeInstanceOf(NetworkError)
  })

  it('본문을 받는 중에 시간이 다 되면 NetworkError로 바꾼다', async () => {
    const { fetch } = fakeFetch(() => {
      const response = new Response('{}', { status: 200 })
      response.json = () => Promise.reject(new DOMException('시간 초과', 'TimeoutError'))
      return response
    })

    await expect(createApiClient({ baseUrl: '', fetch }).request('GET', '/vault')).rejects.toBeInstanceOf(NetworkError)
  })

  it('성공 응답이 JSON이 아니면 UnexpectedResponseError로 바꾼다', async () => {
    const { fetch } = fakeFetch(() => new Response('<html></html>', { status: 200 }))

    await expect(createApiClient({ baseUrl: '', fetch }).request('GET', '/vault')).rejects.toBeInstanceOf(UnexpectedResponseError)
  })

  it('204 응답은 본문 없이 끝낸다', async () => {
    const { fetch } = fakeFetch(() => new Response(null, { status: 204 }))

    await expect(createApiClient({ baseUrl: '', fetch }).request('POST', '/auth/logout')).resolves.toBeUndefined()
  })
})

describe('인증 API 어댑터 (api-spec 2.5)', () => {
  it('prelogin 응답의 형식을 확인한다', async () => {
    const { fetch } = fakeFetch(() => json(200, { kdf: 'PBKDF2-HMAC-SHA256', iterations: '600000', salt: 'AAAA' }))

    await expect(createHttpAuthApi(createApiClient({ baseUrl: '', fetch })).prelogin('a@b.cd')).rejects.toBeInstanceOf(UnexpectedResponseError)
  })

  it('로그인 성공 응답에서 세션만 꺼낸다', async () => {
    const { fetch, calls } = fakeFetch(() => json(200, { accessToken: 'tok', expiresAt: '2026-11-28T00:00:00Z', extra: 1 }))

    const session = await createHttpAuthApi(createApiClient({ baseUrl: '', fetch })).login({ email: 'a@b.cd', authHash: 'h' })

    expect(calls[0].url).toBe('/api/v1/auth/login')
    expect(session).toEqual({ accessToken: 'tok', expiresAt: '2026-11-28T00:00:00Z' })
  })

  it('세션 토큰이 빠진 응답은 거절한다', async () => {
    const { fetch } = fakeFetch(() => json(201, { expiresAt: '2026-11-28T00:00:00Z' }))

    await expect(
      createHttpAuthApi(createApiClient({ baseUrl: '', fetch })).register({ email: 'a@b.cd', authHash: 'h', kdfSalt: 's', kdfIterations: 600_000 }),
    ).rejects.toBeInstanceOf(UnexpectedResponseError)
  })

  it('로그아웃은 토큰을 Authorization 헤더로 보낸다', async () => {
    const { fetch, calls } = fakeFetch(() => new Response(null, { status: 204 }))

    await createHttpAuthApi(createApiClient({ baseUrl: '', fetch })).logout('tok')

    expect(calls[0].url).toBe('/api/v1/auth/logout')
    expect(new Headers(calls[0].init.headers).get('Authorization')).toBe('Bearer tok')
  })
})
