package com.keymanage.android.feature.root.impl

import com.keymanage.android.domain.clipboard.Scheduler
import com.keymanage.android.domain.lock.AutoLock
import com.keymanage.android.domain.lock.AutoLockSettingStore
import com.keymanage.android.feature.root.AppEventListener

/**
 * 잠금이 풀린 동안 도는 자동 잠금(SEC-003 규칙 2). 설정을 바꾸면 그때부터 새 시간으로 잰다.
 * 앱 생명주기와 사용자 조작은 [AppEventListener]로 받는다.
 */
class RootAutoLock(
    private val store: AutoLockSettingStore,
    private val scheduler: Scheduler,
    private val monotonicClock: () -> Long,
) : AppEventListener {
    private var lock: AutoLock? = null
    private var onTimeout: (() -> Unit)? = null
    private var isForeground = true

    fun start(onTimeout: () -> Unit) {
        stop()
        this.onTimeout = onTimeout
        lock = AutoLock(store.read(), monotonicClock, scheduler) { fire() }.also { it.start(isForeground) }
    }

    fun stop() {
        lock?.stop()
        lock = null
        onTimeout = null
    }

    /** 설정을 바꿨을 때. 잠금이 풀린 동안이면 새 시간으로 다시 시작한다. */
    fun restart() {
        val callback = onTimeout ?: return
        start(callback)
    }

    override fun onForeground() {
        isForeground = true
        lock?.foreground()
    }

    override fun onBackground() {
        isForeground = false
        lock?.background()
    }

    override fun onUserInteraction() {
        lock?.activity()
    }

    private fun fire() {
        val callback = onTimeout
        lock = null
        onTimeout = null
        callback?.invoke()
    }
}
