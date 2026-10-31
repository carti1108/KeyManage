// 오류를 사용자 문구로 바꾼다. 문구는 정책이 정한 그대로 쓴다 (SEC-002, SEC-003, COM-001)
import { InvalidEmailError } from '../../domain/auth/authService'
import { InvalidPasswordError } from '../../domain/crypto/keys'
import { NetworkError, ServerError } from '../../domain/server/serverErrors'
import { SessionExpiredError, VaultSetupError, WrongMasterPasswordError } from '../../domain/session/vaultSession'

export interface ErrorMessage {
  readonly title?: string
  readonly body: string
}

const SESSION_EXPIRED = '로그인이 만료되었습니다. 다시 로그인해 주세요.'
// 내부 메시지에는 서버 응답이나 스택이 섞일 수 있어 화면에 그대로 보이지 않는다
const UNKNOWN = '알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'

const SERVER_MESSAGES: Partial<Record<ServerError['code'], ErrorMessage>> = {
  AUTH_FAILED: { title: '로그인 실패', body: '이메일 또는 마스터 비밀번호가 올바르지 않습니다.' },
  UNAUTHORIZED: { body: SESSION_EXPIRED },
  TOO_MANY_ATTEMPTS: { body: '로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.' },
  EMAIL_ALREADY_REGISTERED: { body: '이미 가입된 이메일입니다.' },
  REGISTRATION_DISABLED: { body: '현재 새 계정을 만들 수 없습니다.' },
}

export function describeError(error: unknown): ErrorMessage {
  if (error instanceof ServerError) return SERVER_MESSAGES[error.code] ?? { body: UNKNOWN }
  if (error instanceof SessionExpiredError) return { body: SESSION_EXPIRED }
  if (error instanceof WrongMasterPasswordError) return { body: '마스터 비밀번호가 올바르지 않습니다.' }
  if (error instanceof VaultSetupError) return { body: '계정은 만들었지만 볼트를 준비하지 못했습니다. 다시 로그인해 주세요.' }
  if (error instanceof InvalidEmailError) return { body: '올바른 이메일 주소를 입력해 주세요.' }
  if (error instanceof InvalidPasswordError) return { body: '마스터 비밀번호에 쓸 수 없는 문자가 있습니다.' }
  if (error instanceof NetworkError) return { body: '서버에 연결할 수 없습니다. 네트워크 연결을 확인해 주세요.' }
  return { body: UNKNOWN }
}
