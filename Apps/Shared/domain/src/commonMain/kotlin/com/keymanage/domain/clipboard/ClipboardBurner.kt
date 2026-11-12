package com.keymanage.domain.clipboard

import com.keymanage.domain.lock.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// 클립보드 자동 소각 (COM-001). 복사하고 30초 뒤, 또는 잠기면 바로 빈 값으로 덮어쓴다
// 웹은 탭이 활성일 때만 쓸 수 있어 비우기가 실패할 수 있다. 실패하면 기다렸다가 retry()로 다시 비운다
// 원본: Apps/Web/src/domain/clipboard/clipboardBurner.ts. 생명주기처럼 메인 스레드 하나에서 부른다고 가정한다

val BURN_AFTER: Duration = 30.seconds

/** 클립보드 쓰기(Clipboard API, ClipboardManager, UIPasteboard) */
fun interface ClipboardWriter {
    suspend fun write(text: String)
}

sealed interface BurnState {
    data object Idle : BurnState

    /** expiresAt: 비울 시각 */
    data class Counting(val expiresAt: Instant) : BurnState

    /** 비우지 못해 다음 기회(화면으로 돌아옴)를 기다림 */
    data object PendingBurn : BurnState
}

class ClipboardBurner(
    private val clipboard: ClipboardWriter,
    private val clock: Clock,
    private val scheduler: Scheduler,
    /** 타이머가 끝났을 때 비우기를 띄울 곳. 원본은 결과를 기다리지 않고 비웠다 */
    private val scope: CoroutineScope,
) {
    private val current = MutableStateFlow<BurnState>(BurnState.Idle)
    val state: StateFlow<BurnState> = current.asStateFlow()

    private var cancelTimer: (() -> Unit)? = null

    // 복사할 때마다 늘린다. 늦게 끝난 비우기가 그 뒤에 복사한 새 값의 상태를 덮어쓰지 않게 한다
    private var generation = 0

    // 쓰기가 끝나기 전에도 잠금이 올 수 있다. 그때는 상태가 아직 Idle이라 따로 표시해 둔다
    private var copying = false
    private var clearedWhileCopying = false

    /** @throws Exception 클립보드에 쓰지 못했을 때. 이때는 타이머를 시작하지 않는다 */
    suspend fun copy(text: String) {
        generation += 1
        val mine = generation
        copying = true
        clearedWhileCopying = false
        try {
            clipboard.write(text)
        } finally {
            if (mine == generation) copying = false
        }
        // 쓰는 동안 잠겼으면 이미 비웠다. 더 나중에 시작한 복사가 있으면 그쪽 타이머를 쓴다
        if (clearedWhileCopying || mine != generation) return
        stopTimer()
        val expiresAt = clock.now() + BURN_AFTER
        cancelTimer = scheduler.schedule(BURN_AFTER) { scope.launch { burn() } }
        current.value = BurnState.Counting(expiresAt)
    }

    /** 화면으로 돌아왔을 때 부른다. 비우지 못한 것이 있으면 다시 비운다 */
    suspend fun retry() {
        if (current.value == BurnState.PendingBurn) burn()
    }

    /** 잠길 때 부른다. 30초를 기다리지 않고 바로 비운다 */
    suspend fun clear() {
        if (copying) {
            // 쓰기가 먼저 시작됐으므로 빈 값이 그 뒤에 쓰인다
            clearedWhileCopying = true
            burn()
            return
        }
        if (current.value != BurnState.Idle) burn()
    }

    private fun stopTimer() {
        cancelTimer?.invoke()
        cancelTimer = null
    }

    private suspend fun burn() {
        stopTimer()
        val startedAt = generation
        try {
            clipboard.write("")
            if (startedAt == generation) current.value = BurnState.Idle
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (startedAt == generation) current.value = BurnState.PendingBurn
        }
    }
}
