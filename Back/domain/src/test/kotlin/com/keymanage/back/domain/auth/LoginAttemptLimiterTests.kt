package com.keymanage.back.domain.auth

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 없는 이메일로 실패를 쌓아 메모리를 채우지 못하도록 오래된 기록을 정리한다 */
class LoginAttemptLimiterTests {

    private val clock = MutableClock()
    private val limiter = LoginAttemptLimiter(clock, maxTracked = 3)

    private fun email(n: Int) = Email.of("user$n@example.com")

    @Test
    fun `추적 항목이 상한을 넘으면 15분이 지난 실패 기록을 지운다`() {
        repeat(3) { limiter.recordFailure(email(it)) }
        clock.advance(Duration.ofMinutes(16))

        limiter.recordFailure(email(99))

        assertEquals(1, limiter.trackedCount())
    }

    @Test
    fun `정리할 때 잠긴 계정과 최근 실패는 남긴다`() {
        repeat(5) { limiter.recordFailure(email(0)) }
        clock.advance(Duration.ofMinutes(10))
        limiter.recordFailure(email(1))
        limiter.recordFailure(email(2))

        limiter.recordFailure(email(3))

        assertEquals(4, limiter.trackedCount())
        assertFailsWith<TooManyAttemptsException> { limiter.checkAllowed(email(0)) }
    }

    @Test
    fun `잠금이 풀린 뒤의 실패는 잠금 확인을 거쳐도 사라지지 않는다`() {
        repeat(5) { limiter.recordFailure(email(0)) }
        clock.advance(Duration.ofMinutes(15))
        repeat(4) { limiter.recordFailure(email(0)) }

        limiter.checkAllowed(email(0))
        limiter.recordFailure(email(0))

        assertFailsWith<TooManyAttemptsException> { limiter.checkAllowed(email(0)) }
    }

    @Test
    fun `상한 이하에서는 정리하지 않는다`() {
        limiter.recordFailure(email(0))
        clock.advance(Duration.ofMinutes(16))

        limiter.recordFailure(email(1))

        assertEquals(2, limiter.trackedCount())
    }
}
