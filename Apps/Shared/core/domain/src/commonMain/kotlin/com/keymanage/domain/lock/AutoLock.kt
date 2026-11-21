package com.keymanage.domain.lock

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// 자동 잠금 타이머 (SEC-003). 마지막 조작부터 설정 시간이 지나면 onTimeout을 한 번 부른다
// 플랫폼이 백그라운드에서 타이머를 늦추므로, 타이머만 믿지 않고 다시 보이거나 조작할 때 경과 시간으로도 판정한다
// 원본: Apps/Web/src/domain/lock/autoLock.ts

/** 즉시·1분·5분·15분·1시간. "잠그지 않음"은 없다 (SEC-003) */
val AUTO_LOCK_OPTIONS: List<Duration> = listOf(Duration.ZERO, 1.minutes, 5.minutes, 15.minutes, 60.minutes)

val DEFAULT_AUTO_LOCK: Duration = 5.minutes

fun isAutoLockOption(timeout: Duration): Boolean = timeout in AUTO_LOCK_OPTIONS

/** 정해진 선택지가 아닌 자동 잠금 시간 */
class InvalidAutoLockTimeoutException(timeout: Duration) :
    IllegalArgumentException("자동 잠금 시간은 정해진 선택지 중 하나여야 함: $timeout")

/** 지연 실행. 플랫폼 타이머(브라우저 setTimeout, 코루틴 delay)가 구현한다 */
fun interface Scheduler {
    /** @return 예약 취소 함수 */
    fun schedule(delay: Duration, run: () -> Unit): () -> Unit
}

/**
 * 잠금 원인. 보이는 화면에서 타이머가 끝난 TIMER만 다른 탭에도 퍼뜨린다 (SEC-003, ADR-WEB-001).
 * HIDDEN: "즉시" 설정에서 숨겨짐, SUSPENDED: 동결·떠남,
 * EXPIRED: 숨겨진 동안 타이머가 끝났거나 다시 보이거나 조작할 때 발견한 만료
 */
enum class LockCause { TIMER, HIDDEN, SUSPENDED, EXPIRED }

interface AutoLock {
    /** 잠금 해제 뒤 부른다. 지금부터 시간을 잰다 */
    fun start()
    fun stop()

    /** 사용자 조작 */
    fun activity()

    /** 백그라운드로 감 */
    fun hidden()

    /** 다시 보임 */
    fun visible()

    /** 동결·페이지 떠남. 타이머가 멈출 수 있으므로 바로 잠근다 */
    fun suspended()
}

/** @throws InvalidAutoLockTimeoutException */
fun createAutoLock(
    timeout: Duration,
    clock: Clock,
    scheduler: Scheduler,
    /** 지금 화면에 보이는지. 숨겨진 화면의 타이머 만료는 다른 탭에 퍼뜨리지 않는다 */
    isVisible: () -> Boolean = { true },
    onTimeout: (LockCause) -> Unit,
): AutoLock {
    if (!isAutoLockOption(timeout)) throw InvalidAutoLockTimeoutException(timeout)
    return TimerAutoLock(timeout, clock, scheduler, isVisible, onTimeout)
}

private class TimerAutoLock(
    private val timeout: Duration,
    private val clock: Clock,
    private val scheduler: Scheduler,
    private val isVisible: () -> Boolean,
    private val onTimeout: (LockCause) -> Unit,
) : AutoLock {
    private val immediate = timeout == Duration.ZERO
    private var running = false
    private var lastActivity = Instant.DISTANT_PAST
    private var cancel: (() -> Unit)? = null

    private fun clearTimer() {
        cancel?.invoke()
        cancel = null
    }

    private fun fire(cause: LockCause) {
        running = false
        clearTimer()
        onTimeout(cause)
    }

    private fun restartTimer() {
        clearTimer()
        lastActivity = clock.now()
        // "즉시"는 보이는 동안 잠그지 않고 백그라운드로 갈 때만 잠근다
        if (!immediate) cancel = scheduler.schedule(timeout) { fire(if (isVisible()) LockCause.TIMER else LockCause.EXPIRED) }
    }

    private fun expired() = !immediate && clock.now() - lastActivity >= timeout

    override fun start() {
        running = true
        // "즉시"는 숨겨지는 순간 잠근다. 이미 숨겨진 채 시작하면 숨김 알림이 다시 오지 않으므로 바로 잠근다
        if (immediate && !isVisible()) fire(LockCause.HIDDEN) else restartTimer()
    }

    override fun stop() {
        running = false
        clearTimer()
    }

    override fun activity() {
        if (!running) return
        // 시간이 지난 뒤의 첫 조작이 잠금을 미루지 않게 먼저 확인한다
        if (expired()) fire(LockCause.EXPIRED) else restartTimer()
    }

    override fun hidden() {
        if (running && immediate) fire(LockCause.HIDDEN)
    }

    override fun visible() {
        if (running && expired()) fire(LockCause.EXPIRED)
    }

    override fun suspended() {
        if (running) fire(LockCause.SUSPENDED)
    }
}
