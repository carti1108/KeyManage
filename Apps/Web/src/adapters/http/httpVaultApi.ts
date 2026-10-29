// VaultApi 포트의 HTTP 구현 (api-spec 2.3·2.4)
import type { EncryptedVaultPayload } from '../../domain/crypto/vaultCipher'
import { ServerError, UnexpectedResponseError } from '../../domain/server/serverErrors'
import type { RemoteVault, VaultApi } from '../../domain/vault/vaultApi'
import type { ApiClient } from './apiClient'
import { asRecord } from './responseBody'

export function createHttpVaultApi(client: ApiClient, accessToken: string): VaultApi {
  return {
    async get() {
      try {
        return toRemoteVault(await client.request('GET', '/vault', { accessToken }))
      } catch (error) {
        if (error instanceof ServerError && error.code === 'VAULT_NOT_FOUND') return null
        throw error
      }
    },
    async save(expectedRevision, payload) {
      const body = asRecord(await client.request('POST', '/vault', { accessToken, body: { expectedRevision, payload } }))
      return toRevision(body.revision)
    },
  }
}

// 페이로드 필드 검사는 복호화할 때(openVault) 한다. 여기서는 모양만 확인한다
function toRemoteVault(body: unknown): RemoteVault {
  const { revision, payload } = asRecord(body)
  if (typeof payload !== 'object' || payload === null || Array.isArray(payload)) throw new UnexpectedResponseError('볼트 페이로드가 객체가 아님')
  return { revision: toRevision(revision), payload: payload as EncryptedVaultPayload }
}

function toRevision(value: unknown): number {
  if (!Number.isSafeInteger(value) || (value as number) < 0) throw new UnexpectedResponseError('revision이 0 이상의 정수가 아님')
  return value as number
}
