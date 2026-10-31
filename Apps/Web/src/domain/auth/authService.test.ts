// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { fromBase64 } from '../crypto/base64'
import { InvalidKdfParamsError, MAX_ITERATIONS, MIN_ITERATIONS } from '../crypto/keys'
import { vectors } from '../crypto/testVectors'
import { openVault, sealVault } from '../crypto/vaultCipher'
import type { AuthApi, KdfParams, LoginRequest, RegisterRequest, Session } from './authApi'
import { InvalidEmailError, createAuthService, normalizeEmail } from './authService'

const session: Session = { accessToken: 'token-1', expiresAt: '2026-11-28T00:00:00Z' }
const vector = vectors.keyDerivationCases[0]

// 서버 대신 요청을 기록하고 정해 둔 응답을 돌려준다
class FakeAuthApi implements AuthApi {
  preloginEmails: string[] = []
  registered: RegisterRequest[] = []
  logins: LoginRequest[] = []
  loggedOut: string[] = []

  private readonly kdf: KdfParams

  constructor(kdf: KdfParams = { kdf: 'PBKDF2-HMAC-SHA256', iterations: vectors.kdf.iterations, salt: vector.salt }) {
    this.kdf = kdf
  }

  async prelogin(email: string) {
    this.preloginEmails.push(email)
    return this.kdf
  }
  async register(request: RegisterRequest) {
    this.registered.push(request)
    return session
  }
  async login(request: LoginRequest) {
    this.logins.push(request)
    return session
  }
  async logout(accessToken: string) {
    this.loggedOut.push(accessToken)
  }
}

describe('이메일 정규화 (api-spec 2.5)', () => {
  it('앞뒤 공백을 지우고 소문자로 맞춘다', () => {
    expect(normalizeEmail('  User@Example.COM ')).toBe('user@example.com')
  })

  it('비어 있거나 254자를 넘거나 @가 없으면 거절한다', () => {
    for (const bad of ['', '   ', 'no-at-sign', 'a@b', '@', 'a b@c.d', 'a@@b.cd', `${'a'.repeat(250)}@b.cd`]) {
      expect(() => normalizeEmail(bad), bad).toThrow(InvalidEmailError)
    }
    expect(normalizeEmail(`${'a'.repeat(249)}@b.cd`)).toHaveLength(254)
  })
})

describe('로그인 (SEC-002)', () => {
  it('prelogin으로 받은 값으로 키를 유도해 테스트 벡터와 같은 인증 해시를 보낸다', async () => {
    const api = new FakeAuthApi()

    const result = await createAuthService(api).login(' User@Example.com ', vector.password)

    expect(api.preloginEmails).toEqual(['user@example.com'])
    expect(api.logins).toEqual([{ email: 'user@example.com', authHash: vector.authHash }])
    expect(result.session).toEqual(session)
    // 잠금 해제 때 서버 없이 키를 다시 만들 수 있도록 KDF 값을 돌려준다 (SEC-003)
    expect(result.kdf).toEqual({ salt: vector.salt, iterations: vectors.kdf.iterations })
  })

  it('돌려받은 암호화 키로 볼트를 암·복호화할 수 있다', async () => {
    const { encryptionKey } = await createAuthService(new FakeAuthApi()).login('a@b.cd', vector.password)

    expect(await openVault(encryptionKey, await sealVault(encryptionKey, '{}'))).toBe('{}')
  })

  it('서버가 규격보다 약하거나 지나친 반복 횟수를 주면 로그인 요청을 보내지 않는다', async () => {
    for (const iterations of [MIN_ITERATIONS - 1, MAX_ITERATIONS + 1]) {
      const api = new FakeAuthApi({ kdf: 'PBKDF2-HMAC-SHA256', iterations, salt: vector.salt })

      await expect(createAuthService(api).login('a@b.cd', 'pw')).rejects.toBeInstanceOf(InvalidKdfParamsError)
      expect(api.logins).toEqual([])
    }
  })

  it('모르는 KDF나 표준이 아닌 salt를 주면 로그인 요청을 보내지 않는다', async () => {
    const wrongKdf = new FakeAuthApi({ kdf: 'argon2id' as KdfParams['kdf'], iterations: MIN_ITERATIONS, salt: vector.salt })
    const brokenSalt = new FakeAuthApi({ kdf: 'PBKDF2-HMAC-SHA256', iterations: MIN_ITERATIONS, salt: 'not base64!' })

    for (const api of [wrongKdf, brokenSalt]) {
      await expect(createAuthService(api).login('a@b.cd', 'pw')).rejects.toBeInstanceOf(InvalidKdfParamsError)
      expect(api.logins).toEqual([])
    }
  })
})

describe('가입 (SEC-002)', () => {
  it('새 16바이트 salt와 600,000회로 등록하고, 같은 비밀번호로 로그인하면 같은 인증 해시가 나온다', async () => {
    const api = new FakeAuthApi()
    const service = createAuthService(api)

    const registered = await service.register('New@Example.com', 'correct horse')
    const [request] = api.registered

    expect(request.email).toBe('new@example.com')
    expect(fromBase64(request.kdfSalt)).toHaveLength(16)
    expect(request.kdfIterations).toBe(MIN_ITERATIONS)
    expect(fromBase64(request.authHash)).toHaveLength(32)
    expect(registered.kdf).toEqual({ salt: request.kdfSalt, iterations: MIN_ITERATIONS })

    const loginApi = new FakeAuthApi({ kdf: 'PBKDF2-HMAC-SHA256', iterations: request.kdfIterations, salt: request.kdfSalt })
    await createAuthService(loginApi).login('new@example.com', 'correct horse')
    expect(loginApi.logins[0].authHash).toBe(request.authHash)
  })

  it('가입할 때마다 salt가 다르다', async () => {
    const api = new FakeAuthApi()
    const service = createAuthService(api)

    await service.register('a@b.cd', 'pw')
    await service.register('a@b.cd', 'pw')

    expect(api.registered[0].kdfSalt).not.toBe(api.registered[1].kdfSalt)
  })
})

describe('로그아웃', () => {
  it('받은 토큰을 서버에 폐기 요청한다', async () => {
    const api = new FakeAuthApi()

    await createAuthService(api).logout('token-1')

    expect(api.loggedOut).toEqual(['token-1'])
  })
})
