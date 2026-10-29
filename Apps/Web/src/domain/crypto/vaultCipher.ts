import { fromBase64, toBase64 } from './base64'

// 볼트 평문 JSON ↔ 암호화 페이로드 (vault-spec 3장, ADR-0011)

const VERSION = 1
const CIPHER = 'AES-256-GCM'
const IV_BYTES = 12
const TAG_BYTES = 16

/** 서버와 기기에 저장되는 암호화 볼트. 평문 헤더(version, cipher)는 AAD로 인증된다 */
export interface EncryptedVaultPayload {
  readonly version: typeof VERSION
  readonly cipher: typeof CIPHER
  readonly iv: string
  readonly ciphertext: string
  readonly tag: string
}

/** 키가 다르거나(다른 마스터 비밀번호·계정) 볼트가 변조되어 인증 태그 검증에 실패함 */
export class VaultDecryptError extends Error {
  constructor(message = '마스터 키가 다르거나 볼트가 변조됨') {
    super(message)
    this.name = 'VaultDecryptError'
  }
}

/** 이 앱이 모르는 형식(version·cipher)의 볼트. 복호화를 시도하지 않는다 */
export class UnsupportedPayloadError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'UnsupportedPayloadError'
  }
}

const encoder = new TextEncoder()
const aadOf = (version: number, cipher: string) => encoder.encode(`keymanage-vault|${version}|${cipher}`)

/** iv는 테스트에서만 넘긴다. 실제로는 암호화할 때마다 새 IV를 만든다 */
export async function sealVault(
  key: CryptoKey,
  plaintext: string,
  iv: Uint8Array<ArrayBuffer> = globalThis.crypto.getRandomValues(new Uint8Array(IV_BYTES)),
): Promise<EncryptedVaultPayload> {
  if (iv.length !== IV_BYTES) throw new RangeError(`IV는 ${IV_BYTES}바이트여야 함`)
  const sealed = new Uint8Array(
    await globalThis.crypto.subtle.encrypt(
      { name: 'AES-GCM', iv, additionalData: aadOf(VERSION, CIPHER), tagLength: TAG_BYTES * 8 },
      key,
      encoder.encode(plaintext),
    ),
  )
  // WebCrypto는 암호문 뒤에 태그를 붙여 돌려주므로 나눠 담는다
  return {
    version: VERSION,
    cipher: CIPHER,
    iv: toBase64(iv),
    ciphertext: toBase64(sealed.subarray(0, sealed.length - TAG_BYTES)),
    tag: toBase64(sealed.subarray(sealed.length - TAG_BYTES)),
  }
}

/**
 * @throws UnsupportedPayloadError 모르는 version·cipher일 때
 * @throws VaultDecryptError 키가 다르거나, 헤더·암호문이 변조됐거나, IV·태그 형식이 틀릴 때
 */
export async function openVault(key: CryptoKey, payload: EncryptedVaultPayload): Promise<string> {
  if (payload.version !== VERSION || payload.cipher !== CIPHER) {
    throw new UnsupportedPayloadError(`지원하지 않는 볼트 형식: version ${payload.version}, ${payload.cipher}`)
  }
  let iv: Uint8Array<ArrayBuffer>
  let sealed: Uint8Array<ArrayBuffer>
  try {
    iv = fromBase64(payload.iv)
    const ciphertext = fromBase64(payload.ciphertext)
    const tag = fromBase64(payload.tag)
    if (iv.length !== IV_BYTES || tag.length !== TAG_BYTES) throw new VaultDecryptError('IV 또는 태그 길이가 틀림')
    sealed = new Uint8Array(ciphertext.length + TAG_BYTES)
    sealed.set(ciphertext)
    sealed.set(tag, ciphertext.length)
  } catch (error) {
    throw error instanceof VaultDecryptError ? error : new VaultDecryptError('Base64 형식이 틀림')
  }

  let plaintext: ArrayBuffer
  try {
    plaintext = await globalThis.crypto.subtle.decrypt(
      { name: 'AES-GCM', iv, additionalData: aadOf(payload.version, payload.cipher), tagLength: TAG_BYTES * 8 },
      key,
      sealed,
    )
  } catch {
    throw new VaultDecryptError()
  }
  return new TextDecoder().decode(plaintext)
}
