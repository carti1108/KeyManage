// 계정 인증 서버 포트 (api-spec 2.5). 구현은 adapters가 맡고, 실패는 serverErrors의 오류로 알린다
import type { EncryptedVaultPayload } from '../crypto/vaultCipher'

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

/** 마스터 비밀번호 변경 요청. 인증 해시와 salt는 Standard Base64다 (api-spec 2.5.5, SEC-004) */
export interface ChangePasswordRequest {
  readonly currentAuthHash: string
  readonly newAuthHash: string
  readonly kdf: { readonly iterations: number; readonly salt: string }
  readonly vault: { readonly expectedRevision: number; readonly payload: EncryptedVaultPayload }
}

/** 비밀번호를 바꾼 기기에만 주는 새 세션과 교체한 볼트의 revision */
export interface ChangedPassword extends Session {
  readonly revision: number
}

/** 계정 삭제 요청. 인증 해시는 Standard Base64다 (api-spec 2.5.6, SEC-005) */
export interface DeleteAccountRequest {
  readonly authHash: string
}

export interface AuthApi {
  prelogin(email: string): Promise<KdfParams>
  register(request: RegisterRequest): Promise<Session>
  login(request: LoginRequest): Promise<Session>
  logout(accessToken: string): Promise<void>
  /**
   * KDF 값·인증 해시·볼트를 한 번에 교체한다. 성공하면 이 계정의 다른 토큰은 모두 폐기된다
   * @throws ServerError 401 AUTH_FAILED(현재 인증 해시가 틀림), 409 VAULT_CONFLICT, 413, 429, 401 UNAUTHORIZED
   */
  changePassword(accessToken: string, request: ChangePasswordRequest): Promise<ChangedPassword>
  /**
   * 계정·볼트·세션을 지운다. 성공하면 이 계정의 모든 토큰이 무효다
   * @throws ServerError 401 AUTH_FAILED(인증 해시가 틀림), 401 UNAUTHORIZED, 429
   */
  deleteAccount(accessToken: string, request: DeleteAccountRequest): Promise<void>
}
