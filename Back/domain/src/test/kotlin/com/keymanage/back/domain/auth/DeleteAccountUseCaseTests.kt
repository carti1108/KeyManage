package com.keymanage.back.domain.auth

import com.keymanage.back.domain.vault.CleanUpOrphanVaultsUseCase
import com.keymanage.back.domain.vault.EncryptedVault
import com.keymanage.back.domain.vault.InMemoryVaultRepository
import com.keymanage.back.domain.vault.Revision
import com.keymanage.back.domain.vault.SaveVaultUseCase
import com.keymanage.back.domain.vault.VaultConflictException
import com.keymanage.back.domain.vault.VaultId
import java.io.IOException
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 계정 삭제: 인증 해시를 확인한 뒤 계정을 지워 확정하고 볼트와 세션을 지운다 (SEC-005, ADR-0024) */
class DeleteAccountUseCaseTests {

    private val accounts = InMemoryAccountRepository()
    private val sessions = InMemorySessionRepository()
    private val vaults = InMemoryVaultRepository()
    private val failingVaults = FailingVaultRepository(vaults)
    private val hasher = FakePasswordHasher()
    private val clock = MutableClock()
    private val issuer = SessionIssuer(sessions, clock)
    private val limiter = LoginAttemptLimiter(clock)
    private val login = LoginUseCase(accounts, hasher, issuer, limiter)
    private val register = RegisterUseCase(accounts, hasher, issuer, registrationEnabled = true)
    private val authenticate = AuthenticateUseCase(sessions, accounts, clock)
    private val save = SaveVaultUseCase(vaults)
    private val email = Email.of("user@example.com")
    private val other = Email.of("other@example.com")

    private lateinit var account: Account
    private lateinit var token: String

    @BeforeTest
    fun registerAndSaveVault() {
        token = register.execute(email, authHashOf(1), kdfOf(2)).token
        account = accounts.find(email)!!
        save.execute(account.vaultId, Revision.NONE, EncryptedVault("v1"))
    }

    private fun useCase(
        accounts: AccountRepository = this.accounts,
        sessions: SessionRepository = this.sessions,
    ) = DeleteAccountUseCase(accounts, failingVaults, sessions, hasher, limiter)

    private fun delete(authHash: AuthHash = authHashOf(1)) = useCase().execute(email, authHash)

    private fun assertNothingDeleted() {
        assertEquals(account, accounts.find(email))
        assertNotNull(vaults.find(account.vaultId))
        assertEquals(account, authenticate.execute(token))
    }

    @Test
    fun `인증 해시가 맞으면 계정과 볼트, 세션을 모두 지운다`() {
        val otherDevice = login.execute(email, authHashOf(1))

        delete()

        assertNull(accounts.find(email))
        assertNull(vaults.find(account.vaultId))
        assertTrue(sessions.sessions.values.none { it.email == email })
        assertFailsWith<UnauthorizedException> { authenticate.execute(token) }
        assertFailsWith<UnauthorizedException> { authenticate.execute(otherDevice.token) }
    }

    @Test
    fun `다른 계정의 계정·볼트·세션은 그대로 둔다`() {
        val otherToken = register.execute(other, authHashOf(5), kdfOf(6)).token
        val otherAccount = accounts.find(other)!!
        save.execute(otherAccount.vaultId, Revision.NONE, EncryptedVault("other"))

        delete()

        assertEquals(otherAccount, accounts.find(other))
        assertNotNull(vaults.find(otherAccount.vaultId))
        assertEquals(otherAccount, authenticate.execute(otherToken))
    }

    @Test
    fun `인증 해시가 틀리면 AUTH_FAILED이고 아무것도 지우지 않는다`() {
        assertFailsWith<AuthFailedException> { delete(authHashOf(9)) }

        assertNothingDeleted()
    }

    @Test
    fun `틀린 인증 해시를 5번 보내면 잠겨 맞는 인증 해시도 검증 없이 TOO_MANY_ATTEMPTS`() {
        repeat(LoginAttemptLimiter.MAX_FAILURES) { assertFailsWith<AuthFailedException> { delete(authHashOf(9)) } }
        val verifyCallsBefore = hasher.verifyCalls

        val error = assertFailsWith<TooManyAttemptsException> { delete() }

        assertEquals(LoginAttemptLimiter.LOCK_DURATION, error.retryAfter)
        assertEquals(verifyCallsBefore, hasher.verifyCalls)
        assertNothingDeleted()
    }

    @Test
    fun `로그인과 같은 시도 제한을 쓴다 - 로그인 실패도 삭제 시도에 합산된다`() {
        repeat(LoginAttemptLimiter.MAX_FAILURES) { assertFailsWith<AuthFailedException> { login.execute(email, authHashOf(9)) } }

        assertFailsWith<TooManyAttemptsException> { delete() }
    }

    @Test
    fun `잠금 시간이 지나면 다시 삭제할 수 있다`() {
        repeat(LoginAttemptLimiter.MAX_FAILURES) { assertFailsWith<AuthFailedException> { delete(authHashOf(9)) } }
        clock.advance(LoginAttemptLimiter.LOCK_DURATION.plus(Duration.ofSeconds(1)))

        delete()

        assertNull(accounts.find(email))
    }

    @Test
    fun `토큰의 계정이 이미 없으면 UNAUTHORIZED`() {
        assertFailsWith<UnauthorizedException> { useCase().execute(Email.of("ghost@example.com"), authHashOf(1)) }
    }

