package com.keymanage.android.domain.lock

import com.keymanage.android.domain.clipboard.Scheduler
import kotlin.test.Test
import kotlin.test.assertEquals

class AutoLockTest {
    private var clock = 0L
    private var locks = 0
    private val scheduler = ManualScheduler()

    private fun autoLock(option: AutoLockOption) = AutoLock(option, { clock }, scheduler) { locks++ }

    @Test
    fun `저장값이 선택지가 아니면 기본 5분`() {
        assertEquals(AutoLockOption.FIVE_MINUTES, AutoLockOption.fromStored(null))
        assertEquals(AutoLockOption.FIVE_MINUTES, AutoLockOption.fromStored(123))
        assertEquals(AutoLockOption.ONE_HOUR, AutoLockOption.fromStored(3_600_000))
    }

    @Test
    fun `마지막 조작부터 설정 시간이 지나면 한 번 잠근다`() {
        // Given
        val lock = autoLock(AutoLockOption.ONE_MINUTE)
        lock.start()
        clock = 30_000
        lock.activity()

        // When: 조작 뒤 1분이 지나 타이머가 끝난다
        clock = 90_000
        scheduler.fireLast()

        // Then
        assertEquals(60_000L, scheduler.delays.last())
        assertEquals(1, locks)
        lock.activity()
        assertEquals(1, locks)
    }

    @Test
    fun `시간이 지난 뒤의 첫 조작은 잠금을 미루지 않는다`() {
        val lock = autoLock(AutoLockOption.ONE_MINUTE)
        lock.start()
        clock = 60_000
        lock.activity()
        assertEquals(1, locks)
    }

    @Test
    fun `앞으로 돌아왔을 때 시간이 지났으면 잠근다`() {
        val lock = autoLock(AutoLockOption.FIVE_MINUTES)
        lock.start()
        lock.background()
        clock = 299_999
        lock.foreground()
        assertEquals(0, locks)
        clock = 300_000
        lock.foreground()
        assertEquals(1, locks)
    }

    @Test
    fun `즉시는 화면에 있는 동안 잠그지 않고 백그라운드로 갈 때 잠근다`() {
        val lock = autoLock(AutoLockOption.IMMEDIATELY)
        lock.start()
        clock = 10_000_000
        lock.activity()
        assertEquals(0, locks)
        assertEquals(emptyList(), scheduler.delays)
        lock.background()
        assertEquals(1, locks)
    }

    @Test
    fun `즉시 설정으로 백그라운드에서 시작하면 바로 잠근다`() {
        autoLock(AutoLockOption.IMMEDIATELY).start(isForeground = false)
        assertEquals(1, locks)
    }

    @Test
    fun `멈추면 잠그지 않는다`() {
        val lock = autoLock(AutoLockOption.ONE_MINUTE)
        lock.start()
        lock.stop()
        clock = 120_000
        lock.foreground()
        lock.activity()
        assertEquals(0, locks)
        assertEquals(1, scheduler.cancelled)
    }

    private class ManualScheduler : Scheduler {
        val delays = mutableListOf<Long>()
        var cancelled = 0
        private val actions = mutableListOf<() -> Unit>()

        override fun schedule(delayMillis: Long, action: () -> Unit): () -> Unit {
            delays += delayMillis
            actions += action
            return {
                cancelled++
                actions.remove(action)
            }
        }

        fun fireLast() = actions.last().invoke()
    }
}
