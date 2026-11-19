package com.keymanage.android.domain.auth

import com.keymanage.android.domain.crypto.CryptoError
import com.keymanage.android.domain.crypto.KdfParameters
import com.keymanage.android.domain.crypto.KeySeparator
import com.keymanage.android.domain.crypto.MasterKeyDeriver
import com.keymanage.android.domain.crypto.PasswordPreprocessor
import com.keymanage.android.domain.remote.AuthApi
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.sync.DeviceAccount
import java.security.SecureRandom
import java.util.Base64

/**
 * 가입·로그인 성공 결과. [encryptionKey]는 볼트 암·복호화 키이며 잠그면 0으로 덮어쓴다(SEC-003).
 * [isNewAccount]는 가입 결과다. 가입 직후 생체 인증을 켤지 묻는 데 쓴다(SEC-003 값 표).
 */
class AuthResult(
    val session: Session,
    val encryptionKey: ByteArray,
    val account: DeviceAccount,
    val isNewAccount: Boolean = false,
)

/** 새 마스터 비밀번호 문제(SEC-002 규칙 7). */
enum class NewPasswordProblem { TOO_SHORT, MISMATCH }

/** 서버에 묻기 전에 걸러지는 입력 오류. 서버 오류는 [com.keymanage.android.domain.remote.ApiError]로 올라간다. */
sealed class AuthError(message: String) : Exception(message) {
    class InvalidEmail : AuthError("이메일 형식이 올바르지 않음")
    class WeakNewPassword(val problem: NewPasswordProblem) : AuthError("새 마스터 비밀번호 규칙 위반: $problem")
}

/**
 * 가입·로그인(SEC-002). 서버에는 HKDF로 분리한 인증 해시만 보내고, 마스터 비밀번호와 암호화 키는 이 밖으로 보내지 않는다.
 * 웹 `authService`와 같은 규칙이다. 키 유도(60만 회)는 오래 걸리므로 메인 스레드가 아닌 곳에서 부른다.
 */
class AuthUseCase(
    private val api: AuthApi,
    private val deriver: MasterKeyDeriver,
    private val separator: KeySeparator,
    private val saltSource: () -> ByteArray = ::randomSalt,
) {
    /** 새 salt를 만들어 600,000회로 가입한다. */
    fun register(email: String, password: String, confirmation: String): AuthResult {
        val normalized = normalizeEmail(email)
        checkNewPassword(password, confirmation)?.let { throw AuthError.WeakNewPassword(it) }
        val kdf = KdfParameters(saltSource(), KdfParameters.DEFAULT_ITERATIONS)
        return authenticate(normalized, password, kdf, isNewAccount = true) { authHash ->
            api.register(normalized, authHash, kdf.salt, kdf.iterations)
        }
    }

    /** prelogin으로 받은 KDF 값으로 키를 만들어 로그인한다. KDF 값이 규격 밖이면 로그인 요청을 보내지 않는다. */
    fun login(email: String, password: String): AuthResult {
        val normalized = normalizeEmail(email)
        val info = api.prelogin(normalized)
        if (info.kdf != SUPPORTED_KDF) throw CryptoError.InvalidKdfParameters("지원하지 않는 KDF: ${info.kdf}")
        val kdf = KdfParameters(info.salt, info.iterations)
        return authenticate(normalized, password, kdf) { authHash -> api.login(normalized, authHash) }
    }

    private fun authenticate(
        email: String,
        password: String,
        kdf: KdfParameters,
        isNewAccount: Boolean = false,
        send: (ByteArray) -> Session,
    ): AuthResult {
        val passwordBytes = PasswordPreprocessor.preprocess(password)
        val masterKey = try {
            deriver.deriveMasterKey(passwordBytes, kdf)
        } finally {
            passwordBytes.fill(0)
        }
        val keys = try {
            separator.separate(masterKey)
        } finally {
            masterKey.fill(0)
        }
        val session = try {
            send(keys.authHash)
        } catch (error: Exception) {
            keys.encryptionKey.fill(0)
            throw error
        } finally {
            keys.authHash.fill(0)
        }
        val account = DeviceAccount(email, kdf.iterations, Base64.getEncoder().encodeToString(kdf.salt))
        return AuthResult(session, keys.encryptionKey, account, isNewAccount)
    }

    companion object {
        const val SUPPORTED_KDF = "PBKDF2-HMAC-SHA256"
        const val MAX_EMAIL_LENGTH = 254
        const val MIN_MASTER_PASSWORD_LENGTH = 12

        /** 서버(api-spec 2.5)와 같은 형식 검사. 틀린 이메일로 60만 회 키 유도를 하지 않도록 먼저 거른다. */
        private val EMAIL_FORMAT = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        /** 앞뒤 공백 제거·소문자(SEC-002 값 표). */
        fun normalizeEmail(email: String): String {
            val normalized = email.trim().lowercase()
            if (normalized.isEmpty() || normalized.length > MAX_EMAIL_LENGTH || !EMAIL_FORMAT.matches(normalized)) {
                throw AuthError.InvalidEmail()
            }
            return normalized
        }

        /** 문제가 없으면 null. 코드포인트 수로 세고 앞뒤 공백도 센다. 길이 부족을 불일치보다 먼저 알린다. */
        fun checkNewPassword(password: String, confirmation: String): NewPasswordProblem? = when {
            password.codePointCount(0, password.length) < MIN_MASTER_PASSWORD_LENGTH -> NewPasswordProblem.TOO_SHORT
            password != confirmation -> NewPasswordProblem.MISMATCH
            else -> null
        }

        private fun randomSalt(): ByteArray = ByteArray(KdfParameters.SALT_LENGTH).also(SecureRandom()::nextBytes)
    }
}
