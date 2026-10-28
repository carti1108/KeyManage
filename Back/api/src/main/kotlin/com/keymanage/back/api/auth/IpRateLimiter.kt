package com.keymanage.back.api.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * IP별 고정 구간 요청 제한 (SEC-002: 1분에 30회).
 * 구간이 바뀌면 기록을 통째로 비우므로 메모리는 한 구간 동안 들어온 IP 수를 넘지 않는다. 단일 인스턴스 전제다 (ADR-0007)
 */
class IpRateLimiter(
    private val clock: Clock,
    private val limit: Int = 30,
    private val window: Duration = Duration.ofMinutes(1),
) {

    private var windowStart: Instant? = null
    private val counts = HashMap<String, Int>()

    /** @return 허용하면 null, 넘었으면 다음 구간까지 남은 시간 */
    @Synchronized
    fun tryAcquire(ip: String): Duration? {
        val now = clock.instant()
        val windowMillis = window.toMillis()
        val start = Instant.ofEpochMilli(now.toEpochMilli() / windowMillis * windowMillis)
        if (start != windowStart) {
            windowStart = start
            counts.clear()
        }

        val count = (counts[ip] ?: 0) + 1
        if (count > limit) return Duration.between(now, start.plus(window))
        counts[ip] = count
        return null
    }
}
