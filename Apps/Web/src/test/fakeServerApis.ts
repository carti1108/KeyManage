// 인증 서비스 테스트가 함께 쓰는 가짜 서버. 세션 로직은 Kotlin으로 옮겨져 세션용 준비 함수는 없다
import type { AuthApi, ChangePasswordRequest, ChangedPassword, DeleteAccountRequest, KdfParams, LoginRequest, RegisterRequest, Session } from '../domain/auth/authApi'
import { vectors } from '../domain/crypto/testVectors'
import type { EncryptedVaultPayload } from '../domain/crypto/vaultCipher'
import { NetworkError, ServerError } from '../domain/server/serverErrors'
import type { RemoteVault, VaultApi } from '../domain/vault/vaultApi'

const vector = vectors.keyDerivationCases[0]

export class FakeAuthApi implements AuthApi {
  registered: RegisterRequest[] = []
  loggedOut: string[] = []
  kdf: KdfParams = { kdf: 'PBKDF2-HMAC-SHA256', iterations: vectors.kdf.iterations, salt: vector.salt }
  logins: LoginRequest[] = []
  offline = false
  /** 비밀번호 변경이 함께 교체할 같은 서버의 볼트. 테스트가 넣는다 */
  vaultApi: FakeVaultApi | null = null
  /** 서버가 아는 인증 해시. 가입·로그인 요청의 값을 따르고, 비밀번호 변경은 이 값과 비교한다 */
  authHash: string | null = null
  changes: { accessToken: string; request: ChangePasswordRequest }[] = []
  /** 비밀번호 변경으로 폐기된 토큰 */
  revoked = new Set<string>()
  /** 설정하면 비밀번호 변경을 이 오류로 거절한다 */
  changeError: unknown = null
  /** 설정하면 비밀번호 변경 요청이 이 약속이 풀릴 때까지 기다린다 */
  changeGate: Promise<void> | null = null
  deletions: { accessToken: string; request: DeleteAccountRequest }[] = []
  /** 설정하면 계정 삭제를 이 오류로 거절한다 */
  deleteError: unknown = null
  /** 설정하면 계정 삭제 요청이 이 약속이 풀릴 때까지 기다린다 */
  deleteGate: Promise<void> | null = null
  /** 계정을 지웠음. 지운 계정으로는 로그인할 수 없고 prelogin은 가짜 salt를 준다 */
  deleted = false
  private issued = 1

  private token(): string {
    return `token-${this.issued}`
  }

  async prelogin(): Promise<KdfParams> {
    if (this.offline) throw new NetworkError()
    return this.kdf
  }
  async register(request: RegisterRequest): Promise<Session> {
    this.registered.push(request)
    this.deleted = false
    this.kdf = { kdf: 'PBKDF2-HMAC-SHA256', iterations: request.kdfIterations, salt: request.kdfSalt }
    this.authHash = request.authHash
    return { accessToken: this.token(), expiresAt: '2026-11-30T00:00:00Z' }
  }
  async login(request: LoginRequest): Promise<Session> {
    if (this.offline) throw new NetworkError()
    this.logins.push(request)
    if (this.deleted) throw new ServerError(401, 'AUTH_FAILED')
    this.authHash = request.authHash
    return { accessToken: this.token(), expiresAt: '2026-11-30T00:00:00Z' }
  }
  async logout(accessToken: string) {
    this.loggedOut.push(accessToken)
  }
  /** 실제 서버처럼 현재 인증 해시와 revision을 확인한 뒤 KDF 값·인증 해시·볼트를 함께 바꾸고 이전 토큰을 모두 폐기한다 */
  async changePassword(accessToken: string, request: ChangePasswordRequest): Promise<ChangedPassword> {
    if (this.offline) throw new NetworkError()
    this.changes.push({ accessToken, request })
    if (this.changeGate) await this.changeGate
    if (this.changeError !== null) throw this.changeError
    if (this.revoked.has(accessToken)) throw new ServerError(401, 'UNAUTHORIZED')
    if (this.authHash !== null && request.currentAuthHash !== this.authHash) throw new ServerError(401, 'AUTH_FAILED')
    const vault = this.vaultApi
    if (vault === null) throw new Error('비밀번호 변경에 쓸 볼트 API가 없음')
    if (request.vault.expectedRevision !== vault.revision) throw new ServerError(409, 'VAULT_CONFLICT')
    vault.payload = request.vault.payload
    vault.revision += 1
    this.kdf = { kdf: 'PBKDF2-HMAC-SHA256', iterations: request.kdf.iterations, salt: request.kdf.salt }
    this.authHash = request.newAuthHash
    for (let n = 1; n <= this.issued; n += 1) this.revoked.add(`token-${n}`)
    this.issued += 1
    return { accessToken: this.token(), expiresAt: '2026-12-02T00:00:00Z', revision: vault.revision }
  }
  /** 실제 서버처럼 인증 해시를 확인한 뒤 계정·볼트를 지우고 이 계정의 토큰을 모두 폐기한다 (api-spec 2.5.6) */
  async deleteAccount(accessToken: string, request: DeleteAccountRequest): Promise<void> {
    if (this.offline) throw new NetworkError()
    this.deletions.push({ accessToken, request })
    if (this.deleteGate) await this.deleteGate
    if (this.deleteError !== null) throw this.deleteError
    if (this.deleted || this.revoked.has(accessToken)) throw new ServerError(401, 'UNAUTHORIZED')
    if (this.authHash !== null && request.authHash !== this.authHash) throw new ServerError(401, 'AUTH_FAILED')
    this.deleted = true
    this.authHash = null
    // 없는 이메일의 prelogin은 서버 비밀키로 만든 가짜 salt를 준다 (api-spec 2.5.1)
    this.kdf = { ...this.kdf, salt: 'ZmFrZS1zYWx0LTE2Ynl0ZQ==' }
    for (let n = 1; n <= this.issued; n += 1) this.revoked.add(`token-${n}`)
    // 다시 가입하면 폐기되지 않은 새 토큰을 받는다
    this.issued += 1
    if (this.vaultApi !== null) {
      this.vaultApi.payload = null
      this.vaultApi.revision = 0
    }
  }
}

export class FakeVaultApi implements VaultApi {
  revision = 0
  payload: EncryptedVaultPayload | null = null
  saves = 0
  offline = false
  /** 설정하면 저장 요청이 이 약속이 풀릴 때까지 기다린다 */
  gate: Promise<void> | null = null

  async get(): Promise<RemoteVault | null> {
    if (this.offline) throw new NetworkError()
    return this.payload === null ? null : { revision: this.revision, payload: this.payload }
  }
  async save(expectedRevision: number, payload: EncryptedVaultPayload): Promise<number> {
    if (this.offline) throw new NetworkError()
    if (this.gate) await this.gate
    this.saves += 1
    if (expectedRevision !== this.revision) throw new ServerError(409, 'VAULT_CONFLICT')
    this.payload = payload
    this.revision += 1
    return this.revision
  }
}
