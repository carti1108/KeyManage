// 서버 통신 실패를 화면이 구분해 다룰 수 있도록 나눈 오류 (api-spec 1장)

/** api-spec에 정의된 오류 코드. 모르는 코드는 UNKNOWN으로 받는다 */
export const SERVER_ERROR_CODES = [
  'INVALID_PARAMETER',
  'UNAUTHORIZED',
  'TOO_MANY_ATTEMPTS',
  'INTERNAL_ERROR',
  'VAULT_TOO_LARGE',
  'VAULT_NOT_FOUND',
  'VAULT_CONFLICT',
  'AUTH_FAILED',
  'EMAIL_ALREADY_REGISTERED',
  'REGISTRATION_DISABLED',
  'ORIGIN_NOT_ALLOWED',
] as const

export type ServerErrorCode = (typeof SERVER_ERROR_CODES)[number] | 'UNKNOWN'

/** 서버가 오류 응답을 보냄 */
export class ServerError extends Error {
  readonly status: number
  readonly code: ServerErrorCode
  /** 429일 때 서버가 알려 준 대기 시간(초) */
  readonly retryAfterSeconds?: number

  constructor(status: number, code: ServerErrorCode, retryAfterSeconds?: number) {
    super(`서버 오류 ${status} ${code}`)
    this.name = 'ServerError'
    this.status = status
    this.code = code
    this.retryAfterSeconds = retryAfterSeconds
  }
}

/** 서버에 닿지 못함 (오프라인, 시간 초과, 연결 거부) */
export class NetworkError extends Error {
  constructor(message = '서버에 연결할 수 없음') {
    super(message)
    this.name = 'NetworkError'
  }
}

/** 성공 응답이 약속한 형식이 아님. 서버와 클라이언트 버전이 어긋났을 수 있다 */
export class UnexpectedResponseError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'UnexpectedResponseError'
  }
}