    @Test
    fun `같은 요청을 두 번 보내면 두 번째는 계정이 없어 UNAUTHORIZED`() {
        delete()

        assertFailsWith<UnauthorizedException> { delete() }
    }

    @Test
    fun `볼트를 지우지 못해도 계정 삭제는 확정되고 세션도 지운다`() {
        failingVaults.failDelete = true

        delete()

        assertNull(accounts.find(email))
        assertFailsWith<UnauthorizedException> { authenticate.execute(token) }
        assertTrue(sessions.sessions.values.none { it.email == email })
    }

    @Test
    fun `볼트를 지우지 못해 남은 볼트는 고아 볼트 정리가 지운다`() {
        register.execute(other, authHashOf(5), kdfOf(6))
        failingVaults.failDelete = true
        delete()
        assertNotNull(vaults.find(account.vaultId))

        CleanUpOrphanVaultsUseCase(accounts, vaults).execute()

        assertNull(vaults.find(account.vaultId))
    }

    @Test
    fun `세션을 지우지 못해도 계정 삭제는 확정되고 남은 세션은 인증에서 거절된다`() {
        val failing = object : SessionRepository by sessions {
            override fun deleteAllOf(email: Email) = throw IOException("세션 삭제 실패")
        }

        useCase(sessions = failing).execute(email, authHashOf(1))

        assertNull(accounts.find(email))
        assertNull(vaults.find(account.vaultId))
        assertFailsWith<UnauthorizedException> { authenticate.execute(token) }
    }

    @Test
    fun `계정을 지우지 못하면 오류를 내고 볼트와 세션을 그대로 둔다`() {
        val failing = object : AccountRepository by accounts {
            override fun deleteIfMatches(expected: Account): Boolean = throw IOException("계정 삭제 실패")
        }

        assertFailsWith<IOException> { useCase(accounts = failing).execute(email, authHashOf(1)) }

        assertNothingDeleted()
    }

    @Test
    fun `계정 잠금 안에서 계정이 바뀌어 지우지 못하면 오류를 내고 볼트와 세션을 그대로 둔다`() {
        val refusing = object : AccountRepository by accounts {
            override fun deleteIfMatches(expected: Account): Boolean = false
        }

        assertFailsWith<IllegalStateException> { useCase(accounts = refusing).execute(email, authHashOf(1)) }

        assertNothingDeleted()
    }

    @Test
    fun `계정 잠금 안에서 확인하고 지운다`() {
        var lockDepth = 0
        val observed = mutableListOf<String>()
        val tracking = object : AccountRepository by accounts {
            override fun <T> withLock(email: Email, action: () -> T): T {
                lockDepth++
                try {
                    return accounts.withLock(email, action)
                } finally {
                    lockDepth--
                }
            }

            override fun find(email: Email): Account? = accounts.find(email).also { observed += "find:$lockDepth" }

            override fun deleteIfMatches(expected: Account): Boolean =
                accounts.deleteIfMatches(expected).also { observed += "delete:$lockDepth" }
        }

        useCase(accounts = tracking).execute(email, authHashOf(1))

        assertEquals(listOf("find:1", "delete:1"), observed)
    }

    @Test
    fun `삭제가 성공하면 시도 기록을 지워 같은 이메일로 다시 가입한 계정은 영향을 받지 않는다`() {
        repeat(LoginAttemptLimiter.MAX_FAILURES - 1) { assertFailsWith<AuthFailedException> { delete(authHashOf(9)) } }
        delete()

        register.execute(email, authHashOf(7), kdfOf(8))
        assertFailsWith<AuthFailedException> { login.execute(email, authHashOf(9)) }

        login.execute(email, authHashOf(7))
    }

    @Test
    fun `삭제 뒤 같은 이메일로 다시 가입하면 새 볼트로 시작하고 옛 토큰은 거절된다`() {
        delete()

        val newToken = register.execute(email, authHashOf(7), kdfOf(8)).token
        val newAccount = accounts.find(email)!!

        assertTrue(newAccount.vaultId != account.vaultId)
        assertNull(vaults.find(newAccount.vaultId))
        assertEquals(newAccount, authenticate.execute(newToken))
        assertFailsWith<UnauthorizedException> { authenticate.execute(token) }
    }

    @Test
    fun `삭제 전에 인증을 통과한 늦은 저장은 지운 볼트를 되살리지 않는다`() {
        val vaultId = account.vaultId

        delete()

        assertFailsWith<VaultConflictException> { save.execute(vaultId, Revision(1), EncryptedVault("late")) }
        assertFailsWith<VaultConflictException> { save.execute(vaultId, Revision.NONE, EncryptedVault("late")) }
        assertNull(vaults.find(vaultId))
    }

    @Test
    fun `계정을 지운 직후 볼트를 지우기 전에 끼어든 저장도 남지 않는다`() {
        val vaultId = account.vaultId
        val interleaving = object : AccountRepository by accounts {
            override fun deleteIfMatches(expected: Account): Boolean =
                accounts.deleteIfMatches(expected).also { save.execute(vaultId, Revision(1), EncryptedVault("in-between")) }
        }

        useCase(accounts = interleaving).execute(email, authHashOf(1))

        assertNull(vaults.find(vaultId))
        assertEquals(emptySet<VaultId>(), vaults.allIds())
    }
}
