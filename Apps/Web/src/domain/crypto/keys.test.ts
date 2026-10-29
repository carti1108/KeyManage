// @vitest-environment node
// 암호는 브라우저 DOM이 아닌 WebCrypto만 쓰므로 Node 환경에서 검증한다 (데스크톱 웹뷰·브라우저와 같은 API)
import { describe, expect, it } from 'vitest'
import { fromBase64, toBase64 } from './base64'
import {
  InvalidKdfParamsError,
  InvalidPasswordError,
  MAX_ITERATIONS,
  MIN_ITERATIONS,
  deriveKeys,
  preprocessPassword,
} from './keys'
import { keysOf, vectors } from './testVectors'

const utf8 = (text: string) => new TextEncoder().encode(text)

// 같은 키인지는 꺼낼 수 없는 키 대신, 벡터의 키 원문으로 같은 평문·IV를 암호화한 결과가 같은지로 확인한다
async function sameKey(key: CryptoKey, rawKeyBase64: string): Promise<boolean> {
  const iv = new Uint8Array(12)
  const probe = utf8('keymanage-key-probe')
  const expected = await globalThis.crypto.subtle.importKey('raw', fromBase64(rawKeyBase64), 'AES-GCM', false, ['encrypt'])
  const a = new Uint8Array(await globalThis.crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, probe))
  const b = new Uint8Array(await globalThis.crypto.subtle.encrypt({ name: 'AES-GCM', iv }, expected, probe))
  return toBase64(a) === toBase64(b)
}

describe('마스터 비밀번호 전처리 (ADR-0011)', () => {
  for (const c of vectors.keyDerivationCases) {
    it(`${c.name}: ${c.description}`, () => {
      expect(toBase64(utf8(preprocessPassword(c.password)))).toBe(c.normalizedPasswordUtf8)
    })
  }

  it('중간 공백과 지정하지 않은 공백 문자(U+2003)는 남긴다', () => {
    expect(preprocessPassword(' a b ')).toBe(' a b ')
  })

  it('짝이 맞지 않는 서로게이트가 있으면 거절한다', () => {
    expect(() => preprocessPassword('ab\uD83Dcd')).toThrow(InvalidPasswordError)
    expect(() => preprocessPassword('\uDD11ab')).toThrow(InvalidPasswordError)
    expect(preprocessPassword('🔑')).toBe('🔑')
  })
})

describe('키 유도 (vault-spec 2장)', () => {
  for (const c of vectors.keyDerivationCases) {
    it(`${c.name}: 인증 해시와 암호화 키가 테스트 벡터와 같다`, async () => {
      const keys = await keysOf(c.name)

      expect(toBase64(keys.authHash)).toBe(c.authHash)
      expect(await sameKey(keys.encryptionKey, c.encryptionKey)).toBe(true)
    })
  }

  it('암호화 키는 밖으로 꺼낼 수 없다', async () => {
    const keys = await keysOf('ascii-passphrase')

    expect(keys.encryptionKey.extractable).toBe(false)
    await expect(globalThis.crypto.subtle.exportKey('raw', keys.encryptionKey)).rejects.toThrow()
  })

  it('서버가 600,000보다 적은 반복 횟수를 주면 거절한다', async () => {
    await expect(deriveKeys('pw', new Uint8Array(16), MIN_ITERATIONS - 1)).rejects.toBeInstanceOf(InvalidKdfParamsError)
  })

  it('서버가 상한보다 많은 반복 횟수를 주면 계산하지 않고 거절한다', async () => {
    await expect(deriveKeys('pw', new Uint8Array(16), MAX_ITERATIONS + 1)).rejects.toBeInstanceOf(InvalidKdfParamsError)
  })

  it('salt가 16바이트가 아니면 거절한다', async () => {
    await expect(deriveKeys('pw', new Uint8Array(15), MIN_ITERATIONS)).rejects.toBeInstanceOf(InvalidKdfParamsError)
  })
})
