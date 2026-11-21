package com.keymanage.domain.lock

import com.keymanage.domain.testing.FakeTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// 원본: Apps/Web/src/domain/lock/autoLock.test.ts, autoLockSetting.test.ts (SEC-003, ADR-WEB-001·002)
private class Setup(timeout: Duration = DEFAULT_AUTO_LOCK, isVisible: () -> Boolean = { true }) {
    val time = FakeTime()
    val causes = mutableListOf<LockCause>()
    val autoLock = createAutoLock(
        timeout = timeout,
        clock = time,
        scheduler = time,
        isVisible = isVisible,
        onTimeout = { causes += it },
    ).also { it.start() }
    val locks get() = causes.size
}

class AutoLockTest {
    @Test
    fun optionsAreImmediateOneFiveFifteenSixtyMinutes_defaultFive() {
        assertEquals(listOf(Duration.ZERO, 1.minutes, 5.minutes, 15.minutes, 60.minutes), AUTO_LOCK_OPTIONS)
        assertEquals(5.minutes, DEFAULT_AUTO_LOCK)
    }

    @Test
    fun rejectsTimeoutsOutsideOptions_noNeverLock() {
        listOf((-1).milliseconds, 30.seconds, Duration.INFINITE).forEach { timeout ->
            assertFailsWith<InvalidAutoLockTimeoutException>("$timeout") {
                val time = FakeTime()
                createAutoLock(timeout, time, time, onTimeout = {})
            }
        }
    }

    @Test
    fun locksOnceAfterTimeoutSinceLastActivity() {
        val s = Setup()

        s.time.advance(5.minutes - 1.milliseconds)
        assertEquals(0, s.locks)
        s.time.advance(1.milliseconds)
        assertEquals(1, s.locks)
        s.time.advance(10.minutes)
        assertEquals(1, s.locks)
    }

    @Test
    fun activityRestartsTheClock() {
        val s = Setup()

        s.time.advance(4.minutes)
        s.autoLock.activity()
        s.time.advance(4.minutes)

        assertEquals(0, s.locks)
        s.time.advance(1.minutes)
        assertEquals(1, s.locks)
    }

    @Test
    fun delayedBackgroundTimerStillLocksByElapsedTimeOnVisibleOrActivity() {
        val shown = Setup()
        shown.autoLock.hidden()
        shown.time.advanceWithoutTimers(6.minutes)
        shown.autoLock.visible()
        assertEquals(1, shown.locks)

        // 시간이 지난 뒤의 첫 조작은 잠금을 미루지 않고 잠근다
        val touched = Setup()
        touched.time.advanceWithoutTimers(6.minutes)
        touched.autoLock.activity()
        assertEquals(1, touched.locks)
    }

    @Test
    fun immediateLocksWhenHidden_notWhileVisible() {
        val s = Setup(Duration.ZERO)

        s.time.advance(60.minutes)
        assertEquals(0, s.locks)
        s.autoLock.hidden()
        assertEquals(1, s.locks)
    }

    @Test
    fun restartAfterLockMeasuresFromThen() {
        val s = Setup()
        s.time.advance(5.minutes)

        s.autoLock.start()
        s.time.advance(5.minutes - 1.milliseconds)
        assertEquals(1, s.locks)
        s.time.advance(1.milliseconds)
        assertEquals(2, s.locks)
    }

    @Test
    fun suspendedLocksImmediately() {
        val s = Setup()

        s.autoLock.suspended()

        assertEquals(1, s.locks)
    }

    @Test
    fun stoppedDoesNotLock() {
        val s = Setup()

        s.autoLock.stop()
        s.time.advance(60.minutes)
        s.autoLock.visible()
        s.autoLock.activity()

        assertEquals(0, s.locks)
    }
}

class AutoLockCauseTest {
    @Test
    fun visibleTimerExpiryIsTimer() {
        val s = Setup()
        s.time.advance(5.minutes)
        assertEquals(listOf(LockCause.TIMER), s.causes)
    }

    @Test
    fun immediateHiddenIsHidden() {
        val s = Setup(Duration.ZERO)
        s.autoLock.hidden()
        assertEquals(listOf(LockCause.HIDDEN), s.causes)
    }

    @Test
    fun suspendedIsSuspended() {
        val s = Setup()
        s.autoLock.suspended()
        assertEquals(listOf(LockCause.SUSPENDED), s.causes)
    }

    @Test
    fun discoveredOnVisibleOrActivityIsExpired() {
        val first = Setup()
        first.time.advanceWithoutTimers(5.minutes)
        first.autoLock.visible()
        val second = Setup()
        second.time.advanceWithoutTimers(5.minutes)
        second.autoLock.activity()

        assertEquals(listOf(LockCause.EXPIRED), first.causes)
        assertEquals(listOf(LockCause.EXPIRED), second.causes)
    }

    @Test
    fun timerExpiryWhileHiddenIsExpired() {
        val s = Setup(DEFAULT_AUTO_LOCK) { false }
        s.time.advance(5.minutes)
        assertEquals(listOf(LockCause.EXPIRED), s.causes)
    }

    @Test
    fun immediateStartedWhileHiddenLocksAtOnceAsHidden() {
        val s = Setup(Duration.ZERO) { false }
        assertEquals(listOf(LockCause.HIDDEN), s.causes)
    }

    @Test
    fun nonImmediateStartedWhileHiddenStillMeasures() {
        val s = Setup(1.minutes) { false }
        assertEquals(emptyList(), s.causes)
        s.time.advance(1.minutes)
        assertEquals(listOf(LockCause.EXPIRED), s.causes)
    }
}

class AutoLockSettingTest {
    @Test
    fun optionsAreKept() {
        AUTO_LOCK_OPTIONS.forEach { assertEquals(it, parseAutoLockSetting(it.inWholeMilliseconds.toString())) }
        assertEquals(Duration.ZERO, parseAutoLockSetting("0"))
    }

    @Test
    fun anythingElseFallsBackToFiveMinutes() {
        listOf(null, "", "-60000", "-0", "abc", "NaN", "300000.5", "120000", " 60000 ", "6e4", "Infinity", "99999999999999999999")
            .forEach { assertEquals(DEFAULT_AUTO_LOCK, parseAutoLockSetting(it), "$it") }
    }

    @Test
    fun storedFormatIsMillisecondsAsDecimalString() {
        assertEquals("300000", formatAutoLockSetting(5.minutes))
        assertEquals("0", formatAutoLockSetting(Duration.ZERO))
    }
}
