// 가입·로그인·로그아웃 유스케이스 (SEC-002, ADR-0008)
// 마스터 비밀번호와 암호화 키는 이 모듈 밖의 서버 포트로 넘기지 않는다. 서버에는 인증 해시만 보낸다
import { InvalidBase64Error, fromBase64, toBase64 } from '../crypto/base64'
import { InvalidKdfParamsError, MIN_ITERATIONS, SALT_BYTES, deriveKeys } from '../crypto/keys'
import type { AuthApi, KdfParams, Session } from './authApi'

const SUPPORTED_KDF: KdfParams['kdf'] = 'PBKDF2-HMAC-SHA256'
const MAX_EMAIL_LENGTH = 254
// 서버(Email)와 같은 형식 검사. 틀린 이메일로 60만 회 키 유도를 하지 않도록 먼저 거른다
const EMAIL_FORMAT = /^[^@\s]+@[^@\s]+\.[^@\s]+$/

export class InvalidEmailError extends Error {
  constructor() {
    super('이메일 형식이 올바르지 않음')
    this.name = 'InvalidEmailError'
  }
}

/** 잠금 해제 때 서버 없이 같은 키를 다시 만들기 위한 값. 비밀이 아니다 (SEC-003) */
export interface UnlockKdf {
  /** Standard Base64, 16바이트 */
  readonly salt: string
  readonly iterations: number
}

export interface AuthResult {
  readonly session: Session
  /** 볼트 암·복호화 키. 잠그면 버린다 (SEC-003) */
  readonly encryptionKey: CryptoKey
  readonly kdf: UnlockKdf
}

/** 앞뒤 공백 제거·소문자. 서버도 같은 규칙으로 비교한다 (api-spec 2.5) */
export function normalizeEmail(email: string): string {
  const normalized = email.trim().toLowerCase()
  if (normalized.length === 0 || normalized.length > MAX_EMAIL_LENGTH || !EMAIL_FORMAT.test(normalized)) {
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
      const kdf = { salt: toBase64(salt), iterations: MIN_ITERATIONS }
      const session = await api.register({
        email: normalized,
        authHash: takeAuthHash(keys.authHash),
        kdfSalt: kdf.salt,
        kdfIterations: kdf.iterations,
      })
      return { session, encryptionKey: keys.encryptionKey, kdf }
    },

    /**
     * @throws InvalidKdfParamsError 서버가 준 KDF 값이 규격 밖이면 로그인 요청을 보내지 않는다
     */
    async login(email: string, password: string): Promise<AuthResult> {
      const normalized = normalizeEmail(email)
      const params = await api.prelogin(normalized)
      if (params.kdf !== SUPPORTED_KDF) throw new InvalidKdfParamsError(`지원하지 않는 KDF: ${params.kdf}`)
      const keys = await deriveKeys(password, decodeSalt(params.salt), params.iterations)
      const session = await api.login({ email: normalized, authHash: takeAuthHash(keys.authHash) })
      return { session, encryptionKey: keys.encryptionKey, kdf: { salt: params.salt, iterations: params.iterations } }
    },

    logout(accessToken: string): Promise<void> {
      return api.logout(accessToken)
    },
  }
}

export type AuthService = ReturnType<typeof createAuthService>

// 인증 해시는 서버로 보낼 문자열만 만들고 바이트는 지운다. 잠긴 동안 메모리에 남지 않게 한다 (SEC-003)
function takeAuthHash(authHash: Uint8Array): string {
  const encoded = toBase64(authHash)
  authHash.fill(0)
  return encoded
}

function decodeSalt(salt: string): Uint8Array<ArrayBuffer> {
  try {
    return fromBase64(salt)
  } catch (error) {
    if (error instanceof InvalidBase64Error) throw new InvalidKdfParamsError('salt가 표준 Base64가 아님')
    throw error
  }
}
