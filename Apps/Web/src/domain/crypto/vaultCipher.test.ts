// @vitest-environment node
import { beforeAll, describe, expect, it } from 'vitest'
import { InvalidBase64Error, fromBase64, toBase64 } from './base64'
import { keysOf, vectors } from './testVectors'
import {
  UnsupportedPayloadError,
  VaultDecryptError,
  openVault,
  sealVault,
  sealVaultWithFixedIvForTest,
  type EncryptedVaultPayload,
} from './vaultCipher'

const vector = vectors.encryptionCases[0]
const payload = vector.payload as EncryptedVaultPayload

describe('볼트 암호화·복호화 (vault-spec 3장, ADR-0011)', () => {
  let key: CryptoKey
  let otherKey: CryptoKey

  beforeAll(async () => {
    key = (await keysOf(vector.keyFrom)).encryptionKey
    otherKey = (await keysOf('korean-emoji')).encryptionKey
  })

  it('고정 IV로 암호화하면 테스트 벡터와 같은 페이로드가 나온다', async () => {
    expect(await sealVaultWithFixedIvForTest(key, vector.plaintext, fromBase64(payload.iv))).toEqual(vector.payload)
  })

  it('테스트 벡터 페이로드를 복호화하면 원래 볼트가 나온다', async () => {
    expect(await openVault(key, payload)).toBe(vector.plaintext)
  })

  it('암호화할 때마다 새 12바이트 IV를 만든다', async () => {
    const first = await sealVault(key, 'same')
    const second = await sealVault(key, 'same')

    expect(fromBase64(first.iv)).toHaveLength(12)
    expect(first.iv).not.toBe(second.iv)
    expect(await openVault(key, first)).toBe('same')
  })

  it('다른 계정의 키로는 열 수 없다', async () => {
    await expect(openVault(otherKey, payload)).rejects.toBeInstanceOf(VaultDecryptError)
  })

  it('암호문을 한 바이트라도 바꾸면 열 수 없다', async () => {
    const bytes = fromBase64(payload.ciphertext)
    bytes[0] ^= 1

    await expect(openVault(key, { ...payload, ciphertext: toBase64(bytes) })).rejects.toBeInstanceOf(VaultDecryptError)
  })

  it('지원하지 않는 version이나 cipher는 복호화하지 않고 거절한다', async () => {
    const wrongVersion = { ...payload, version: 2 } as unknown as EncryptedVaultPayload
    const wrongCipher = { ...payload, cipher: 'AES-128-GCM' } as unknown as EncryptedVaultPayload

    await expect(openVault(key, wrongVersion)).rejects.toBeInstanceOf(UnsupportedPayloadError)
    await expect(openVault(key, wrongCipher)).rejects.toBeInstanceOf(UnsupportedPayloadError)
  })

  it('필드가 문자열이 아니면 키 문제가 아니라 형식 오류로 거절한다', async () => {
    const nullCiphertext = { ...payload, ciphertext: null } as unknown as EncryptedVaultPayload
    const numberIv = { ...payload, iv: 12345678 } as unknown as EncryptedVaultPayload

    await expect(openVault(key, nullCiphertext)).rejects.toBeInstanceOf(UnsupportedPayloadError)
    await expect(openVault(key, numberIv)).rejects.toBeInstanceOf(UnsupportedPayloadError)
  })

  it('IV가 12바이트가 아니거나 태그가 16바이트가 아니면 열 수 없다', async () => {
    await expect(openVault(key, { ...payload, iv: toBase64(new Uint8Array(8)) })).rejects.toBeInstanceOf(VaultDecryptError)
    await expect(openVault(key, { ...payload, tag: toBase64(new Uint8Array(8)) })).rejects.toBeInstanceOf(VaultDecryptError)
  })

  it('표준이 아닌 Base64(공백, 패딩 누락)는 형식 오류로 거절한다', async () => {
    await expect(openVault(key, { ...payload, iv: `${payload.iv}\n` })).rejects.toBeInstanceOf(UnsupportedPayloadError)
    await expect(openVault(key, { ...payload, tag: payload.tag.replace(/=+$/, '') })).rejects.toBeInstanceOf(UnsupportedPayloadError)
  })
})

describe('Base64 (RFC 4648 표준 형식)', () => {
  it('큰 데이터도 그대로 왕복한다', () => {
    const bytes = new Uint8Array(200_000).map((_, i) => i % 256)

    expect(fromBase64(toBase64(bytes))).toEqual(bytes)
  })

  it('표준이 아닌 입력은 거절한다', () => {
    for (const bad of ['AAA', 'AA A=', 'AA==\n', 'A-_=', 'A===']) {
      expect(() => fromBase64(bad), bad).toThrow(InvalidBase64Error)
    }
  })
})
