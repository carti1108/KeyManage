package com.keymanage.android.domain.remote

import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import java.time.Instant

/** 서버 볼트(api-spec 2.3). revision은 다음 저장의 expectedRevision이다. */
data class RemoteVault(val revision: Int, val payload: EncryptedVaultPayload)

/** 로그인·가입·비밀번호 변경 결과 세션(api-spec 2.5). */
data class Session(val accessToken: String, val expiresAt: Instant)

/** prelogin 응답(api-spec 2.5.1). salt는 Standard Base64. 범위 검사는 KdfParameters가 맡는다. */
class PreloginInfo(val kdf: String, val iterations: Int, salt: ByteArray) {
    private val saltBytes = salt.copyOf()
    val salt: ByteArray get() = saltBytes.copyOf()

    override fun equals(other: Any?): Boolean =
        other is PreloginInfo && kdf == other.kdf && iterations == other.iterations && saltBytes.contentEquals(other.saltBytes)

    override fun hashCode(): Int = 31 * (31 * kdf.hashCode() + iterations) + saltBytes.contentHashCode()
}

/** 볼트 API 포트(api-spec 2.3·2.4). 구현은 `:core:data`의 HTTP 어댑터다. */
interface VaultApi {
    /** 볼트가 없으면(404 VAULT_NOT_FOUND) null. */
    fun fetchVault(accessToken: String): RemoteVault?

    /** 저장에 성공하면 새 revision. expectedRevision이 다르면 [ApiError.Server]의 `VAULT_CONFLICT`. */
    fun saveVault(accessToken: String, expectedRevision: Int, payload: EncryptedVaultPayload): Int
}

/** 계정 인증 API 포트(api-spec 2.5). 바이트 값은 어댑터가 Standard Base64로 보낸다. */
interface AuthApi {
    fun prelogin(email: String): PreloginInfo
    fun register(email: String, authHash: ByteArray, kdfSalt: ByteArray, kdfIterations: Int): Session
    fun login(email: String, authHash: ByteArray): Session
    fun logout(accessToken: String)
    fun changePassword(request: ChangePasswordRequest): ChangePasswordResult
    fun deleteAccount(accessToken: String, authHash: ByteArray)
}

/** 마스터 비밀번호 변경 요청(api-spec 2.5.5). KDF·인증 해시·볼트를 한 번에 바꾼다. */
class ChangePasswordRequest(
    val accessToken: String,
    val currentAuthHash: ByteArray,
    val newAuthHash: ByteArray,
    val kdfIterations: Int,
    val kdfSalt: ByteArray,
    val expectedRevision: Int,
    val payload: EncryptedVaultPayload,
)

data class ChangePasswordResult(val session: Session, val revision: Int)
