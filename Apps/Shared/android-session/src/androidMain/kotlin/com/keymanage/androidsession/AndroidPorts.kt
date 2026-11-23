package com.keymanage.androidsession

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.backup.LocalDay
import com.keymanage.domain.clipboard.ClipboardWriter
import com.keymanage.domain.lock.AutoLockSettingStore
import com.keymanage.domain.lock.Scheduler
import com.keymanage.feature.AppEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.toJavaInstant

// 공유 세션·RIB에 넣는 Android 쪽 포트 구현. 앱(Apps/Android)은 이것들을 구현하지 않고 화면 정보만 넘긴다

/** 코루틴으로 도는 지연 실행. 세션과 같은 메인 스레드 범위에서 돌린다 */
class CoroutineScheduler(private val scope: CoroutineScope) : Scheduler {
    override fun schedule(delay: Duration, run: () -> Unit): () -> Unit {
        val job = scope.launch {
            delay(delay)
            run()
        }
        return { job.cancel() }
    }
}

/**
 * 시스템 클립보드에 쓴다 (COM-001). 복사한 값은 민감한 값으로 표시해 Android 13 이상의 복사 미리 보기에서 가린다.
 * 이 앱이 아닌 곳에서 클립보드가 바뀌면 [onExternalChange]로 알려, 사용자가 복사한 다른 값을 지우지 않게 한다. 메인 스레드에서만 부른다
 */
class AndroidClipboardWriter(private val manager: ClipboardManager, private val onExternalChange: () -> Unit) : ClipboardWriter {
    // 변경 알림은 쓰기가 끝난 뒤 메인 루퍼로 늦게 오고, 한 번 쓰면 여러 번 온다(에뮬레이터 API 37에서 3번).
    // 그래서 "쓰는 중" 표시나 횟수로는 내 쓰기를 가릴 수 없어, 내가 쓴 직후 짧은 시간 안에 온 알림을 내 것으로 본다
    private var ownChangeUntil = 0L

    init {
        manager.addPrimaryClipChangedListener {
            if (SystemClock.uptimeMillis() > ownChangeUntil) onExternalChange()
        }
    }

    override suspend fun write(text: String) {
        ownChangeUntil = SystemClock.uptimeMillis() + OWN_CHANGE_WINDOW_MS
        if (text.isEmpty()) clear() else copySecret(text)
    }

    private fun copySecret(text: String) {
        val clip = ClipData.newPlainText(LABEL, text)
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        manager.setPrimaryClip(clip)
    }

    private fun clear() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.clearPrimaryClip()
        } else {
            manager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    private companion object {
        const val LABEL = "KeyManage"

        /** 내 쓰기의 알림으로 보는 시간. 그 뒤의 변경은 다른 곳에서 복사한 것이다 */
        const val OWN_CHANGE_WINDOW_MS = 500L
    }
}

/**
 * 앱 쪽 상태: 시각, 기기의 현지 날짜, 자동 잠금 설정, 서버 주소, 네트워크 끊김.
 * 네트워크 상태는 기본 네트워크가 있는지로 본다(매니페스트에 ACCESS_NETWORK_STATE가 필요하다). 알림은 메인 스레드로 넘긴다
 */
class AndroidAppEnvironment(
    context: Context,
    override val autoLockSetting: AutoLockSettingStore,
    override val serverAddress: String,
) : AppEnvironment {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()
    private var callback: ConnectivityManager.NetworkCallback? = null

    override val clock: Clock = Clock.System

    override val calendar = LocalCalendar { time ->
        val date = time.toJavaInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        LocalDay(date.year, date.monthValue, date.dayOfMonth)
    }

    override fun isNetworkOffline(): Boolean = connectivity?.activeNetwork == null

    override fun subscribeNetwork(listener: () -> Unit): () -> Unit {
        listeners += listener
        if (callback == null) register()
        return {
            listeners -= listener
            if (listeners.isEmpty()) unregister()
        }
    }

    private fun register() {
        val created = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = notifyListeners()
            override fun onLost(network: Network) = notifyListeners()
        }
        callback = created
        connectivity?.registerDefaultNetworkCallback(created)
    }

    private fun unregister() {
        callback?.let { connectivity?.unregisterNetworkCallback(it) }
        callback = null
    }

    private fun notifyListeners() {
        main.post { listeners.toList().forEach { it() } }
    }
}
