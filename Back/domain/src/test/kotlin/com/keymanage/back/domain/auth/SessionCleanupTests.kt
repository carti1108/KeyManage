package com.keymanage.back.domain.auth

import com.keymanage.back.domain.vault.EncryptedVault
import com.keymanage.back.domain.vault.InMemoryVaultRepository
import com.keymanage.back.domain.vault.Revision
import com.keymanage.back.domain.vault.SaveVaultUseCase
import com.keymanage.back.domain.vault.VaultId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 무효 세션은 거절하면서 지우고, 비밀번호 변경은 새 볼트에 묶인 정상 세션을 지우지 않는다 (SEC-002, SEC-004 규칙 7) */
class SessionCleanupTests {

    private val accounts = InMemoryAccountRepository()
    private val sessions = InMemorySessionRepository()
    private val vaults = InMemoryVaultRepository()
    private val hasher = FakePasswordHasher()
    private val clock = MutableClock()
    private val issuer = SessionIssuer(sessions, clock)
    private val limiter = LoginAttemptLimiter(clock)
    private val authenticate = AuthenticateUseCase(sessions, accounts, clock)
    private val email = Email.of("user@example.com")

    @BeforeTest
    fun registerAndSaveVault() {
        RegisterUseCase(accounts, hasher, issuer, registrationEnabled = true).execute(email, authHashOf(1), kdfOf(2))
        SaveVaultUseCase(vaults).execute(accounts.find(email)!!.vaultId, Revision.NONE, EncryptedVault("v1"))
    }

    @Test
    fun `계정이 없는 세션은 거절하면서 지운다`() {
        val token = "orphan-session-token"
        val session = Session(SessionTokens.hash(token), Email.of("ghost@example.com"), clock.instant().plus(SessionIssuer.TTL), VaultId.of("550e8400-e29b-41d4-a716-446655440000"))
        sessions.save(session)

        assertFailsWith<UnauthorizedException> { authenticate.execute(token) }

        assertNull(sessions.find(session.tokenHash))
    }

    @Test
    fun `확정 직후 새 비밀번호로 로그인한 세션은 변경 뒤 세션 정리에서 지우지 않는다`() {
        val oldToken = LoginUseCase(accounts, hasher, issuer, limiter).execute(email, authHashOf(1)).token
        var loggedInRightAfterCommit: String? = null
        val loginBeforeCleanup = object : SessionRepository by sessions {
            override fun deleteAllOf(email: Email, except: VaultId) {
                loggedInRightAfterCommit = LoginUseCase(accounts, hasher, issuer, limiter).execute(email, authHashOf(3)).token
                sessions.deleteAllOf(email, except)
            }
        }
        val useCase = ChangePasswordUseCase(accounts, vaults, loginBeforeCleanup, hasher, issuer, limiter)

        val result = useCase.execute(email, PasswordChange(authHashOf(1), authHashOf(3), kdfOf(4), Revision(1), EncryptedVault("re-encrypted")))

        assertEquals(email, authenticate.execute(loggedInRightAfterCommit!!).email)
        assertEquals(email, authenticate.execute(result.session.token).email)
        assertNull(sessions.find(SessionTokens.hash(oldToken)))
        assertNotNull(sessions.find(SessionTokens.hash(loggedInRightAfterCommit!!)))
    }
}
