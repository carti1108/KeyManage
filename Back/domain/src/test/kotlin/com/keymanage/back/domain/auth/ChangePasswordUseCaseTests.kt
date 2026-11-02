package com.keymanage.back.domain.auth

import com.keymanage.back.domain.vault.EncryptedVault
import com.keymanage.back.domain.vault.InMemoryVaultRepository
import com.keymanage.back.domain.vault.Revision
import com.keymanage.back.domain.vault.SaveVaultUseCase
import com.keymanage.back.domain.vault.StoredVault
import com.keymanage.back.domain.vault.VaultConflictException
import com.keymanage.back.domain.vault.VaultId
import com.keymanage.back.domain.vault.VaultRepository
import com.keymanage.back.domain.vault.VaultTooLargeException
import java.io.IOException
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 마스터 비밀번호 변경: 인증 정보와 볼트를 전부 바꾸거나 전부 그대로 둔다 (SEC-004, ADR-0023, ADR-BE-001) */
class ChangePasswordUseCaseTests {

    private val accounts = InMemoryAccountRepository()
    private val sessions = InMemorySessionRepository()
    private val vaults = InMemoryVaultRepository()
    private val failingVaults = FailingVaultRepository(vaults)
    private val failingAccounts = FailingAccountRepository(accounts)
    private val hasher = FakePasswordHasher()
    private val clock = MutableClock()
    private val issuer = SessionIssuer(sessions, clock)
    private val limiter = LoginAttemptLimiter(clock)
    private val login = LoginUseCase(accounts, hasher, issuer, limiter)
    private val useCase = ChangePasswordUseCase(failingAccounts, failingVaults, sessions, hasher, issuer, limiter)
    private val email = Email.of("user@example.com")

    private lateinit var oldAccount: Account
    private lateinit var oldVault: StoredVault

    @BeforeTest
    fun registerAndSaveVault() {
        RegisterUseCase(accounts, hasher, issuer, registrationEnabled = true).execute(email, authHashOf(1), kdfOf(2))
        oldAccount = accounts.find(email)!!
        val save = SaveVaultUseCase(vaults)
        save.execute(oldAccount.vaultId, Revision.NONE, EncryptedVault("v1"))
        save.execute(oldAccount.vaultId, Revision(1), EncryptedVault("v2"))
        oldVault = vaults.find(oldAccount.vaultId)!!
    }

    private fun change(
        current: AuthHash = authHashOf(1),
        new: AuthHash = authHashOf(3),
        kdf: KdfParams = kdfOf(4),
        expected: Revision = Revision(2),
        payload: String = "re-encrypted",
    ): PasswordChanged = useCase.execute(email, PasswordChange(current, new, kdf, expected, EncryptedVault(payload)))

    private fun assertNothingChanged() {
        assertEquals(oldAccount, accounts.find(email))
        assertEquals(oldVault, vaults.find(oldAccount.vaultId))
        assertEquals(setOf(oldAccount.vaultId), vaults.allIds())
        login.execute(email, authHashOf(1))
    }

    @Test
    fun `현재 인증 해시가 맞으면 KDF 값과 인증 해시, 볼트를 함께 교체한다`() {
        change()

        val account = accounts.find(email)!!
        assertEquals(kdfOf(4), account.kdf)
        assertTrue(hasher.verify(authHashOf(3), account.passwordHash))
        assertNotEquals(oldAccount.vaultId, account.vaultId)
        assertEquals(StoredVault(account.vaultId, Revision(3), EncryptedVault("re-encrypted")), vaults.find(account.vaultId))
    }

    @Test
    fun `교체한 볼트의 revision은 옛 revision에 1을 더한 값이다`() {
        assertEquals(Revision(3), change().revision)
    }

    @Test
    fun `교체가 끝나면 옛 볼트를 지운다`() {
        change()

        assertNull(vaults.find(oldAccount.vaultId))
        assertEquals(setOf(accounts.find(email)!!.vaultId), vaults.allIds())
    }

    @Test
    fun `볼트를 아직 저장하지 않은 계정은 기대 revision 0으로 바꾸고 revision 1을 받는다`() {
        val fresh = Email.of("fresh@example.com")
        RegisterUseCase(accounts, hasher, issuer, registrationEnabled = true).execute(fresh, authHashOf(1), kdfOf(2))

        val result = useCase.execute(fresh, PasswordChange(authHashOf(1), authHashOf(3), kdfOf(4), Revision.NONE, EncryptedVault("first")))

        assertEquals(Revision(1), result.revision)
        assertEquals(EncryptedVault("first"), vaults.find(accounts.find(fresh)!!.vaultId)?.vault)
    }

