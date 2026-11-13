// @vitest-environment node
// 계정 삭제 요청을 만들고 인증 해시 바이트를 남기지 않는다 (SEC-005, api-spec 2.5.6)
import { describe, expect, it } from 'vitest'
import { FakeAuthApi } from '../../test/fakeServerApis'
import { toBase64 } from '../crypto/base64'
import { ServerError } from '../server/serverErrors'
import { createAuthService } from './authService'

describe('계정 삭제 요청 (api-spec 2.5.6)', () => {
  it('토큰을 붙여 인증 해시를 Base64로 보낸다', async () => {
    const authApi = new FakeAuthApi()
    authApi.authHash = toBase64(new Uint8Array(32).fill(1))

    await createAuthService(authApi).deleteAccount('token-1', new Uint8Array(32).fill(1))

    expect(authApi.deletions).toEqual([{ accessToken: 'token-1', request: { authHash: toBase64(new Uint8Array(32).fill(1)) } }])
  })

  it('성공하면 인증 해시 바이트를 0으로 지운다', async () => {
    const authHash = new Uint8Array(32).fill(1)

    await createAuthService(new FakeAuthApi()).deleteAccount('token-1', authHash)

    expect(authHash.every((byte) => byte === 0)).toBe(true)
  })

  it('실패해도 인증 해시 바이트를 0으로 지우고 서버 오류를 그대로 알린다', async () => {
    const authApi = new FakeAuthApi()
    authApi.deleteError = new ServerError(401, 'AUTH_FAILED')
    const authHash = new Uint8Array(32).fill(1)

    await expect(createAuthService(authApi).deleteAccount('token-1', authHash)).rejects.toEqual(new ServerError(401, 'AUTH_FAILED'))

    expect(authHash.every((byte) => byte === 0)).toBe(true)
  })
})
