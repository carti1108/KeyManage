// AuthApi 포트의 HTTP 구현 (api-spec 2.5)
import type { AuthApi, ChangedPassword, KdfParams, Session } from '../../domain/auth/authApi'
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
    async changePassword(accessToken, request) {
      return toChangedPassword(await client.request('POST', '/auth/change-password', { accessToken, body: request }))
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

function toChangedPassword(body: unknown): ChangedPassword {
  const session = toSession(body)
  const { revision } = asRecord(body)
  if (!Number.isSafeInteger(revision) || (revision as number) < 0) throw new UnexpectedResponseError('revision이 0 이상의 정수가 아님')
  return { ...session, revision: revision as number }
}