    @Test
    fun `현재 인증 해시가 틀리면 AUTH_FAILED이고 아무것도 바꾸지 않는다`() {
        val sessionsBefore = sessions.sessions.toMap()

        assertFailsWith<AuthFailedException> { change(current = authHashOf(9)) }

        assertEquals(sessionsBefore, sessions.sessions)
        assertNothingChanged()
    }

    @Test
    fun `현재 인증 해시를 5번 틀리면 로그인과 같은 시도 제한으로 잠긴다`() {
        repeat(LoginAttemptLimiter.MAX_FAILURES) { assertFailsWith<AuthFailedException> { change(current = authHashOf(9)) } }

        val error = assertFailsWith<TooManyAttemptsException> { change() }
        assertEquals(Duration.ofMinutes(15), error.retryAfter)
        assertFailsWith<TooManyAttemptsException> { login.execute(email, authHashOf(1)) }
        assertEquals(oldAccount, accounts.find(email))
    }

    @Test
    fun `잠긴 동안에는 현재 인증 해시를 검증하지 않는다`() {
        repeat(LoginAttemptLimiter.MAX_FAILURES) { assertFailsWith<AuthFailedException> { change(current = authHashOf(9)) } }
        val before = hasher.verifyCalls

        assertFailsWith<TooManyAttemptsException> { change() }

        assertEquals(before, hasher.verifyCalls)
    }

    @Test
    fun `로그인 실패도 비밀번호 변경의 시도 제한에 함께 센다`() {
        repeat(LoginAttemptLimiter.MAX_FAILURES) { assertFailsWith<AuthFailedException> { login.execute(email, authHashOf(9)) } }

        assertFailsWith<TooManyAttemptsException> { change() }
    }

    @Test
    fun `현재 인증 해시가 맞으면 실패 기록을 초기화한다`() {
        repeat(LoginAttemptLimiter.MAX_FAILURES - 1) { assertFailsWith<AuthFailedException> { change(current = authHashOf(9)) } }
        change()

        repeat(LoginAttemptLimiter.MAX_FAILURES - 1) { assertFailsWith<AuthFailedException> { login.execute(email, authHashOf(9)) } }
        login.execute(email, authHashOf(3))
    }

    @Test
    fun `revision이 다르면 현재 revision을 담아 충돌로 거절하고 아무것도 바꾸지 않는다`() {
        val error = assertFailsWith<VaultConflictException> { change(expected = Revision(1)) }

        assertEquals(Revision(2), error.currentRevision)
        assertNothingChanged()
    }

    @Test
    fun `revision 충돌은 시도 제한 실패로 세지 않는다`() {
        repeat(LoginAttemptLimiter.MAX_FAILURES) { assertFailsWith<VaultConflictException> { change(expected = Revision(1)) } }

        change()
    }

    @Test
    fun `교체가 끝나면 그 계정의 세션을 모두 폐기하고 새 토큰을 하나 발급한다`() {
        val other = Email.of("other@example.com")
        RegisterUseCase(accounts, hasher, issuer, registrationEnabled = true).execute(other, authHashOf(1), kdfOf(2))
        val oldTokens = listOf(login.execute(email, authHashOf(1)).token, login.execute(email, authHashOf(1)).token)

        val result = change()

        oldTokens.forEach { assertNull(sessions.find(SessionTokens.hash(it))) }
        assertEquals(email, sessions.find(SessionTokens.hash(result.session.token))?.email)
        assertEquals(clock.instant().plus(SessionIssuer.TTL), result.session.expiresAt)
        assertEquals(1, sessions.sessions.values.count { it.email == email })
        assertEquals(1, sessions.sessions.values.count { it.email == other })
    }

    @Test
    fun `교체 뒤에는 새 인증 해시로만 로그인할 수 있다`() {
        change()

        assertFailsWith<AuthFailedException> { login.execute(email, authHashOf(1)) }
        login.execute(email, authHashOf(3))
    }

    @Test
    fun `새 볼트 쓰기에 실패하면 옛 계정과 볼트가 그대로라 옛 인증 해시로 로그인할 수 있다`() {
        val token = login.execute(email, authHashOf(1)).token
        failingVaults.failCreate = true

        assertFailsWith<IOException> { change() }

        assertNothingChanged()
        assertNotNull(sessions.find(SessionTokens.hash(token)))
    }

    @Test
    fun `계정 교체에 실패하면 옛 계정과 볼트가 그대로이고 새로 쓴 볼트는 지운다`() {
        val token = login.execute(email, authHashOf(1)).token
        failingAccounts.failReplace = true

        assertFailsWith<IOException> { change() }

        assertNothingChanged()
        assertNotNull(sessions.find(SessionTokens.hash(token)))
    }

