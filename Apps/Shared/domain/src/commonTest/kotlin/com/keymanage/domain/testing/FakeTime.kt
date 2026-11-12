package com.keymanage.domain.testing

import com.keymanage.domain.lock.Scheduler
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * 시간을 직접 움직이는 시계와 타이머. 원본 테스트의 fakeTime·fakeScheduler와 같다.
 * 브라우저처럼 백그라운드에서 타이머가 늦게 도는 상황도 [advanceWithoutTimers]로 흉내 낸다
 */
class FakeTime(start: Instant = Instant.fromEpochMilliseconds(0)) : Clock, Scheduler {
    private var current = start
    private val tasks = mutableListOf<Task>()

    private class Task(val at: Instant, val run: () -> Unit) {
        var cancelled = false
    }

    override fun now(): Instant = current

    override fun schedule(delay: Duration, run: () -> Unit): () -> Unit {
        val task = Task(current + delay, run)
        tasks += task
        return { task.cancelled = true }
    }

    /** 시간을 흘리고, 그 사이 예정된 타이머를 실행한다 */
    fun advance(by: Duration) {
        current += by
        val due = tasks.filter { !it.cancelled && it.at <= current }
        tasks.removeAll(due)
        due.forEach { it.run() }
    }

    /** 타이머를 실행하지 않고 시간만 흘린다 (백그라운드 탭의 타이머 지연) */
    fun advanceWithoutTimers(by: Duration) {
        current += by
    }
}
