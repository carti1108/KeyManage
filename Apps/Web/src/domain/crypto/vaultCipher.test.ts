// @vitest-environment node
import { beforeAll, describe, expect, it } from 'vitest'
import vectors from '../../../../../docs/crypto-test-vectors.json'
import { fromBase64, toBase64 } from './base64'
import { deriveKeys } from './keys'
import { UnsupportedPayloadError, VaultDecryptError, openVault, sealVault, type EncryptedVaultPayload } from './vaultCipher'

const vector = vectors.encryptionCases[0]

async function keyOf(name: string): Promise<CryptoKey> {
  const c = vectors.keyDerivationCases.find((k) => k.name === name)!
  return (await deriveKeys(c.password, fromBase64(c.salt), vectors.kdf.iterations)).encryptionKey
}

describe('볼트 암호화·복호화 (vault-spec 3장, ADR-0011)', () => {
  let key: CryptoKey
  let otherKey: CryptoKey

  beforeAll(async () => {
    key = await keyOf(vector.keyFrom)
    otherKey = await keyOf('korean-emoji')
  })

  it('고정 IV로 암호화하면 테스트 벡터와 같은 페이로드가 나온다', async () => {
    const payload = await sealVault(key, vector.plaintext, fromBase64(vector.payload.iv))

    expect(payload).toEqual(vector.payload)
  })

  it('테스트 벡터 페이로드를 복호화하면 원래 볼트가 나온다', async () => {
    expect(await openVault(key, vector.payload as EncryptedVaultPayload)).toBe(vector.plaintext)
  })

  it('IV를 주지 않으면 매번 새 12바이트 IV를 만든다', async () => {
    const first = await sealVault(key, 'same')
    const second = await sealVault(key, 'same')

    expect(fromBase64(first.iv)).toHaveLength(12)
    expect(first.iv).not.toBe(second.iv)
    expect(await openVault(key, first)).toBe('same')
  })

  it('다른 계정의 키로는 열 수 없다', async () => {
    await expect(openVault(otherKey, vector.payload as EncryptedVaultPayload)).rejects.toBeInstanceOf(VaultDecryptError)
  })

  it('암호문을 한 바이트라도 바꾸면 열 수 없다', async () => {
    const bytes = fromBase64(vector.payload.ciphertext)
    bytes[0] ^= 1
    const tampered = { ...vector.payload, ciphertext: toBase64(bytes) } as EncryptedVaultPayload

    await expect(openVault(key, tampered)).rejects.toBeInstanceOf(VaultDecryptError)
  })

  it('지원하지 않는 version이나 cipher는 복호화하지 않고 거절한다', async () => {
    const wrongVersion = { ...vector.payload, version: 2 } as unknown as EncryptedVaultPayload
    const wrongCipher = { ...vector.payload, cipher: 'AES-128-GCM' } as unknown as EncryptedVaultPayload

    await expect(openVault(key, wrongVersion)).rejects.toBeInstanceOf(UnsupportedPayloadError)
    await expect(openVault(key, wrongCipher)).rejects.toBeInstanceOf(UnsupportedPayloadError)
  })

  it('IV가 12바이트가 아니거나 태그가 16바이트가 아니면 열 수 없다', async () => {
    const shortIv = { ...vector.payload, iv: toBase64(new Uint8Array(8)) } as EncryptedVaultPayload
    const shortTag = { ...vector.payload, tag: toBase64(new Uint8Array(8)) } as EncryptedVaultPayload

    await expect(openVault(key, shortIv)).rejects.toBeInstanceOf(VaultDecryptError)
    await expect(openVault(key, shortTag)).rejects.toBeInstanceOf(VaultDecryptError)
  })
})
