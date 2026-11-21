package com.keymanage.applesession

import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.backup.LocalDay
import com.keymanage.domain.clipboard.ClipboardWriter
import com.keymanage.domain.lock.AutoLockSettingStore
import com.keymanage.domain.lock.BiometricSetting
import com.keymanage.domain.lock.BiometricSettingStore
import com.keymanage.domain.lock.InvalidAutoLockTimeoutException
import com.keymanage.domain.lock.Scheduler
import com.keymanage.domain.lock.formatAutoLockSetting
import com.keymanage.domain.lock.isAutoLockOption
import com.keymanage.domain.lock.parseAutoLockSetting
import com.keymanage.feature.AppEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.Foundation.dateByAddingTimeInterval
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardOptionExpirationDate
import platform.UIKit.UIPasteboardOptionLocalOnly
import platform.UniformTypeIdentifiers.UTTypeUTF8PlainText
import platform.darwin.dispatch_get_main_queue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

// 공유 세션·RIB에 넣는 iOS 쪽 포트 구현. 앱(Apps/Apple)은 이것들을 구현하지 않고 화면 정보만 넘긴다 (android-session/AndroidPorts.kt의 짝)

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
 * 시스템 클립보드에 쓴다 (COM-001). 복사한 값은 이 기기에만 두고(유니버설 클립보드로 다른 기기에 넘기지 않음),
 * 앱이 꺼져 타이머가 돌지 못해도 iOS가 비울 시각을 함께 건다.
 * 비울 때는 마지막으로 쓴 뒤 클립보드가 바뀌지 않았을 때만 비워, 사용자가 다른 앱에서 복사한 값을 지우지 않는다. 메인 스레드에서만 부른다
 */
class PasteboardClipboardWriter(
    private val pasteboard: UIPasteboard = UIPasteboard.generalPasteboard,
    private val lifetime: Duration,
) : ClipboardWriter {
    private var ownChangeCount: Long? = null

    override suspend fun write(text: String) {
        if (text.isEmpty()) clear() else copySecret(text)
    }

    private fun copySecret(text: String) {
        val expiresAt = NSDate().dateByAddingTimeInterval(lifetime.inWholeMilliseconds / MILLIS_PER_SECOND)
        pasteboard.setItems(
            listOf(mapOf(UTTypeUTF8PlainText.identifier to text)),
            options = mapOf<Any?, Any?>(UIPasteboardOptionLocalOnly to true, UIPasteboardOptionExpirationDate to expiresAt),
        )
        ownChangeCount = pasteboard.changeCount
    }

    private fun clear() {
        val mine = ownChangeCount ?: return
        ownChangeCount = null
        if (pasteboard.changeCount == mine) pasteboard.setItems(emptyList<Map<Any?, Any?>>(), options = emptyMap<Any?, Any?>())
    }
}

/**
 * 자동 잠금 시간을 UserDefaults에 둔다 (SEC-003). 형식은 웹·Android와 같은 밀리초 십진 문자열이다.
 * 쓴 값은 이번 실행 동안 메모리에서 돌려준다
 */
class UserDefaultsAutoLockSettingStore(private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) : AutoLockSettingStore {
    private var written: Duration? = null

    override fun read(): Duration = written ?: parseAutoLockSetting(defaults.stringForKey(AUTO_LOCK_KEY))

    override fun write(timeout: Duration) {
        if (!isAutoLockOption(timeout)) throw InvalidAutoLockTimeoutException(timeout)
        written = timeout
        defaults.setObject(formatAutoLockSetting(timeout), forKey = AUTO_LOCK_KEY)
    }

    /** 앱은 화면이 하나라 다른 탭이 값을 바꾸는 일이 없다 */
    override fun subscribe(listener: (Duration) -> Unit): () -> Unit = {}
}

/**
 * 생체 해제 켜짐 여부와 마지막 마스터 비밀번호 해제 시각을 UserDefaults에 둔다 (SEC-003 규칙 4).
 * 비밀 값은 두지 않는다. 값이 없거나 형식이 다르면 꺼짐으로 본다
 */
class UserDefaultsBiometricSettingStore(private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) : BiometricSettingStore {
    override fun read(): BiometricSetting {
        val last = defaults.objectForKey(BIOMETRIC_LAST_KEY) as? Double
        return BiometricSetting(
            enabled = defaults.boolForKey(BIOMETRIC_ENABLED_KEY),
            lastPasswordUnlock = last?.let { Instant.fromEpochMilliseconds(it.toLong()) },
            passwordRequired = defaults.boolForKey(BIOMETRIC_PASSWORD_REQUIRED_KEY),
        )
    }

    override fun write(setting: BiometricSetting) {
        defaults.setBool(setting.enabled, forKey = BIOMETRIC_ENABLED_KEY)
        defaults.setBool(setting.passwordRequired, forKey = BIOMETRIC_PASSWORD_REQUIRED_KEY)
        val last = setting.lastPasswordUnlock
        if (last == null) {
            defaults.removeObjectForKey(BIOMETRIC_LAST_KEY)
        } else {
            defaults.setDouble(last.toEpochMilliseconds().toDouble(), forKey = BIOMETRIC_LAST_KEY)
        }
    }
}

/**
 * 앱 쪽 상태: 시각, 기기의 현지 날짜, 자동 잠금 설정, 서버 주소, 네트워크 끊김.
 * 네트워크 상태는 NWPathMonitor가 메인 큐로 알린다. 첫 알림 전에는 연결된 것으로 본다
 */
class IosAppEnvironment(
    override val autoLockSetting: AutoLockSettingStore,
    override val serverAddress: String,
) : AppEnvironment {
    private val listeners = mutableSetOf<() -> Unit>()
    private var monitor: nw_path_monitor_t = null
    private var isOffline = false

    override val clock: Clock = Clock.System

    override val calendar = LocalCalendar { time ->
        val date = NSDate.dateWithTimeIntervalSince1970(time.toEpochMilliseconds() / MILLIS_PER_SECOND)
        val parts = NSCalendar.currentCalendar.components(NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay, fromDate = date)
        LocalDay(parts.year.toInt(), parts.month.toInt(), parts.day.toInt())
    }

    override fun isNetworkOffline(): Boolean = isOffline

    override fun subscribeNetwork(listener: () -> Unit): () -> Unit {
        listeners += listener
        if (monitor == null) startMonitor()
        return {
            listeners -= listener
            if (listeners.isEmpty()) stopMonitor()
        }
    }

    private fun startMonitor() {
        val created = nw_path_monitor_create()
        nw_path_monitor_set_queue(created, dispatch_get_main_queue())
        nw_path_monitor_set_update_handler(created) { path ->
            val offline = nw_path_get_status(path) != nw_path_status_satisfied
            if (offline != isOffline) {
                isOffline = offline
                listeners.toList().forEach { it() }
            }
        }
        nw_path_monitor_start(created)
        monitor = created
    }

    private fun stopMonitor() {
        monitor?.let { nw_path_monitor_cancel(it) }
        monitor = null
    }
}

private const val MILLIS_PER_SECOND = 1000.0
private const val AUTO_LOCK_KEY = "autoLockMs"
private const val BIOMETRIC_ENABLED_KEY = "biometricEnabled"
private const val BIOMETRIC_LAST_KEY = "lastPasswordUnlockMs"
private const val BIOMETRIC_PASSWORD_REQUIRED_KEY = "biometricPasswordRequired"
