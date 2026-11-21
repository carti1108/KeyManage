package com.keymanage.domain.session

import com.keymanage.domain.lock.Scheduler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 동기화 대기 내용을 다시 보내는 간격 (VLT-002 값 표) */
val PENDING_SYNC_RETRY_INTERVAL: Duration = 30.seconds

/**
 * 동기화 대기 내용을 네트워크가 다시 연결되면 바로, 그 밖에는 [PENDING_SYNC_RETRY_INTERVAL]마다 다시 보낸다 (VLT-002 규칙 6).
 * 잠금 해제 직후 재전송은 세션이 맡는다. 대기가 없거나 분명한 거절 뒤면 [retry]가 요청 없이 끝난다(VaultSession.retryPendingSync).
 * 원본: Apps/Web/src/app/pendingSyncWiring.ts. 앱 진입점(Android·Apple)이 메인 스레드에서 시작하고 멈춘다
 *
 * @param retry 재전송을 띄운다. 결과를 기다리지 않는다
 */
class PendingSyncRetry(
    private val retry: () -> Unit,
    private val isOffline: () -> Boolean,
    private val subscribeNetwork: (() -> Unit) -> () -> Unit,
    private val scheduler: Scheduler,
) {
    private var cancelTimer: (() -> Unit)? = null
    private var unsubscribe: (() -> Unit)? = null
    private var wasOffline = false

    fun start() {
        if (unsubscribe != null) return
        wasOffline = isOffline()
        unsubscribe = subscribeNetwork(::onNetworkChange)
        scheduleNext()
    }

    fun stop() {
        cancelTimer?.invoke()
        cancelTimer = null
        unsubscribe?.invoke()
        unsubscribe = null
    }

    private fun onNetworkChange() {
        val offline = isOffline()
        if (wasOffline && !offline) retry()
        wasOffline = offline
    }

    private fun scheduleNext() {
        cancelTimer = scheduler.schedule(PENDING_SYNC_RETRY_INTERVAL) {
            retry()
            if (unsubscribe != null) scheduleNext()
        }
    }
}
