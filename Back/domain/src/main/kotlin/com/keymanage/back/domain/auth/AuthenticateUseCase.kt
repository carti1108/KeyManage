package com.keymanage.back.domain.auth

import java.time.Clock

/** 요청의 토큰으로 계정을 찾는다. 만료된 세션은 거절하면서 지운다 (SEC-002) */
class AuthenticateUseCase(
    private val sessions: SessionRepository,
    private val accounts: AccountRepository,
    private val clock: Clock,
) {

    /** @throws UnauthorizedException 토큰이 없거나 만료·폐기됐을 때 */
    fun execute(token: String): Account {
        val tokenHash = SessionTokens.hash(token)
        val session = sessions.find(tokenHash) ?: throw UnauthorizedException()
        if (!clock.instant().isBefore(session.expiresAt)) {
            sessions.delete(tokenHash)
            throw UnauthorizedException()
        }
        return accounts.find(session.email) ?: throw UnauthorizedException()
    }
}
