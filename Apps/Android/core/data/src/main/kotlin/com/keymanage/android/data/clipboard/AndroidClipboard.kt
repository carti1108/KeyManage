package com.keymanage.android.data.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import com.keymanage.android.domain.clipboard.ClipboardPort
import com.keymanage.android.domain.clipboard.Scheduler

/**
 * 시스템 클립보드 어댑터(COM-001). 민감한 값으로 표시해 Android 13+ 복사 미리 보기에서 가린다.
 * 이 앱이 쓰지 않은 변경은 [onExternalChange]로 알린다. 메인 스레드에서만 부른다.
 */
class AndroidClipboard(
    private val manager: ClipboardManager,
    private val onExternalChange: () -> Unit,
) : ClipboardPort {
    private var writing = false

    init {
        manager.addPrimaryClipChangedListener {
            if (!writing) onExternalChange()
        }
    }

    override fun copySecret(text: String) = write {
        val clip = ClipData.newPlainText(LABEL, text)
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        manager.setPrimaryClip(clip)
    }

    override fun clear() = write {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.clearPrimaryClip()
        } else {
            manager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    private fun write(block: () -> Unit) {
        writing = true
        try {
            block()
        } finally {
            writing = false
        }
    }

    private companion object {
        const val LABEL = "KeyManage"
    }
}

/** 메인 루퍼에서 도는 지연 실행. */
class MainLooperScheduler : Scheduler {
    private val handler = Handler(Looper.getMainLooper())

    override fun schedule(delayMillis: Long, action: () -> Unit): () -> Unit {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMillis)
        return { handler.removeCallbacks(runnable) }
    }
}
