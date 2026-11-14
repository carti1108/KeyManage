package com.keymanage.apple

import com.keymanage.domain.lock.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

// 자동 잠금·클립보드 소각 타이머(Scheduler 포트). 진입점이 메인 스레드 범위를 넘겨 run이 메인 스레드에서 돈다
class CoroutineScheduler(private val scope: CoroutineScope) : Scheduler {
    override fun schedule(delay: Duration, run: () -> Unit): () -> Unit {
        val job = scope.launch {
            delay(delay)
            run()
        }
        return { job.cancel() }
    }
}
