package com.keymanage.android.domain.auth

import com.keymanage.android.domain.crypto.CryptoError
import com.keymanage.android.domain.crypto.KdfParameters
import com.keymanage.android.domain.crypto.KeySeparator
import com.keymanage.android.domain.crypto.MasterKeyDeriver
import com.keymanage.android.domain.crypto.SeparatedKeys
import com.keymanage.android.domain.remote.AuthApi
import com.keymanage.android.domain.remote.ChangePasswordRequest
import com.keymanage.android.domain.remote.ChangePasswordResult
import com.keymanage.android.domain.remote.PreloginInfo
import com.keymanage.android.domain.remote.Session
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthUseCaseTest {
    private val salt = ByteArray(16) { 9 }
    private val api = FakeAuthApi(PreloginInfo("PBKDF2-HMAC-SHA256", 600_000, salt))
    private val useCase = AuthUseCase(api, EchoDeriver, SplitSeparator, saltSource = { salt.copyOf() })

    @Test
    fun `이메일은 앞뒤 공백을 지우고 소문자로 바꾼다`() {
        assertEquals("dev@example.com", AuthUseCase.normalizeEmail("  Dev@Example.COM "))
    }

    @Test
    fun `형식이 틀리거나 254자를 넘는 이메일은 거절한다`() {
        for (email in listOf("", "no-at", "a@b", "a b@c.d", "a@@b.c", "a".repeat(250) + "@b.cd")) {
            assertFailsWith<AuthError.InvalidEmail>(email) { AuthUseCase.normalizeEmail(email) }
        }
    }

    @Test
    fun `새 마스터 비밀번호는 코드포인트 12자 이상이고 확인과 같아야 한다`() {
        assertEquals(NewPasswordProblem.TOO_SHORT, AuthUseCase.checkNewPassword("a".repeat(11), "a".repeat(11)))
        assertEquals(NewPasswordProblem.MISMATCH, AuthUseCase.checkNewPassword("a".repeat(12), "b".repeat(12)))
        assertNull(AuthUseCase.checkNewPassword("a".repeat(12), "a".repeat(12)))
        // 이모지 11개 + 공백 1개 = 12 코드포인트. 앞뒤 공백도 센다
        val emoji = "🔑".repeat(11) + " "
        assertNull(AuthUseCase.checkNewPassword(emoji, emoji))
    }

    @Test
    fun `길이 부족을 불일치보다 먼저 알린다`() {
        assertEquals(NewPasswordProblem.TOO_SHORT, AuthUseCase.checkNewPassword("short", "other"))
    }

    @Test
    fun `로그인은 prelogin의 KDF 값으로 키를 만들어 인증 해시만 보낸다`() {
        // When
        val result = useCase.login(" Dev@Example.com", "password")

        // Then
        assertEquals("dev@example.com", api.loginEmail)
        assertContentEquals(SplitSeparator.authHashOf("password"), api.loginAuthHash)
        assertContentEquals(SplitSeparator.encryptionKeyOf("password"), result.encryptionKey)
        assertEquals("tok", result.session.accessToken)
        assertEquals(600_000, result.account.kdfIterations)
        assertEquals(Base64.getEncoder().encodeToString(salt), result.account.kdfSalt)
        assertEquals("dev@example.com", result.account.email)
    }

    @Test
    fun `지원하지 않는 KDF면 로그인 요청을 보내지 않는다`() {
        val api = FakeAuthApi(PreloginInfo("scrypt", 600_000, salt))
        assertFailsWith<CryptoError.InvalidKdfParameters> { AuthUseCase(api, EchoDeriver, SplitSeparator).login("a@b.c", "password") }
        assertNull(api.loginEmail)
    }

    @Test
    fun `범위 밖 반복 횟수면 로그인 요청을 보내지 않는다`() {
        val api = FakeAuthApi(PreloginInfo("PBKDF2-HMAC-SHA256", 10_000_001, salt))
        assertFailsWith<CryptoError.InvalidKdfParameters> { AuthUseCase(api, EchoDeriver, SplitSeparator).login("a@b.c", "password") }
        assertNull(api.loginEmail)
    }

    @Test
    fun `형식이 틀린 이메일은 서버에 묻기 전에 거절한다`() {
        assertFailsWith<AuthError.InvalidEmail> { useCase.login("nope", "password") }
        assertNull(api.preloginEmail)
    }

    @Test
    fun `가입은 새 salt와 600000회로 등록한다`() {
        // When
        val result = useCase.register("dev@example.com", "a".repeat(12), "a".repeat(12))

        // Then
        assertContentEquals(salt, api.registerSalt)
        assertEquals(KdfParameters.DEFAULT_ITERATIONS, api.registerIterations)
        assertEquals(KdfParameters.DEFAULT_ITERATIONS, result.account.kdfIterations)
    }

    @Test
    fun `가입 결과만 새 계정으로 표시한다`() {
        assertTrue(useCase.register("dev@example.com", "a".repeat(12), "a".repeat(12)).isNewAccount)
        assertFalse(useCase.login("dev@example.com", "password").isNewAccount)
    }

    @Test
    fun `가입 비밀번호 규칙을 어기면 서버에 보내지 않는다`() {
        val error = assertFailsWith<AuthError.WeakNewPassword> { useCase.register("dev@example.com", "short", "short") }
        assertEquals(NewPasswordProblem.TOO_SHORT, error.problem)
        assertNull(api.registerSalt)
    }

    /** 비밀번호 바이트를 그대로 마스터 키로 쓰는 테스트 유도기. */
    private object EchoDeriver : MasterKeyDeriver {
        override fun deriveMasterKey(password: ByteArray, parameters: KdfParameters) = password.copyOf()
    }

    /** 마스터 키 앞에 표식을 붙여 두 키를 구분한다. */
    private object SplitSeparator : KeySeparator {
        override fun separate(masterKey: ByteArray) = SeparatedKeys(byteArrayOf(1) + masterKey, byteArrayOf(2) + masterKey)
        fun encryptionKeyOf(password: String) = byteArrayOf(1) + password.toByteArray()
        fun authHashOf(password: String) = byteArrayOf(2) + password.toByteArray()
    }

    private class FakeAuthApi(private val prelogin: PreloginInfo) : AuthApi {
        var preloginEmail: String? = null
        var loginEmail: String? = null
        var loginAuthHash: ByteArray? = null
        var registerSalt: ByteArray? = null
        var registerIterations: Int? = null
        private val session = Session("tok", Instant.parse("2026-12-18T00:00:00Z"))

        override fun prelogin(email: String): PreloginInfo = prelogin.also { preloginEmail = email }

        override fun register(email: String, authHash: ByteArray, kdfSalt: ByteArray, kdfIterations: Int): Session {
            registerSalt = kdfSalt.copyOf()
            registerIterations = kdfIterations
            return session
        }

        override fun login(email: String, authHash: ByteArray): Session {
            loginEmail = email
            loginAuthHash = authHash.copyOf()
            return session
        }

        override fun logout(accessToken: String) = Unit
        override fun changePassword(request: ChangePasswordRequest): ChangePasswordResult = error("쓰지 않음")
        override fun deleteAccount(accessToken: String, authHash: ByteArray) = Unit
    }
}
