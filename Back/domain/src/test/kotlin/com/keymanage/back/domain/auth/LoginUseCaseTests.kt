package com.keymanage.back.domain.auth

import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoginUseCaseTests {

    private val accounts = InMemoryAccountRepository()
    private val sessions = InMemorySessionRepository()
    private val hasher = FakePasswordHasher()
    private val clock = MutableClock()
    private val issuer = SessionIssuer(sessions, clock)
    private val useCase = LoginUseCase(accounts, hasher, issuer, LoginAttemptLimiter(clock))
    private val email = Email.of("user@example.com")

    @BeforeTest
    fun register() {
        RegisterUseCase(accounts, hasher, issuer, registrationEnabled = true).execute(email, authHashOf(1), kdfOf(2))
    }

    private fun failOnce() = assertFailsWith<AuthFailedException> { useCase.execute(email, authHashOf(9)) }

    @Test
    fun `올바른 인증 해시면 새 세션 토큰을 발급한다`() {
        val issued = useCase.execute(email, authHashOf(1))

        assertEquals(email, sessions.find(SessionTokens.hash(issued.token))?.email)
    }

    @Test
    fun `틀린 인증 해시와 없는 이메일은 같은 실패이고 둘 다 해시 검증을 한 번씩 한다`() {
        val before = hasher.verifyCalls

        failOnce()
        assertFailsWith<AuthFailedException> { useCase.execute(Email.of("ghost@example.com"), authHashOf(1)) }

        assertEquals(before + 2, hasher.verifyCalls)
    }

    @Test
    fun `15분 안에 5번 틀리면 올바른 해시로도 15분 동안 로그인할 수 없다`() {
        repeat(5) { failOnce() }

        val error = assertFailsWith<TooManyAttemptsException> { useCase.execute(email, authHashOf(1)) }
        assertEquals(Duration.ofMinutes(15), error.retryAfter)

        clock.advance(Duration.ofMinutes(15))
        useCase.execute(email, authHashOf(1))
    }

    @Test
    fun `잠긴 동안에는 해시 검증을 하지 않는다`() {
        repeat(5) { failOnce() }
        val before = hasher.verifyCalls

        assertFailsWith<TooManyAttemptsException> { useCase.execute(email, authHashOf(1)) }

        assertEquals(before, hasher.verifyCalls)
    }

    @Test
    fun `남은 잠금 시간을 알려준다`() {
        repeat(5) { failOnce() }
        clock.advance(Duration.ofMinutes(10))

        val error = assertFailsWith<TooManyAttemptsException> { useCase.execute(email, authHashOf(1)) }

        assertEquals(Duration.ofMinutes(5), error.retryAfter)
    }

    @Test
    fun `15분이 지난 실패는 세지 않는다`() {
        repeat(4) { failOnce() }
        clock.advance(Duration.ofMinutes(16))

        failOnce()
        useCase.execute(email, authHashOf(1))
    }

    @Test
    fun `로그인에 성공하면 실패 횟수를 초기화한다`() {
        repeat(4) { failOnce() }
        useCase.execute(email, authHashOf(1))

        repeat(4) { failOnce() }
        useCase.execute(email, authHashOf(1))
    }

    @Test
    fun `없는 이메일도 같은 방식으로 잠긴다`() {
        val ghost = Email.of("ghost@example.com")
        repeat(5) { assertFailsWith<AuthFailedException> { useCase.execute(ghost, authHashOf(1)) } }

        assertFailsWith<TooManyAttemptsException> { useCase.execute(ghost, authHashOf(1)) }
    }
}
