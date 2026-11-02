package com.keymanage.back.domain.auth

import com.keymanage.back.domain.vault.VaultId
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration

/** 세션 토큰을 발급하고 해시만 저장한다. 유효 기간은 30일이며 사용해도 늘지 않는다 (SEC-002) */
class SessionIssuer(
    private val sessions: SessionRepository,
    private val clock: Clock,
    private val random: SecureRandom = SecureRandom(),
) {

    /** @param vaultId 발급하는 지금 계정이 가리키는 볼트. 계정의 볼트가 바뀌면 이 세션은 무효다 */
    fun issue(email: Email, vaultId: VaultId): IssuedSession {
        val token = SessionTokens.generate(random)
        val expiresAt = clock.instant().plus(TTL)
        sessions.save(Session(SessionTokens.hash(token), email, expiresAt, vaultId))
        return IssuedSession(token, expiresAt)
    }

    companion object {
        val TTL: Duration = Duration.ofDays(30)
    }
}
