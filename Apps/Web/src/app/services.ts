// 어댑터를 만들어 domain 유스케이스에 끼운다. 화면에는 이 결과만 넘긴다 (ADR-0004, ADR-0013)
import { createApiClient, resolveBaseUrl } from '../adapters/http/apiClient'
import { createHttpAuthApi } from '../adapters/http/httpAuthApi'
import { createAuthService, type AuthService } from '../domain/auth/authService'

export interface Services {
  readonly auth: AuthService
}

/** @throws InvalidBaseUrlError 빌드할 때 넣은 서버 주소가 규칙에 맞지 않을 때 */
export function createServices(apiBaseUrl: string | undefined = import.meta.env.VITE_API_BASE_URL): Services {
  const client = createApiClient({ baseUrl: resolveBaseUrl(apiBaseUrl), fetch: globalThis.fetch.bind(globalThis) })
  return { auth: createAuthService(createHttpAuthApi(client)) }
}
