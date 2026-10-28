package com.keymanage.back.domain.auth

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SessionUseCaseTests {

    private val accounts = InMemoryAccountRepository()
    private val sessions = InMemorySessionRepository()
    private val clock = MutableClock()
    private val email = Email.of("user@example.com")
    private val token = RegisterUseCase(accounts, FakePasswordHasher(), SessionIssuer(sessions, clock), registrationEnabled = true)
        .execute(email, authHashOf(1), kdfOf(2)).token

    private val authenticate = AuthenticateUseCase(sessions, accounts, clock)
    private val logout = LogoutUseCase(sessions)

    @Test
    fun `유효한 토큰은 계정을 돌려준다`() {
        assertEquals(email, authenticate.execute(token).email)
    }

    @Test
    fun `모르는 토큰은 거절한다`() {
        assertFailsWith<UnauthorizedException> { authenticate.execute("unknown") }
    }

    @Test
    fun `30일이 지난 토큰은 거절하고 지운다`() {
        clock.advance(Duration.ofDays(30))

        assertFailsWith<UnauthorizedException> { authenticate.execute(token) }

        assertNull(sessions.find(SessionTokens.hash(token)))
    }

    @Test
    fun `만료 직전 토큰은 받아들이고 사용해도 만료일이 늘지 않는다`() {
        clock.advance(Duration.ofDays(30).minusSeconds(1))
        authenticate.execute(token)

        clock.advance(Duration.ofSeconds(1))
        assertFailsWith<UnauthorizedException> { authenticate.execute(token) }
    }

    @Test
    fun `로그아웃한 토큰은 거절하고 다시 로그아웃해도 실패하지 않는다`() {
        logout.execute(token)

        assertFailsWith<UnauthorizedException> { authenticate.execute(token) }
        logout.execute(token)
    }
}
