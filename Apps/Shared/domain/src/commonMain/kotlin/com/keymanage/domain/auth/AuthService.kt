package com.keymanage.domain.auth

import com.keymanage.domain.crypto.EncryptionKey
import com.keymanage.domain.crypto.InvalidKdfParamsException
import com.keymanage.domain.crypto.MIN_ITERATIONS
import com.keymanage.domain.crypto.SALT_BYTES
import com.keymanage.domain.crypto.deriveKeys
import com.keymanage.domain.encoding.InvalidBase64Exception
import com.keymanage.domain.encoding.fromBase64
import com.keymanage.domain.encoding.toBase64
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.domain.platform.SecureRandomBytes
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode

// 가입·로그인·로그아웃·비밀번호 변경·계정 삭제 유스케이스 (SEC-002, SEC-004, SEC-005, ADR-0008)
// 원본: Apps/Web/src/domain/auth/authService.ts
// 마스터 비밀번호와 암호화 키는 서버 포트로 넘기지 않는다. 서버에는 인증 해시만 보낸다

private const val MAX_EMAIL_LENGTH = 254

// 원본 정규식의 \s와 같은 공백 문자. 플랫폼마다 \s·trim 범위가 달라 문자를 고정한다
private const val JS_WHITESPACE = "\t\n\u000B\u000C\r                  　﻿"

// 서버(Email)와 같은 형식 검사. 틀린 이메일로 60만 회 키 유도를 하지 않도록 먼저 거른다
private val EMAIL_FORMAT = Regex("^[^@$JS_WHITESPACE]+@[^@$JS_WHITESPACE]+\\.[^@$JS_WHITESPACE]+$")

class InvalidEmailException : IllegalArgumentException("이메일 형식이 올바르지 않음")

/** 기기에 저장한 KDF 값이 서버와 다르거나 로그인이 인증 실패로 거절됨. 다른 기기에서 마스터 비밀번호를 바꾼 것으로 본다 (SEC-003) */
class MasterPasswordChangedException : Exception("마스터 비밀번호가 바뀜")

/** 잠금 해제 때 서버 없이 같은 키를 다시 만들기 위한 값. 비밀이 아니다 (SEC-003) */
data class UnlockKdf(
    /** Standard Base64, 16바이트 */
    val salt: String,
    val iterations: Int,
)

class AuthResult(
    val session: Session,
    /** 볼트 암·복호화 키. 잠그면 버린다 (SEC-003) */
    val encryptionKey: EncryptionKey,
    val kdf: UnlockKdf,
)

/** 앞뒤 공백 제거·소문자. 서버도 같은 규칙으로 비교한다 (api-spec 2.5) */
fun normalizeEmail(email: String): String {
    val normalized = email.trim { it in JS_WHITESPACE }.lowercase()
    if (normalized.isEmpty() || normalized.length > MAX_EMAIL_LENGTH || !EMAIL_FORMAT.matches(normalized)) {
        throw InvalidEmailException()
    }
    return normalized
}

/** 서버가 준 KDF 값이 기기에 저장한 값과 다른지(지원하지 않는 KDF 포함) (SEC-003 규칙 11) */
private fun kdfDiffers(params: KdfParams, kdf: UnlockKdf): Boolean =
    params.kdf != PBKDF2_SHA256 || params.salt != kdf.salt || params.iterations != kdf.iterations

