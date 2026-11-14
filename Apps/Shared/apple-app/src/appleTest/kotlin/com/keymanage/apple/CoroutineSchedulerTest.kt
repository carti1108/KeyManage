package com.keymanage.apple

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class CoroutineSchedulerTest {
    @Test
    fun runsAfterDelay() = runTest {
        val scheduler = CoroutineScheduler(backgroundScope)
        var runs = 0

        scheduler.schedule(30.seconds) { runs += 1 }
        advanceTimeBy(29.seconds)
        runCurrent()
        assertEquals(0, runs)
        advanceTimeBy(1.seconds)
        runCurrent()

        assertEquals(1, runs)
    }

    @Test
    fun cancelledScheduleDoesNotRun() = runTest {
        val scheduler = CoroutineScheduler(backgroundScope)
        var runs = 0

        val cancel = scheduler.schedule(30.seconds) { runs += 1 }
        cancel()
        advanceTimeBy(60.seconds)
        runCurrent()

        assertEquals(0, runs)
    }
}
