package com.keymanage.back.domain.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 계정 단위 로그인 시도 제한 (SEC-002): 15분 안에 5번 실패하면 15분 동안 잠그고, 성공하면 초기화한다.
 * 없는 이메일도 같은 방식으로 세어 계정 존재 여부가 드러나지 않게 한다. 상태는 메모리에만 두므로 단일 인스턴스 전제다 (ADR-0007)
 *
 * 없는 이메일로 실패를 쌓아 메모리를 채우지 못하도록, 추적 항목이 [maxTracked]를 넘으면 잠기지 않았고 최근 실패도 없는 항목을 지운다.
 * 항목의 변경과 삭제는 모두 ConcurrentHashMap의 compute 계열 안에서 해, 정리와 실패 기록이 엇갈려 기록이 사라지지 않게 한다.
 */
class LoginAttemptLimiter(private val clock: Clock, private val maxTracked: Int = 10_000) {

    private class State {
        val failures = ArrayDeque<Instant>()
        var lockedUntil: Instant? = null
    }

    private val states = ConcurrentHashMap<Email, State>()

    @Volatile
    private var lastSweep: Instant = Instant.MIN

    /** @throws TooManyAttemptsException 잠겨 있을 때 (남은 시간 포함) */
    fun checkAllowed(email: Email) {
        val now = clock.instant()
        var retryAfter: Duration? = null
        states.computeIfPresent(email) { _, state ->
            val lockedUntil = state.lockedUntil
            when {
                lockedUntil == null -> state
                now.isBefore(lockedUntil) -> state.also { retryAfter = Duration.between(now, lockedUntil) }
                // 잠금이 끝났으면 잠금만 풀고, 그 뒤 쌓인 실패가 없으면 항목을 지워 메모리에 남기지 않는다
                else -> {
                    state.lockedUntil = null
                    if (state.failures.isEmpty()) null else state
                }
            }
        }
        retryAfter?.let { throw TooManyAttemptsException(it) }
    }

    fun recordFailure(email: Email) {
        val now = clock.instant()
        states.compute(email) { _, existing ->
            (existing ?: State()).apply {
                failures.addLast(now)
                while (!failures.first().isAfter(now.minus(WINDOW))) failures.removeFirst()
                if (failures.size >= MAX_FAILURES) {
                    lockedUntil = now.plus(LOCK_DURATION)
                    failures.clear()
                }
            }
        }
        if (states.size > maxTracked) sweep(now)
    }

    fun recordSuccess(email: Email) {
        states.remove(email)
    }

    internal fun trackedCount(): Int = states.size

    private fun sweep(now: Instant) {
        // 상한을 넘은 채 최근 실패만 쌓이는 동안 실패마다 전체를 훑지 않도록 1분에 한 번만 정리한다
        if (now.isBefore(lastSweep.plus(SWEEP_INTERVAL))) return
        lastSweep = now
        states.keys.forEach { email ->
            states.computeIfPresent(email) { _, state -> if (isStale(state, now)) null else state }
        }
    }

    private fun isStale(state: State, now: Instant): Boolean {
        val lockedUntil = state.lockedUntil
        if (lockedUntil != null && now.isBefore(lockedUntil)) return false
        val lastFailure = state.failures.lastOrNull() ?: return true
        return !lastFailure.isAfter(now.minus(WINDOW))
    }

    companion object {
        const val MAX_FAILURES = 5
        val WINDOW: Duration = Duration.ofMinutes(15)
        val LOCK_DURATION: Duration = Duration.ofMinutes(15)
        private val SWEEP_INTERVAL: Duration = Duration.ofMinutes(1)
    }
}
