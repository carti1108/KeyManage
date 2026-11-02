// @vitest-environment node
// 마스터 비밀번호 변경 요청을 만들고 인증 해시 바이트를 남기지 않는다 (SEC-004, api-spec 2.5.5)
import { describe, expect, it } from 'vitest'
import { FakeAuthApi, FakeVaultApi } from '../../test/sessionFixtures'
import { toBase64 } from '../crypto/base64'
import type { EncryptedVaultPayload } from '../crypto/vaultCipher'
import { ServerError } from '../server/serverErrors'
import { createAuthService } from './authService'

const payload: EncryptedVaultPayload = { version: 1, cipher: 'AES-256-GCM', iv: 'aXY=', ciphertext: 'Y3Q=', tag: 'dGFn' }
const kdf = { salt: toBase64(new Uint8Array(16).fill(4)), iterations: 600_000 }

function api() {
  const authApi = new FakeAuthApi()
  authApi.vaultApi = new FakeVaultApi()
  authApi.vaultApi.revision = 3
  return authApi
}

describe('비밀번호 변경 요청 (api-spec 2.5.5)', () => {
  it('인증 해시를 Base64로 보내고 새 토큰과 revision을 돌려준다', async () => {
    const authApi = api()
    authApi.authHash = toBase64(new Uint8Array(32).fill(1))

    const changed = await createAuthService(authApi).changePassword('token-1', new Uint8Array(32).fill(1), new Uint8Array(32).fill(2), kdf, { expectedRevision: 3, payload })

    expect(changed).toEqual(expect.objectContaining({ accessToken: 'token-2', revision: 4 }))
    expect(authApi.changes[0]).toEqual({
      accessToken: 'token-1',
      request: { currentAuthHash: toBase64(new Uint8Array(32).fill(1)), newAuthHash: toBase64(new Uint8Array(32).fill(2)), kdf, vault: { expectedRevision: 3, payload } },
    })
  })

  it('성공하면 두 인증 해시 바이트를 0으로 지운다', async () => {
    const current = new Uint8Array(32).fill(1)
    const next = new Uint8Array(32).fill(2)

    await createAuthService(api()).changePassword('token-1', current, next, kdf, { expectedRevision: 3, payload })

    expect([...current, ...next].every((byte) => byte === 0)).toBe(true)
  })

  it('실패해도 두 인증 해시 바이트를 0으로 지우고 서버 오류를 그대로 알린다', async () => {
    const authApi = api()
    authApi.changeError = new ServerError(401, 'AUTH_FAILED')
    const current = new Uint8Array(32).fill(1)
    const next = new Uint8Array(32).fill(2)

    await expect(createAuthService(authApi).changePassword('token-1', current, next, kdf, { expectedRevision: 3, payload })).rejects.toEqual(
      new ServerError(401, 'AUTH_FAILED'),
    )

    expect([...current, ...next].every((byte) => byte === 0)).toBe(true)
  })
})