class AuthService(
    private val api: AuthApi,
    private val random: RandomBytes = SecureRandomBytes,
) {
    /** 새 salt를 만들어 600,000회로 가입한다 */
    suspend fun register(email: String, password: String): AuthResult {
        val normalized = normalizeEmail(email)
        val salt = random.nextBytes(SALT_BYTES)
        val keys = deriveKeys(password, salt, MIN_ITERATIONS)
        val kdf = UnlockKdf(toBase64(salt), MIN_ITERATIONS)
        val session = api.register(RegisterRequest(normalized, takeAuthHash(keys.authHash), kdf.salt, kdf.iterations))
        return AuthResult(session, keys.encryptionKey, kdf)
    }

    /** @throws InvalidKdfParamsException 서버가 준 KDF 값이 규격 밖이면 로그인 요청을 보내지 않는다 */
    suspend fun login(email: String, password: String): AuthResult {
        val normalized = normalizeEmail(email)
        val params = api.prelogin(normalized)
        if (params.kdf != PBKDF2_SHA256) throw InvalidKdfParamsException("지원하지 않는 KDF: ${params.kdf}")
        val keys = deriveKeys(password, decodeSalt(params.salt), params.iterations)
        val session = api.login(LoginRequest(normalized, takeAuthHash(keys.authHash)))
        return AuthResult(session, keys.encryptionKey, UnlockKdf(params.salt, params.iterations))
    }

    /**
     * 세션 토큰 없이 잠금을 풀 때, 기기에 저장한 KDF 값으로 이미 만든 인증 해시로 로그인한다 (ADR-0019).
     * 인증 해시 바이트는 성공·실패와 관계없이 지운다
     * @throws MasterPasswordChangedException, 그 밖에 서버 통신 오류
     */
    suspend fun resume(email: String, kdf: UnlockKdf, authHash: ByteArray): Session {
        try {
            val params = api.prelogin(email)
            if (kdfDiffers(params, kdf)) throw MasterPasswordChangedException()
            return api.login(LoginRequest(email, takeAuthHash(authHash)))
        } catch (error: ServerError) {
            if (error.code == ServerErrorCode.AUTH_FAILED) throw MasterPasswordChangedException()
            throw error
        } finally {
            authHash.fill(0)
        }
    }

    /**
     * 서버의 KDF 값이 기기 값과 다른지 prelogin으로만 확인한다. 비밀번호·인증 해시는 보내지 않는다 (SEC-003 규칙 11)
     * @throws 서버 통신 오류. 부른 쪽이 확인 실패로 다룬다
     */
    suspend fun kdfChanged(email: String, kdf: UnlockKdf): Boolean = kdfDiffers(api.prelogin(email), kdf)

    suspend fun logout(accessToken: String) = api.logout(accessToken)

    /**
     * 현재·새 인증 해시와 새 KDF 값, 새 키로 다시 암호화한 볼트를 한 요청으로 보낸다 (SEC-004, api-spec 2.5.5).
     * 두 인증 해시 바이트는 성공·실패와 관계없이 지운다
     */
    suspend fun changePassword(
        accessToken: String,
        currentAuthHash: ByteArray,
        newAuthHash: ByteArray,
        kdf: UnlockKdf,
        vault: VaultReplacement,
    ): ChangedPassword = try {
        api.changePassword(accessToken, ChangePasswordRequest(takeAuthHash(currentAuthHash), takeAuthHash(newAuthHash), kdf, vault))
    } finally {
        currentAuthHash.fill(0)
        newAuthHash.fill(0)
    }

    /** 인증 해시로 계정을 지운다 (SEC-005, api-spec 2.5.6). 인증 해시 바이트는 성공·실패와 관계없이 지운다 */
    suspend fun deleteAccount(accessToken: String, authHash: ByteArray) {
        try {
            api.deleteAccount(accessToken, DeleteAccountRequest(takeAuthHash(authHash)))
        } finally {
            authHash.fill(0)
        }
    }
}

// 인증 해시는 서버로 보낼 문자열만 만들고 바이트는 지운다. 잠긴 동안 메모리에 남지 않게 한다 (SEC-003)
private fun takeAuthHash(authHash: ByteArray): String {
    val encoded = toBase64(authHash)
    authHash.fill(0)
    return encoded
}

private fun decodeSalt(salt: String): ByteArray = try {
    fromBase64(salt)
} catch (error: InvalidBase64Exception) {
    throw InvalidKdfParamsException("salt가 표준 Base64가 아님")
}
