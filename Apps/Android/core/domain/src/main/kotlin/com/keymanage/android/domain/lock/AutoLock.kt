package com.keymanage.android.domain.lock

import com.keymanage.android.domain.clipboard.Scheduler

/** 자동 잠금 선택지(SEC-003 값 표). "잠그지 않음"은 없다. */
enum class AutoLockOption(val millis: Long, val label: String) {
    IMMEDIATELY(0, "즉시"),
    ONE_MINUTE(60_000, "1분"),
    FIVE_MINUTES(300_000, "5분"),
    FIFTEEN_MINUTES(900_000, "15분"),
    ONE_HOUR(3_600_000, "1시간"),
    ;

    companion object {
        val DEFAULT = FIVE_MINUTES

        /** 저장값을 선택지로 바꾼다. 없거나 선택지가 아니면 기본 5분. */
        fun fromStored(millis: Long?): AutoLockOption = entries.firstOrNull { it.millis == millis } ?: DEFAULT
    }
}

/** 자동 잠금 시간을 기기에 둔다. 계정과 묶지 않고 로그아웃 뒤에도 남는다. 실패해도 던지지 않는다. */
interface AutoLockSettingStore {
    /** 저장값. 없거나 읽지 못하면 기본 5분. */
    fun read(): AutoLockOption

    /** 저장하지 못하면 이번 실행에만 쓴다. */
    fun write(option: AutoLockOption)
}

/**
 * 자동 잠금 타이머(SEC-003 규칙 2). 마지막 조작부터 설정 시간이 지나면 [onTimeout]을 한 번 부른다.
 * 백그라운드에서는 타이머가 늦을 수 있어, 다시 앞으로 오거나 조작할 때 경과 시간으로도 판정한다.
 * "즉시"는 화면에 있는 동안 잠그지 않고 백그라운드로 갈 때 잠근다. 웹 `createAutoLock`과 같은 규칙이다.
 *
 * @param now 단조 증가 시계(밀리초). 기기 시계를 바꿔도 흔들리지 않게 한다.
 */
class AutoLock(
    private val option: AutoLockOption,
    private val now: () -> Long,
    private val scheduler: Scheduler,
    private val onTimeout: () -> Unit,
) {
    private val immediate = option == AutoLockOption.IMMEDIATELY
    private var running = false
    private var lastActivity = 0L
    private var cancel: (() -> Unit)? = null

    /** 잠금이 풀린 뒤 부른다. [isForeground]가 false이고 "즉시"면 바로 잠근다. */
    fun start(isForeground: Boolean = true) {
        running = true
        if (immediate && !isForeground) fire() else restartTimer()
    }

    fun stop() {
        running = false
        clearTimer()
    }

    /** 사용자 조작. 시간이 지난 뒤의 첫 조작은 잠금을 미루지 않고 잠근다. */
    fun activity() {
        if (!running) return
        if (expired()) fire() else restartTimer()
    }

    fun background() {
        if (running && immediate) fire()
    }

    fun foreground() {
        if (running && expired()) fire()
    }

    private fun expired() = !immediate && now() - lastActivity >= option.millis

    private fun restartTimer() {
        clearTimer()
        lastActivity = now()
        if (!immediate) cancel = scheduler.schedule(option.millis) { fire() }
    }

    private fun clearTimer() {
        cancel?.invoke()
        cancel = null
    }

    private fun fire() {
        running = false
        clearTimer()
        onTimeout()
    }
}
