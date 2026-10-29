// fetch 기반 API 클라이언트. 브라우저와 데스크톱 앱이 함께 쓴다 (api-spec 1장, ADR-0013)
export { InvalidBaseUrlError, resolveBaseUrl } from './baseUrl'
import { NetworkError, SERVER_ERROR_CODES, ServerError, UnexpectedResponseError, type ServerErrorCode } from '../../domain/server/serverErrors'

const API_PREFIX = '/api/v1'
// 응답이 없을 때 무한정 기다리지 않는다. 볼트 업로드(최대 5MB)도 이 안에 끝나야 한다
const TIMEOUT_MS = 30_000

export interface RequestOptions {
  readonly body?: unknown
  readonly accessToken?: string
}

export interface ApiClientConfig {
  /** resolveBaseUrl을 거친 값 */
  readonly baseUrl: string
  readonly fetch: typeof globalThis.fetch
}

export function createApiClient({ baseUrl, fetch }: ApiClientConfig) {
  return {
    /**
     * @returns 성공 응답의 JSON. 204면 undefined
     * @throws ServerError, NetworkError, UnexpectedResponseError
     */
    async request(method: 'GET' | 'POST', path: string, options: RequestOptions = {}): Promise<unknown> {
      const headers = new Headers({ Accept: 'application/json' })
      if (options.body !== undefined) headers.set('Content-Type', 'application/json; charset=UTF-8')
      if (options.accessToken) headers.set('Authorization', `Bearer ${options.accessToken}`)

      let response: Response
      try {
        response = await fetch(`${baseUrl}${API_PREFIX}${path}`, {
          method,
          headers,
          body: options.body === undefined ? undefined : JSON.stringify(options.body),
          credentials: 'omit',
          // 리다이렉트를 따라가면 인증 해시가 담긴 본문이 다른 주소로 갈 수 있다
          redirect: 'error',
          signal: AbortSignal.timeout(TIMEOUT_MS),
        })
      } catch {
        throw new NetworkError()
      }

      if (!response.ok) throw await toServerError(response)
      if (response.status === 204) return undefined
      try {
        return await response.json()
      } catch (error) {
        // 제한 시간은 본문을 받는 동안에도 적용된다. 느린 회선을 형식 오류로 오해하지 않게 구분한다
        if (isAbort(error)) throw new NetworkError('응답을 받는 중 시간이 초과됨')
        throw new UnexpectedResponseError(`JSON이 아닌 성공 응답: ${response.status}`)
      }
    },
  }
}

export type ApiClient = ReturnType<typeof createApiClient>

async function toServerError(response: Response): Promise<ServerError> {
  let code: ServerErrorCode = 'UNKNOWN'
  try {
    const body: unknown = await response.json()
    const raw = typeof body === 'object' && body !== null ? (body as { code?: unknown }).code : undefined
    if (isKnownCode(raw)) code = raw
  } catch {
    // 프록시가 만든 HTML 오류 페이지 등. 코드 없이 상태만 알린다
  }
  return new ServerError(response.status, code, parseRetryAfter(response.headers.get('Retry-After')))
}

function isAbort(error: unknown): boolean {
  return error instanceof DOMException && (error.name === 'TimeoutError' || error.name === 'AbortError')
}

function isKnownCode(value: unknown): value is ServerErrorCode {
  return typeof value === 'string' && (SERVER_ERROR_CODES as readonly string[]).includes(value)
}

// 서버는 초 단위 정수만 보낸다 (api-spec 1장). 날짜 형식은 쓰지 않는다
function parseRetryAfter(value: string | null): number | undefined {
  if (value === null || !/^\d+$/.test(value)) return undefined
  return Number(value)
}
