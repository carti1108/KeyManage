package com.keymanage.android.domain.clipboard

/** 시스템 클립보드. 실패하면 예외를 던진다. */
interface ClipboardPort {
    /** 민감한 값으로 표시해 복사한다(미리 보기에서 가림). */
    fun copySecret(text: String)

    /** 빈 값으로 덮어쓴다. */
    fun clear()
}

/** 지연 실행. 돌려준 함수를 부르면 취소된다. */
fun interface Scheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): () -> Unit
}

/**
 * 클립보드 자동 비우기(COM-001). 복사하고 30초 뒤, 또는 잠기면 바로 비운다.
 * 그 전에 사용자가 다른 텍스트를 복사하면 타이머를 무효로 한다(규칙 2).
 */
class ClipboardBurner(
    private val clipboard: ClipboardPort,
    private val scheduler: Scheduler,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var cancelTimer: (() -> Unit)? = null

    /** 비울 시각(밀리초). 기다리는 값이 없으면 null. */
    var expiresAtMillis: Long? = null
        private set

    /** 남은 초(올림). 기다리는 값이 없으면 null. */
    fun remainingSeconds(): Int? = expiresAtMillis?.let { ((it - now() + 999) / 1000).toInt().coerceAtLeast(0) }

    /** 복사하고 30초 타이머를 새로 건다. 복사에 실패하면 타이머를 걸지 않고 예외를 그대로 던진다. */
    fun copy(text: String) {
        clipboard.copySecret(text)
        stopTimer()
        expiresAtMillis = now() + BURN_AFTER_MILLIS
        cancelTimer = scheduler.schedule(BURN_AFTER_MILLIS) { burn() }
    }

    /** 이 앱이 아닌 곳에서 클립보드가 바뀌었다. 사용자가 복사한 값을 지우지 않도록 타이머만 멈춘다. */
    fun onExternalChange() = stopTimer()

    /** 잠길 때 30초를 기다리지 않고 바로 비운다(규칙 3). 기다리는 값이 없으면 아무것도 하지 않는다. */
    fun burnNow() {
        if (expiresAtMillis != null) burn()
    }

    private fun burn() {
        stopTimer()
        runCatching { clipboard.clear() }
    }

    private fun stopTimer() {
        cancelTimer?.invoke()
        cancelTimer = null
        expiresAtMillis = null
    }

    companion object {
        /** 클립보드 유효 시간. 정확히 30초(COM-001 값 표). */
        const val BURN_AFTER_MILLIS = 30_000L
    }
}
