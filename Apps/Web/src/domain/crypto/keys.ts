// 마스터 비밀번호 → 마스터 키 → 암호화 키·인증 해시 (vault-spec 2장, ADR-0008, ADR-0011)
// 브라우저·Node·데스크톱 웹뷰에 모두 있는 WebCrypto(globalThis.crypto)만 쓴다

export const MIN_ITERATIONS = 600_000
export const SALT_BYTES = 16

const KEY_BITS = 256
const ENCRYPTION_INFO = 'keymanage-enc'
const AUTH_INFO = 'keymanage-auth'

// 앞뒤에서 자르는 공백 문자. 플랫폼마다 trim의 범위가 달라 문자를 고정한다 (vault-spec 2장)
const TRIMMED = new Set([' ', '\u0009', '\u000A', '\u000D', ' ', '　'])

/** 서버가 내려준 KDF 값이 규격보다 약하거나 형식이 틀림. 따르지 않고 로그인을 멈춘다 (SEC-002) */
export class InvalidKdfParamsError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'InvalidKdfParamsError'
  }
}

export interface DerivedKeys {
  /** 볼트 암·복호화용 AES-256-GCM 키. 밖으로 꺼낼 수 없다 */
  readonly encryptionKey: CryptoKey
  /** 서버 로그인에 보내는 32바이트 인증 해시. 이것으로 암호화 키를 계산할 수 없다 */
  readonly authHash: Uint8Array
}

/** NFC 정규화 → 지정 공백 문자만 앞뒤에서 제거. 중간 공백은 유지한다 (ADR-0011) */
export function preprocessPassword(password: string): string {
  const s = password.normalize('NFC')
  let start = 0
  let end = s.length
  while (start < end && TRIMMED.has(s[start])) start += 1
  while (end > start && TRIMMED.has(s[end - 1])) end -= 1
  return s.slice(start, end)
}

/**
 * @throws InvalidKdfParamsError 반복 횟수가 600,000보다 적거나 salt가 16바이트가 아닐 때.
 *   서버가 prelogin으로 약한 값을 내려줘 키를 약하게 만드는 것을 막는다
 */
export async function deriveKeys(password: string, salt: Uint8Array<ArrayBuffer>, iterations: number): Promise<DerivedKeys> {
  if (!Number.isInteger(iterations) || iterations < MIN_ITERATIONS) {
    throw new InvalidKdfParamsError(`반복 횟수는 ${MIN_ITERATIONS} 이상이어야 함`)
  }
  if (salt.length !== SALT_BYTES) throw new InvalidKdfParamsError(`salt는 ${SALT_BYTES}바이트여야 함`)

  const subtle = globalThis.crypto.subtle
  const encode = (text: string) => new TextEncoder().encode(text)

  const passwordKey = await subtle.importKey('raw', encode(preprocessPassword(password)), 'PBKDF2', false, ['deriveBits'])
  const masterKey = new Uint8Array(
    await subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt, iterations }, passwordKey, KEY_BITS),
  )
  const hkdfKey = await subtle.importKey('raw', masterKey, 'HKDF', false, ['deriveKey', 'deriveBits'])
  // 마스터 키 원본은 HKDF 키로 넘긴 뒤 메모리에서 지운다
  masterKey.fill(0)

  // salt 빈 값: RFC 5869에 따라 32바이트 0과 같다 (vault-spec 2장)
  const hkdf = (info: string): HkdfParams => ({ name: 'HKDF', hash: 'SHA-256', salt: new Uint8Array(0), info: encode(info) })
  const encryptionKey = await subtle.deriveKey(hkdf(ENCRYPTION_INFO), hkdfKey, { name: 'AES-GCM', length: KEY_BITS }, false, ['encrypt', 'decrypt'])
  const authHash = new Uint8Array(await subtle.deriveBits(hkdf(AUTH_INFO), hkdfKey, KEY_BITS))
  return { encryptionKey, authHash }
}
