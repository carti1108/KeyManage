// AuthApi 포트의 HTTP 구현 (api-spec 2.5)
import type { AuthApi, KdfParams, Session } from '../../domain/auth/authApi'
import { UnexpectedResponseError } from '../../domain/server/serverErrors'
import type { ApiClient } from './apiClient'
import { asRecord } from './responseBody'

export function createHttpAuthApi(client: ApiClient): AuthApi {
  return {
    async prelogin(email) {
      return toKdfParams(await client.request('POST', '/auth/prelogin', { body: { email } }))
    },
    async register(request) {
      return toSession(await client.request('POST', '/auth/register', { body: request }))
    },
    async login(request) {
      return toSession(await client.request('POST', '/auth/login', { body: request }))
    },
    async logout(accessToken) {
      await client.request('POST', '/auth/logout', { accessToken })
    },
  }
}

function toKdfParams(body: unknown): KdfParams {
  const { kdf, iterations, salt } = asRecord(body)
  if (typeof kdf !== 'string' || typeof iterations !== 'number' || typeof salt !== 'string') {
    throw new UnexpectedResponseError('prelogin 응답 형식이 틀림')
  }
  return { kdf: kdf as KdfParams['kdf'], iterations, salt }
}

function toSession(body: unknown): Session {
  const { accessToken, expiresAt } = asRecord(body)
  if (typeof accessToken !== 'string' || accessToken === '' || typeof expiresAt !== 'string') {
    throw new UnexpectedResponseError('세션 응답 형식이 틀림')
  }
  return { accessToken, expiresAt }
}
