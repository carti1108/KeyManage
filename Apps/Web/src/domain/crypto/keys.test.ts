// @vitest-environment node
// 암호는 브라우저 DOM이 아닌 WebCrypto만 쓰므로 Node 환경에서 검증한다 (데스크톱 웹뷰·브라우저와 같은 API)
import { describe, expect, it } from 'vitest'
import vectors from '../../../../../docs/crypto-test-vectors.json'
import { fromBase64, toBase64 } from './base64'
import { InvalidKdfParamsError, MIN_ITERATIONS, deriveKeys, preprocessPassword } from './keys'

const utf8 = (text: string) => new TextEncoder().encode(text)

describe('마스터 비밀번호 전처리 (ADR-0011)', () => {
  for (const c of vectors.keyDerivationCases) {
    it(`${c.name}: ${c.description}`, () => {
      expect(toBase64(utf8(preprocessPassword(c.password)))).toBe(c.normalizedPasswordUtf8)
    })
  }

  it('중간 공백과 지정하지 않은 공백 문자(U+2003)는 남긴다', () => {
    expect(preprocessPassword(' a b ')).toBe(' a b ')
  })
})

describe('키 유도 (vault-spec 2장)', () => {
  for (const c of vectors.keyDerivationCases) {
    it(`${c.name}: 인증 해시가 테스트 벡터와 같다`, async () => {
      const keys = await deriveKeys(c.password, fromBase64(c.salt), vectors.kdf.iterations)

      expect(toBase64(keys.authHash)).toBe(c.authHash)
    })
  }

  it('암호화 키는 밖으로 꺼낼 수 없다', async () => {
    const c = vectors.keyDerivationCases[0]
    const keys = await deriveKeys(c.password, fromBase64(c.salt), MIN_ITERATIONS)

    expect(keys.encryptionKey.extractable).toBe(false)
    await expect(globalThis.crypto.subtle.exportKey('raw', keys.encryptionKey)).rejects.toThrow()
  })

  it('서버가 600,000보다 적은 반복 횟수를 주면 거절한다', async () => {
    await expect(deriveKeys('pw', new Uint8Array(16), MIN_ITERATIONS - 1)).rejects.toBeInstanceOf(InvalidKdfParamsError)
  })

  it('salt가 16바이트가 아니면 거절한다', async () => {
    await expect(deriveKeys('pw', new Uint8Array(15), MIN_ITERATIONS)).rejects.toBeInstanceOf(InvalidKdfParamsError)
  })
})
