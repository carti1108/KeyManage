package com.keymanage.back.domain.auth

import java.time.Clock

/**
 * 무효 세션을 지운다 (ADR-BE-002 결정 4). 서버가 요청을 받기 전, 시작 때 한 번 실행한다.
 *
 * 만료됐거나, 계정이 없거나, 계정의 볼트 ID와 다른 세션을 지운다. 형식이 틀린 세션은 저장소가 읽을 때 지운다.
 * 계정을 하나라도 읽지 못하면 정상 세션을 계정 없음으로 잘못 볼 수 있으므로 아무것도 지우지 않는다.
 */
class CleanUpSessionsUseCase(
    private val sessions: SessionRepository,
    private val accounts: AccountRepository,
    private val clock: Clock,
) {

    /** @return 지운 세션 수(형식이 틀려 저장소가 지운 것은 세지 않는다) */
    fun execute(): Int {
        try {
            accounts.allVaultIds()
        } catch (e: Exception) {
            log.log(System.Logger.Level.WARNING, "계정을 읽지 못해 세션 정리를 건너뜀", e)
            return 0
        }
        val now = clock.instant()
        var deleted = 0
        for (tokenHash in sessions.allTokenHashes()) {
            try {
                val session = sessions.find(tokenHash) ?: continue
                if (isValid(session, now)) continue
                sessions.delete(tokenHash)
                deleted += 1
            } catch (e: Exception) {
                // 하나를 읽거나 지우지 못해도 나머지는 정리한다. 남은 세션은 인증 때 거절되며 지워진다
                log.log(System.Logger.Level.WARNING, "세션 하나를 정리하지 못함", e)
            }
        }
        return deleted
    }

    private fun isValid(session: Session, now: java.time.Instant): Boolean {
        if (!now.isBefore(session.expiresAt)) return false
        return accounts.find(session.email)?.vaultId == session.vaultId
    }

    private companion object {
        val log: System.Logger = System.getLogger(CleanUpSessionsUseCase::class.java.name)
    }
}
