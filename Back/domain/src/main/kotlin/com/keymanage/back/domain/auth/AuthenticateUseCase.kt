package com.keymanage.back.domain.auth

import java.time.Clock

/**
 * 요청의 토큰으로 계정을 찾는다. 만료된 세션과, 발급 뒤 계정의 볼트가 바뀐(비밀번호 변경) 세션은 거절하면서 지운다 (SEC-002, SEC-004)
 */
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
        val account = accounts.find(session.email)
        if (account == null || account.vaultId != session.vaultId) {
            sessions.delete(tokenHash)
            throw UnauthorizedException()
        }
        return account
    }
}
