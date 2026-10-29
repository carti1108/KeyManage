// 계정 인증 서버 포트 (api-spec 2.5). 구현은 adapters가 맡고, 실패는 serverErrors의 오류로 알린다

export interface KdfParams {
  readonly kdf: 'PBKDF2-HMAC-SHA256'
  readonly iterations: number
  /** Standard Base64, 16바이트 */
  readonly salt: string
}

export interface Session {
  readonly accessToken: string
  /** ISO 8601 */
  readonly expiresAt: string
}

export interface RegisterRequest {
  readonly email: string
  readonly authHash: string
  readonly kdfSalt: string
  readonly kdfIterations: number
}

export interface LoginRequest {
  readonly email: string
  readonly authHash: string
}

export interface AuthApi {
  prelogin(email: string): Promise<KdfParams>
  register(request: RegisterRequest): Promise<Session>
  login(request: LoginRequest): Promise<Session>
  logout(accessToken: string): Promise<void>
}
