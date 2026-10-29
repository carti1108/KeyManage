// 가입·로그인·로그아웃 유스케이스 (SEC-002, ADR-0008)
// 마스터 비밀번호와 암호화 키는 이 모듈 밖의 서버 포트로 넘기지 않는다. 서버에는 인증 해시만 보낸다
import { InvalidBase64Error, fromBase64, toBase64 } from '../crypto/base64'
import { InvalidKdfParamsError, MIN_ITERATIONS, SALT_BYTES, deriveKeys } from '../crypto/keys'
import type { AuthApi, KdfParams, Session } from './authApi'

const SUPPORTED_KDF: KdfParams['kdf'] = 'PBKDF2-HMAC-SHA256'
const MAX_EMAIL_LENGTH = 254

export class InvalidEmailError extends Error {
  constructor() {
    super('이메일 형식이 올바르지 않음')
    this.name = 'InvalidEmailError'
  }
}

export interface AuthResult {
  readonly session: Session
  /** 볼트 암·복호화 키. 잠그면 버린다 (SEC-003) */
  readonly encryptionKey: CryptoKey
}

/** 앞뒤 공백 제거·소문자. 서버도 같은 규칙으로 비교한다 (api-spec 2.5) */
export function normalizeEmail(email: string): string {
  const normalized = email.trim().toLowerCase()
  if (normalized.length === 0 || normalized.length > MAX_EMAIL_LENGTH || !normalized.includes('@')) {
    throw new InvalidEmailError()
  }
  return normalized
}

export function createAuthService(api: AuthApi) {
  return {
    /** 새 salt를 만들어 600,000회로 가입한다 */
    async register(email: string, password: string): Promise<AuthResult> {
      const normalized = normalizeEmail(email)
      const salt = globalThis.crypto.getRandomValues(new Uint8Array(SALT_BYTES))
      const keys = await deriveKeys(password, salt, MIN_ITERATIONS)
      const session = await api.register({
        email: normalized,
        authHash: toBase64(keys.authHash),
        kdfSalt: toBase64(salt),
        kdfIterations: MIN_ITERATIONS,
      })
      return { session, encryptionKey: keys.encryptionKey }
    },

    /**
     * @throws InvalidKdfParamsError 서버가 준 KDF 값이 규격 밖이면 로그인 요청을 보내지 않는다
     */
    async login(email: string, password: string): Promise<AuthResult> {
      const normalized = normalizeEmail(email)
      const params = await api.prelogin(normalized)
      if (params.kdf !== SUPPORTED_KDF) throw new InvalidKdfParamsError(`지원하지 않는 KDF: ${params.kdf}`)
      const keys = await deriveKeys(password, decodeSalt(params.salt), params.iterations)
      const session = await api.login({ email: normalized, authHash: toBase64(keys.authHash) })
      return { session, encryptionKey: keys.encryptionKey }
    },

    logout(accessToken: string): Promise<void> {
      return api.logout(accessToken)
    },
  }
}

export type AuthService = ReturnType<typeof createAuthService>

function decodeSalt(salt: string): Uint8Array<ArrayBuffer> {
  try {
    return fromBase64(salt)
  } catch (error) {
    if (error instanceof InvalidBase64Error) throw new InvalidKdfParamsError('salt가 표준 Base64가 아님')
    throw error
  }
}
