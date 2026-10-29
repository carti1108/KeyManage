// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { ServerError, UnexpectedResponseError } from '../../domain/server/serverErrors'
import { createApiClient } from './apiClient'
import { createHttpVaultApi } from './httpVaultApi'

const payload = { version: 1, cipher: 'AES-256-GCM', iv: 'aXY=', ciphertext: 'Y3Q=', tag: 'dGFn' } as const

function apiReturning(response: () => Response) {
  const calls: { url: string; init: RequestInit }[] = []
  const fetch = (async (url: RequestInfo | URL, init: RequestInit = {}) => {
    calls.push({ url: String(url), init })
    return response()
  }) as typeof globalThis.fetch
  return { api: createHttpVaultApi(createApiClient({ baseUrl: '', fetch }), 'tok'), calls }
}

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

describe('볼트 API 어댑터 (api-spec 2.3·2.4)', () => {
  it('조회는 토큰을 붙여 revision과 페이로드를 받는다', async () => {
    const { api, calls } = apiReturning(() => json(200, { revision: 7, payload }))

    expect(await api.get()).toEqual({ revision: 7, payload })
    expect(calls[0].url).toBe('/api/v1/vault')
    expect(calls[0].init.method).toBe('GET')
    expect(new Headers(calls[0].init.headers).get('Authorization')).toBe('Bearer tok')
  })

  it('저장된 볼트가 없으면(404 VAULT_NOT_FOUND) null을 돌려준다', async () => {
    const { api } = apiReturning(() => json(404, { code: 'VAULT_NOT_FOUND', message: 'm', timestamp: 't' }))

    expect(await api.get()).toBeNull()
  })

  it('저장은 기대 revision과 페이로드를 보내고 새 revision을 받는다', async () => {
    const { api, calls } = apiReturning(() => json(200, { revision: 8 }))

    expect(await api.save(7, payload)).toBe(8)
    expect(JSON.parse(String(calls[0].init.body))).toEqual({ expectedRevision: 7, payload })
  })

  it('409는 VAULT_CONFLICT ServerError로 그대로 알린다', async () => {
    const { api } = apiReturning(() => json(409, { code: 'VAULT_CONFLICT', message: 'm', currentRevision: 9, timestamp: 't' }))

    await expect(api.save(7, payload)).rejects.toEqual(expect.objectContaining({ code: 'VAULT_CONFLICT' }))
    await expect(api.save(7, payload)).rejects.toBeInstanceOf(ServerError)
  })

  it('revision이 0 이상의 정수가 아니거나 페이로드가 객체가 아니면 거절한다', async () => {
    for (const body of [{ revision: '7', payload }, { revision: -1, payload }, { revision: 1.5, payload }, { revision: 7, payload: 'x' }]) {
      const { api } = apiReturning(() => json(200, body))

      await expect(api.get(), JSON.stringify(body)).rejects.toBeInstanceOf(UnexpectedResponseError)
    }
    const { api } = apiReturning(() => json(200, { revision: 'x' }))
    await expect(api.save(0, payload)).rejects.toBeInstanceOf(UnexpectedResponseError)
  })
})
