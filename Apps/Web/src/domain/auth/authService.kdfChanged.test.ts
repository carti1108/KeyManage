// @vitest-environment node
// 잠금 해제 실패 뒤 서버의 KDF 값만 확인한다. 비밀번호·인증 해시는 보내지 않는다 (SEC-003 규칙 11)
import { describe, expect, it } from 'vitest'
import { FakeAuthApi } from '../../test/fakeServerApis'
import { NetworkError } from '../server/serverErrors'
import { createAuthService } from './authService'

describe('서버 KDF 값 변경 확인', () => {
  it('prelogin의 salt·반복 횟수가 기기 값과 같으면 false, 다르면 true이고 로그인 요청은 보내지 않는다', async () => {
    const api = new FakeAuthApi()
    const auth = createAuthService(api)
    const same = { salt: api.kdf.salt, iterations: api.kdf.iterations }

    expect(await auth.kdfChanged('user@example.com', same)).toBe(false)
    expect(await auth.kdfChanged('user@example.com', { ...same, salt: 'AAAAAAAAAAAAAAAAAAAAAA==' })).toBe(true)
    expect(await auth.kdfChanged('user@example.com', { ...same, iterations: same.iterations + 1 })).toBe(true)
    expect(api.logins).toHaveLength(0)
  })

  it('서버에 닿지 못하면 오류를 그대로 알린다', async () => {
    const api = new FakeAuthApi()
    api.offline = true

    await expect(createAuthService(api).kdfChanged('user@example.com', { salt: api.kdf.salt, iterations: api.kdf.iterations })).rejects.toBeInstanceOf(NetworkError)
  })
})
