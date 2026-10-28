package com.keymanage.back.domain.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 계정 단위 로그인 시도 제한 (SEC-002): 15분 안에 5번 실패하면 15분 동안 잠그고, 성공하면 초기화한다.
 * 없는 이메일도 같은 방식으로 세어 계정 존재 여부가 드러나지 않게 한다. 상태는 메모리에만 두므로 단일 인스턴스 전제다 (ADR-0007)
 */
class LoginAttemptLimiter(private val clock: Clock) {

    private class State {
        val failures = ArrayDeque<Instant>()
        var lockedUntil: Instant? = null
    }

    private val states = ConcurrentHashMap<Email, State>()

    /** @throws TooManyAttemptsException 잠겨 있을 때 (남은 시간 포함) */
    fun checkAllowed(email: Email) {
        val state = states[email] ?: return
        val now = clock.instant()
        synchronized(state) {
            val lockedUntil = state.lockedUntil ?: return
            if (now.isBefore(lockedUntil)) throw TooManyAttemptsException(Duration.between(now, lockedUntil))
            // 잠금이 끝난 항목은 실패 기록도 비어 있으므로 통째로 지워 메모리에 남기지 않는다
            states.remove(email, state)
        }
    }

    fun recordFailure(email: Email) {
        val now = clock.instant()
        val state = states.computeIfAbsent(email) { State() }
        synchronized(state) {
            state.failures.addLast(now)
            while (!state.failures.first().isAfter(now.minus(WINDOW))) state.failures.removeFirst()
            if (state.failures.size >= MAX_FAILURES) {
                state.lockedUntil = now.plus(LOCK_DURATION)
                state.failures.clear()
            }
        }
    }

    fun recordSuccess(email: Email) {
        states.remove(email)
    }

    companion object {
        const val MAX_FAILURES = 5
        val WINDOW: Duration = Duration.ofMinutes(15)
        val LOCK_DURATION: Duration = Duration.ofMinutes(15)
    }
}
