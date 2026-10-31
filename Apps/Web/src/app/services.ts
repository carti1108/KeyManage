// 어댑터를 만들어 domain 유스케이스에 끼운다. 화면에는 이 결과만 넘긴다 (ADR-0004, ADR-0013)
import { createApiClient, resolveBaseUrl } from '../adapters/http/apiClient'
import { createHttpAuthApi } from '../adapters/http/httpAuthApi'
import { createHttpVaultApi } from '../adapters/http/httpVaultApi'
import { createAuthService } from '../domain/auth/authService'
import { createVaultSession, type VaultSession } from '../domain/session/vaultSession'
import { createRevisionMemory, createVaultSync } from '../domain/vault/vaultSync'

export interface Services {
  /** 로그인·가입·잠금·잠금 해제·로그아웃과 볼트 저장 (SEC-002, SEC-003) */
  readonly session: VaultSession
}

/** @throws InvalidBaseUrlError 빌드할 때 넣은 서버 주소가 규칙에 맞지 않을 때 */
export function createServices(apiBaseUrl: string | undefined = import.meta.env.VITE_API_BASE_URL): Services {
  const client = createApiClient({ baseUrl: resolveBaseUrl(apiBaseUrl), fetch: globalThis.fetch.bind(globalThis) })
  // 잠금 해제·재로그인으로 동기화를 새로 만들어도 되돌림 감지 기준을 이어 간다
  const revisionMemory = createRevisionMemory()
  return {
    session: createVaultSession({
      auth: createAuthService(createHttpAuthApi(client)),
      vaultSyncFor: (accessToken, encryptionKey) =>
        createVaultSync({ api: createHttpVaultApi(client, accessToken), encryptionKey, now: () => new Date(), revisionMemory }),
    }),
  }
}
