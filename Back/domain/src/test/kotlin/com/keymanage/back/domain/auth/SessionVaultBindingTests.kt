package com.keymanage.back.domain.auth

import com.keymanage.back.domain.vault.EncryptedVault
import com.keymanage.back.domain.vault.InMemoryVaultRepository
import com.keymanage.back.domain.vault.Revision
import com.keymanage.back.domain.vault.SaveVaultUseCase
import java.io.IOException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 세션은 발급 시점 계정의 볼트 ID에 묶인다. 비밀번호 변경은 볼트 ID를 바꾸므로, 변경과 겹쳐 발급된 옛 비밀번호 세션과
 * 확정 뒤 아직 지우지 못한 세션이 모두 무효가 된다 (SEC-004 규칙 7, ADR-BE-001 결정 6)
 */
class SessionVaultBindingTests {

    private val accounts = InMemoryAccountRepository()
    private val sessions = InMemorySessionRepository()
    private val vaults = InMemoryVaultRepository()
    private val hasher = FakePasswordHasher()
    private val clock = MutableClock()
    private val issuer = SessionIssuer(sessions, clock)
    private val limiter = LoginAttemptLimiter(clock)
    private val authenticate = AuthenticateUseCase(sessions, accounts, clock)
    private val email = Email.of("user@example.com")

    private fun changeUseCase(
        sessionRepository: SessionRepository = sessions,
        sessionIssuer: SessionIssuer = issuer,
        passwordHasher: PasswordHasher = hasher,
        accountRepository: AccountRepository = accounts,
    ) = ChangePasswordUseCase(accountRepository, vaults, sessionRepository, passwordHasher, sessionIssuer, limiter)

    private fun change(useCase: ChangePasswordUseCase = changeUseCase()) =
        useCase.execute(email, PasswordChange(authHashOf(1), authHashOf(3), kdfOf(4), Revision(1), EncryptedVault("re-encrypted")))

    @BeforeTest
    fun registerAndSaveVault() {
        RegisterUseCase(accounts, hasher, issuer, registrationEnabled = true).execute(email, authHashOf(1), kdfOf(2))
        SaveVaultUseCase(vaults).execute(accounts.find(email)!!.vaultId, Revision.NONE, EncryptedVault("v1"))
    }

    @Test
    fun `발급한 세션은 그 계정의 지금 볼트 ID를 담는다`() {
        val token = LoginUseCase(accounts, hasher, issuer, limiter).execute(email, authHashOf(1)).token

        assertEquals(accounts.find(email)!!.vaultId, sessions.find(SessionTokens.hash(token))?.vaultId)
        assertEquals(email, authenticate.execute(token).email)
    }

    @Test
    fun `옛 비밀번호 로그인이 검증하는 사이 변경이 확정되면 그 로그인 토큰은 거절하고 지운다`() {
        // 로그인이 옛 계정을 읽고 해시를 검증하는 중에 다른 요청이 비밀번호를 바꾼다
        val racing = object : PasswordHasher by hasher {
            var raced = false
            override fun verify(authHash: AuthHash, encoded: String): Boolean {
                val result = hasher.verify(authHash, encoded)
                if (!raced) {
                    raced = true
                    change()
                }
                return result
            }
        }
        val token = LoginUseCase(accounts, racing, issuer, limiter).execute(email, authHashOf(1)).token

        assertFailsWith<UnauthorizedException> { authenticate.execute(token) }
        assertNull(sessions.find(SessionTokens.hash(token)))
    }

    @Test
    fun `변경이 확정된 뒤 세션을 지우기 전에도 옛 토큰은 거절한다`() {
        val oldToken = LoginUseCase(accounts, hasher, issuer, limiter).execute(email, authHashOf(1)).token
        var authenticatedBeforeDelete: Result<Account>? = null
        val observing = object : SessionRepository by sessions {
            override fun deleteAllOf(email: Email) {
                authenticatedBeforeDelete = runCatching { authenticate.execute(oldToken) }
                sessions.deleteAllOf(email)
            }
        }

        change(changeUseCase(sessionRepository = observing))

        assertTrue(authenticatedBeforeDelete!!.exceptionOrNull() is UnauthorizedException)
    }

    @Test
    fun `확정 뒤 세션 삭제가 실패해도 변경은 성공하고 남은 옛 세션은 무효다`() {
        val oldToken = LoginUseCase(accounts, hasher, issuer, limiter).execute(email, authHashOf(1)).token
        val failing = object : SessionRepository by sessions {
            override fun deleteAllOf(email: Email) = throw IOException("세션 폴더 오류")
        }

        val result = change(changeUseCase(sessionRepository = failing))

        assertEquals(email, authenticate.execute(result.session.token).email)
        assertFailsWith<UnauthorizedException> { authenticate.execute(oldToken) }
        assertTrue(hasher.verify(authHashOf(3), accounts.find(email)!!.passwordHash))
    }

    @Test
    fun `확정 뒤 새 토큰 발급이 실패하면 500 대신 UNAUTHORIZED로 알리고 변경은 남는다`() {
        val failingIssuer = SessionIssuer(
            object : SessionRepository by sessions {
                override fun save(session: Session) = throw IOException("세션 쓰기 실패")
            },
            clock,
        )

        assertFailsWith<UnauthorizedException> { change(changeUseCase(sessionIssuer = failingIssuer)) }

        assertTrue(hasher.verify(authHashOf(3), accounts.find(email)!!.passwordHash))
        LoginUseCase(accounts, hasher, issuer, limiter).execute(email, authHashOf(3))
    }

    @Test
    fun `새 인증 해시는 계정·볼트 잠금 밖에서 미리 해시한다`() {
        var locked = false
        val tracking = object : AccountRepository by accounts {
            override fun <T> withLock(email: Email, action: () -> T): T {
                locked = true
                try {
                    return accounts.withLock(email, action)
                } finally {
                    locked = false
                }
            }
        }
        val hashedInsideLock = mutableListOf<Boolean>()
        val recording = object : PasswordHasher by hasher {
            override fun hash(authHash: AuthHash): String {
                hashedInsideLock += locked
                return hasher.hash(authHash)
            }
        }

        change(changeUseCase(passwordHasher = recording, accountRepository = tracking))

        assertEquals(listOf(false), hashedInsideLock)
        assertFalse(locked)
    }
}
