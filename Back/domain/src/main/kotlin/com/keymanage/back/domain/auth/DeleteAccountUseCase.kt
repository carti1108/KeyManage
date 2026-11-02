package com.keymanage.back.domain.auth

import com.keymanage.back.domain.vault.VaultRepository

/**
 * 계정 삭제: 인증 해시를 확인한 뒤 계정을 지워 삭제를 확정하고, 그 계정의 볼트와 세션을 지운다 (SEC-005, ADR-0024).
 *
 * 시도 제한 확인 -> 계정 잠금 -> 인증 해시 검증 -> 계정 삭제(확정) -> 볼트 삭제 -> 세션 전부 삭제 -> 잠금 해제.
 * 확정 전에 실패하면 아무것도 지우지 않는다. 확정 뒤 볼트·세션을 지우지 못해도 성공이다:
 * 계정이 없으면 그 계정의 토큰은 인증되지 않고, 남은 볼트는 어느 계정도 가리키지 않아 시작 때 정리된다.
 * 세션 삭제까지 계정 잠금 안에서 하므로, 같은 이메일로 바로 다시 가입한 계정의 새 세션을 지우지 않는다.
 */
class DeleteAccountUseCase(
    private val accounts: AccountRepository,
    private val vaults: VaultRepository,
    private val sessions: SessionRepository,
    private val hasher: PasswordHasher,
    private val limiter: LoginAttemptLimiter,
) {

    /**
     * @param email 요청 토큰의 계정
     * @throws TooManyAttemptsException 계정이 잠겨 있을 때 (해시 검증을 하지 않음)
     * @throws UnauthorizedException 토큰의 계정이 없을 때
     * @throws AuthFailedException 인증 해시가 틀릴 때 (실패로 기록, 아무것도 지우지 않음)
     */
    fun execute(email: Email, authHash: AuthHash) {
        limiter.checkAllowed(email)
        accounts.withLock(email) {
            val account = accounts.find(email) ?: throw UnauthorizedException()
            verify(account, authHash)
            if (!accounts.deleteIfMatches(account)) throw IllegalStateException("계정 잠금 안에서 계정이 바뀜")
            // 여기부터는 확정 뒤다. 실패해도 경고만 남긴다
            afterCommit("계정 삭제 뒤 볼트를 지우지 못함") { vaults.delete(account.vaultId) }
            afterCommit("계정 삭제 뒤 세션을 지우지 못함") { sessions.deleteAllOf(email) }
        }
    }

    private fun verify(account: Account, authHash: AuthHash) {
        if (!hasher.verify(authHash, account.passwordHash)) {
            limiter.recordFailure(account.email)
            throw AuthFailedException()
        }
        // 성공하면 시도 기록을 지운다. 같은 이메일로 다시 가입한 계정이 옛 실패를 물려받지 않는다
        limiter.recordSuccess(account.email)
    }

    private fun afterCommit(message: String, step: () -> Unit) {
        try {
            step()
        } catch (e: Exception) {
            log.log(System.Logger.Level.WARNING, message, e)
        }
    }

    private companion object {
        val log: System.Logger = System.getLogger(DeleteAccountUseCase::class.java.name)
    }
}
