package com.keymanage.domain.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PlatformPortsTest {
    @Test
    fun secureRandomBytes_returnsRequestedSize_andDiffersBetweenCalls() {
        val random = SecureRandomBytes
        val first = random.nextBytes(32)
        assertEquals(32, first.size)
        assertFalse(first.contentEquals(random.nextBytes(32)))
    }

    @Test
    fun secureRandomBytes_rejectsNegativeSize() {
        assertFailsWith<IllegalArgumentException> { SecureRandomBytes.nextBytes(-1) }
    }

    // 테스트는 RandomBytes·Clock을 가짜로 바꿔 끼워 결과를 고정한다
    @Test
    fun fakesCanReplaceRandomAndClock() {
        val random = RandomBytes { size -> ByteArray(size) { 7 } }
        val start = Instant.fromEpochSeconds(1_700_000_000)
        val clock = FixedClock(start)

        assertEquals(listOf<Byte>(7, 7, 7), random.nextBytes(3).toList())
        clock.advance(5.seconds)
        assertEquals(start + 5.seconds, clock.now())
    }

    private class FixedClock(private var current: Instant) : Clock {
        override fun now(): Instant = current
        fun advance(by: kotlin.time.Duration) {
            current += by
        }
    }
}
