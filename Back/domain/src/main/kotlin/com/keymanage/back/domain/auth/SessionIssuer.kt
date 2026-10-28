package com.keymanage.back.domain.auth

import java.security.SecureRandom
import java.time.Clock
import java.time.Duration

/** 세션 토큰을 발급하고 해시만 저장한다. 유효 기간은 30일이며 사용해도 늘지 않는다 (SEC-002) */
class SessionIssuer(
    private val sessions: SessionRepository,
    private val clock: Clock,
    private val random: SecureRandom = SecureRandom(),
) {

    fun issue(email: Email): IssuedSession {
        val token = SessionTokens.generate(random)
        val expiresAt = clock.instant().plus(TTL)
        sessions.save(Session(SessionTokens.hash(token), email, expiresAt))
        return IssuedSession(token, expiresAt)
    }

    companion object {
        val TTL: Duration = Duration.ofDays(30)
    }
}
