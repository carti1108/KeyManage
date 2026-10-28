package com.keymanage.back.domain.auth

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegisterUseCaseTests {

    private val accounts = InMemoryAccountRepository()
    private val sessions = InMemorySessionRepository()
    private val hasher = FakePasswordHasher()
    private val clock = MutableClock()
    private val email = Email.of("user@example.com")

    private fun useCase(registrationEnabled: Boolean = true) =
        RegisterUseCase(accounts, hasher, SessionIssuer(sessions, clock), registrationEnabled)

    @Test
    fun `가입하면 계정을 만들고 30일짜리 세션 토큰을 발급한다`() {
        val issued = useCase().execute(email, authHashOf(1), kdfOf(2))

        val account = assertNotNull(accounts.find(email))
        assertEquals(kdfOf(2), account.kdf)
        assertEquals(clock.instant().plus(Duration.ofDays(30)), issued.expiresAt)
        assertEquals(email, sessions.find(SessionTokens.hash(issued.token))?.email)
    }

    @Test
    fun `저장되는 값에는 인증 해시와 토큰 원문이 없다`() {
        val issued = useCase().execute(email, authHashOf(1), kdfOf(2))

        val account = assertNotNull(accounts.find(email))
        assertEquals(hasher.hash(authHashOf(1)), account.passwordHash)
        assertFalse(issued.token in sessions.sessions.keys)
    }

    @Test
    fun `가입마다 서로 다른 볼트와 토큰을 발급한다`() {
        val first = useCase().execute(email, authHashOf(1), kdfOf(2))
        val second = useCase().execute(Email.of("other@example.com"), authHashOf(1), kdfOf(2))

        assertTrue(first.token != second.token)
        assertTrue(accounts.find(email)?.vaultId != accounts.find(Email.of("other@example.com"))?.vaultId)
    }

    @Test
    fun `이미 가입된 이메일이면 거절하고 기존 계정은 그대로다`() {
        useCase().execute(email, authHashOf(1), kdfOf(2))

        assertFailsWith<EmailAlreadyRegisteredException> { useCase().execute(email, authHashOf(9), kdfOf(9)) }

        assertEquals(kdfOf(2), accounts.find(email)?.kdf)
    }

    @Test
    fun `가입이 꺼져 있으면 거절한다`() {
        assertFailsWith<RegistrationDisabledException> { useCase(registrationEnabled = false).execute(email, authHashOf(1), kdfOf(2)) }

        assertNull(accounts.find(email))
    }

    @Test
    fun `같은 이메일로 동시에 가입하면 한 건만 성공한다`() {
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val results = (1..8).map {
            executor.submit<Boolean> {
                start.await()
                runCatching { useCase().execute(email, authHashOf(it), kdfOf(it)) }.isSuccess
            }
        }

        start.countDown()
        val successes = results.count { it.get(5, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(1, successes)
    }
}