    @Test
    fun `계정이 잠금 밖에서 바뀌어 교체하지 못하면 실패하고 새로 쓴 볼트는 지운다`() {
        failingAccounts.refuseReplace = true

        assertFailsWith<IllegalStateException> { change() }

        assertNothingChanged()
    }

    @Test
    fun `새 볼트를 지우다 실패해도 원래 오류를 그대로 알린다`() {
        failingAccounts.failReplace = true
        failingVaults.failDelete = true

        assertFailsWith<IOException> { change() }

        assertEquals(oldAccount, accounts.find(email))
    }

    @Test
    fun `옛 볼트를 지우지 못해도 변경은 성공한다`() {
        failingVaults.failDelete = true

        val result = change()

        assertEquals(Revision(3), result.revision)
        assertTrue(hasher.verify(authHashOf(3), accounts.find(email)!!.passwordHash))
    }

    @Test
    fun `교체 뒤 옛 볼트 ID로 오는 저장은 충돌로 거절된다`() {
        change()

        assertFailsWith<VaultConflictException> {
            SaveVaultUseCase(vaults).execute(oldAccount.vaultId, Revision(2), EncryptedVault("late"))
        }
        assertFailsWith<VaultConflictException> {
            SaveVaultUseCase(vaults).execute(oldAccount.vaultId, Revision.NONE, EncryptedVault("late"))
        }
    }

    @Test
    fun `반복 횟수는 600,000에서 10,000,000까지 받는다`() {
        assertFailsWith<InvalidAuthInputException> {
            change(kdf = KdfParams(ByteArray(KdfParams.SALT_BYTES) { 4 }, KdfParams.MAX_ITERATIONS + 1))
        }.also { assertEquals("kdfIterations", it.field) }
        assertNothingChanged()

        change(kdf = KdfParams(ByteArray(KdfParams.SALT_BYTES) { 4 }, KdfParams.MAX_ITERATIONS))
        assertEquals(KdfParams.MAX_ITERATIONS, accounts.find(email)!!.kdf.iterations)
    }

    @Test
    fun `볼트가 5MB를 넘으면 검증 전에 거절하고 아무것도 바꾸지 않는다`() {
        val before = hasher.verifyCalls

        assertFailsWith<VaultTooLargeException> { change(payload = "a".repeat(SaveVaultUseCase.MAX_PAYLOAD_BYTES + 1)) }

        assertEquals(before, hasher.verifyCalls)
        assertNothingChanged()
    }

    @Test
    fun `볼트가 정확히 5MB면 받는다`() {
        change(payload = "a".repeat(SaveVaultUseCase.MAX_PAYLOAD_BYTES))
    }

    @Test
    fun `입력 형식 오류는 시도 제한 실패로 세지 않는다`() {
        repeat(LoginAttemptLimiter.MAX_FAILURES) {
            assertFailsWith<VaultTooLargeException> { change(payload = "a".repeat(SaveVaultUseCase.MAX_PAYLOAD_BYTES + 1)) }
        }

        change()
    }

    @Test
    fun `토큰의 계정이 사라졌으면 UNAUTHORIZED`() {
        assertFailsWith<UnauthorizedException> {
            useCase.execute(Email.of("ghost@example.com"), PasswordChange(authHashOf(1), authHashOf(3), kdfOf(4), Revision.NONE, EncryptedVault("x")))
        }
    }

    @Test
    fun `같은 요청을 두 번 보내면 두 번째는 옛 인증 해시가 틀려 실패한다`() {
        change()

        assertFailsWith<AuthFailedException> { change(expected = Revision(3)) }
    }
}

/** 실패를 주입할 수 있는 볼트 저장소 */
class FailingVaultRepository(private val delegate: VaultRepository) : VaultRepository by delegate {
    var failCreate = false
    var failDelete = false

    override fun create(vault: StoredVault) {
        if (failCreate) throw IOException("볼트 쓰기 실패")
        delegate.create(vault)
    }

    override fun delete(id: VaultId) {
        if (failDelete) throw IOException("볼트 삭제 실패")
        delegate.delete(id)
    }
}

/** 실패를 주입할 수 있는 계정 저장소 */
class FailingAccountRepository(private val delegate: AccountRepository) : AccountRepository by delegate {
    var failReplace = false
    var refuseReplace = false

    override fun replaceIfMatches(expected: Account, replacement: Account): Boolean {
        if (failReplace) throw IOException("계정 쓰기 실패")
        if (refuseReplace) return false
        return delegate.replaceIfMatches(expected, replacement)
    }
}
