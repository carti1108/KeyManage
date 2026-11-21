package com.keymanage.domain.auth

import com.keymanage.domain.crypto.EncryptedVaultPayload

// 계정 인증 서버 포트 (api-spec 2.5). 원본: Apps/Web/src/domain/auth/authApi.ts
// 구현은 server-http 모듈이 맡고, 실패는 ServerFailure(ServerError, NetworkError, UnexpectedResponseError)로 알린다

/** 지원하는 유일한 KDF */
const val PBKDF2_SHA256: String = "PBKDF2-HMAC-SHA256"

data class KdfParams(
    val kdf: String,
    val iterations: Int,
    /** Standard Base64, 16바이트 */
    val salt: String,
)

data class Session(
    val accessToken: String,
    /** ISO 8601 */
    val expiresAt: String,
)

data class RegisterRequest(val email: String, val authHash: String, val kdfSalt: String, val kdfIterations: Int)

data class LoginRequest(val email: String, val authHash: String)

/** 비밀번호 변경 때 함께 바꾸는 볼트 */
data class VaultReplacement(val expectedRevision: Int, val payload: EncryptedVaultPayload)

/** 마스터 비밀번호 변경 요청. 인증 해시와 salt는 Standard Base64다 (api-spec 2.5.5, SEC-004) */
data class ChangePasswordRequest(
    val currentAuthHash: String,
    val newAuthHash: String,
    val kdf: UnlockKdf,
    val vault: VaultReplacement,
)

/** 비밀번호를 바꾼 기기에만 주는 새 세션과 교체한 볼트의 revision */
data class ChangedPassword(val accessToken: String, val expiresAt: String, val revision: Int)

/** 계정 삭제 요청. 인증 해시는 Standard Base64다 (api-spec 2.5.6, SEC-005) */
data class DeleteAccountRequest(val authHash: String)

interface AuthApi {
    suspend fun prelogin(email: String): KdfParams
    suspend fun register(request: RegisterRequest): Session
    suspend fun login(request: LoginRequest): Session
    suspend fun logout(accessToken: String)

    /**
     * KDF 값·인증 해시·볼트를 한 번에 교체한다. 성공하면 이 계정의 다른 토큰은 모두 폐기된다
     * @throws com.keymanage.domain.server.ServerError 401 AUTH_FAILED, 409 VAULT_CONFLICT, 413, 429, 401 UNAUTHORIZED
     */
    suspend fun changePassword(accessToken: String, request: ChangePasswordRequest): ChangedPassword

    /**
     * 계정·볼트·세션을 지운다. 성공하면 이 계정의 모든 토큰이 무효다
     * @throws com.keymanage.domain.server.ServerError 401 AUTH_FAILED, 401 UNAUTHORIZED, 429
     */
    suspend fun deleteAccount(accessToken: String, request: DeleteAccountRequest)
}
