// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { ServerError, UnexpectedResponseError } from '../../domain/server/serverErrors'
import { createApiClient } from './apiClient'
import { createHttpAuthApi } from './httpAuthApi'

const payload = { version: 1, cipher: 'AES-256-GCM', iv: 'aXY=', ciphertext: 'Y3Q=', tag: 'dGFn' } as const

const request = {
  currentAuthHash: 'Y3VycmVudA==',
  newAuthHash: 'bmV3',
  kdf: { iterations: 600_000, salt: 'c2FsdA==' },
  vault: { expectedRevision: 7, payload },
}

function apiReturning(response: () => Response) {
  const calls: { url: string; init: RequestInit }[] = []
  const fetch = (async (url: RequestInfo | URL, init: RequestInit = {}) => {
    calls.push({ url: String(url), init })
    return response()
  }) as typeof globalThis.fetch
  return { api: createHttpAuthApi(createApiClient({ baseUrl: '', fetch })), calls }
}

const json = (status: number, body: unknown, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } })

describe('마스터 비밀번호 변경 API 어댑터 (api-spec 2.5.5)', () => {
  it('토큰을 붙여 현재·새 인증 해시, KDF 값, 볼트를 한 요청으로 보내고 새 토큰과 revision을 받는다', async () => {
    const { api, calls } = apiReturning(() => json(200, { accessToken: 'new-token', expiresAt: '2026-12-02T11:20:00Z', revision: 8 }))

    expect(await api.changePassword('tok', request)).toEqual({ accessToken: 'new-token', expiresAt: '2026-12-02T11:20:00Z', revision: 8 })
    expect(calls[0].url).toBe('/api/v1/auth/change-password')
    expect(calls[0].init.method).toBe('POST')
    expect(new Headers(calls[0].init.headers).get('Authorization')).toBe('Bearer tok')
    expect(JSON.parse(String(calls[0].init.body))).toEqual(request)
  })

  it('현재 인증 해시가 틀리면 AUTH_FAILED, 충돌이면 VAULT_CONFLICT ServerError로 그대로 알린다', async () => {
    for (const [status, code] of [
      [401, 'AUTH_FAILED'],
      [409, 'VAULT_CONFLICT'],
      [401, 'UNAUTHORIZED'],
    ] as const) {
      const { api } = apiReturning(() => json(status, { code, message: 'm', timestamp: 't' }))

      await expect(api.changePassword('tok', request)).rejects.toEqual(new ServerError(status, code))
    }
  })

  it('429는 Retry-After 초를 담아 알린다', async () => {
    const { api } = apiReturning(() => json(429, { code: 'TOO_MANY_ATTEMPTS', message: 'm', timestamp: 't' }, { 'Retry-After': '900' }))

    await expect(api.changePassword('tok', request)).rejects.toEqual(expect.objectContaining({ code: 'TOO_MANY_ATTEMPTS', retryAfterSeconds: 900 }))
  })

  it('응답에 토큰이나 0 이상의 정수 revision이 없으면 거절한다', async () => {
    for (const body of [
      { accessToken: '', expiresAt: 'e', revision: 1 },
      { accessToken: 't', expiresAt: 'e' },
      { accessToken: 't', expiresAt: 'e', revision: -1 },
      { accessToken: 't', expiresAt: 'e', revision: 1.5 },
    ]) {
      const { api } = apiReturning(() => json(200, body))

      await expect(api.changePassword('tok', request), JSON.stringify(body)).rejects.toBeInstanceOf(UnexpectedResponseError)
    }
  })
})
