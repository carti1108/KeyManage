package com.keymanage.apple

import com.keymanage.domain.clipboard.ClipboardBurner
import com.keymanage.domain.lock.AutoLock
import com.keymanage.domain.lock.AutoLockSettingStore
import com.keymanage.domain.lock.LockCause
import com.keymanage.domain.lock.Scheduler
import com.keymanage.domain.lock.createAutoLock
import com.keymanage.domain.session.LockScope
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// 앱 수명주기를 세션에 잇는다 (domain-migration-plan 11.1 끝, 12.3의 2단계). RIB이 아니라 진입점이 붙인다.
// 자동 잠금(SEC-003), 잠금 시 클립보드 소각(COM-001), 대기 재전송(VLT-002), 네트워크 감지. 웹 app/*Wiring.ts와 같은 일을 한다.
// 메인 스레드에서만 부른다

/** 대기 내용 재전송 간격 (VLT-002) */
internal val PENDING_SYNC_RETRY_INTERVAL: Duration = 30.seconds

/** 수명주기 배선이 쓰는 세션 기능 */
internal interface LifecycleSession {
    fun isUnlocked(): Boolean
    fun lock(scope: LockScope)
    fun onLock(listener: () -> Unit): () -> Unit
    fun onUnlock(listener: () -> Unit): () -> Unit
    suspend fun retryPendingSync()
}

internal class VaultLifecycleSession(private val session: VaultSession) : LifecycleSession {
    override fun isUnlocked(): Boolean = session.state() is SessionState.Unlocked
    override fun lock(scope: LockScope) = session.lock(scope)
    override fun onLock(listener: () -> Unit): () -> Unit = session.onLock(listener)
    override fun onUnlock(listener: () -> Unit): () -> Unit = session.onUnlock(listener)
    override suspend fun retryPendingSync() = session.retryPendingSync()
}

/** 네트워크 경로 감지 시작·중지. 실제 구현은 [NetworkPathMonitor] */
interface NetworkMonitoring {
    fun start()
    fun stop()
}

/** 진입점이 Swift 앱 수명주기에서 부르는 것 */
internal interface AppLifecycle {
    fun start()
    fun stop()

    /** 앱이 백그라운드로 감(scenePhase background) */
    fun didEnterBackground()

    /** 앱이 다시 앞으로 옴(scenePhase active) */
    fun willEnterForeground()

    /** 앱이 잠깐 비활성이 됨(iOS 제어 센터·앱 전환 화면). 가림 화면만 씌우고 잠그지 않는다 */
    fun didResignActive()

    /** 비활성에서 다시 활성이 됨(백그라운드를 거치지 않음) */
    fun didBecomeActive()

    /** 사용자 조작(터치·입력). 자동 잠금 시간을 다시 잰다 */
    fun userActivity()

    /** 볼트 내용을 가림 화면으로 덮어야 하는지 (SEC-003 3번) */
    val isPrivacyCoverActive: Boolean

    /** 가림 화면 상태가 바뀔 때 알린다. 돌려준 함수로 구독을 끊는다 */
    fun onPrivacyCoverChanged(listener: (Boolean) -> Unit): () -> Unit
}

internal class AppLifecycleWiring(
    private val session: LifecycleSession,
    settings: AutoLockSettingStore,
    private val clock: Clock,
    private val scheduler: Scheduler,
    private val burner: ClipboardBurner,
    private val network: NetworkStatus,
    private val monitor: NetworkMonitoring,
    private val scope: CoroutineScope,
) : AppLifecycle {
    /** 설정 화면에 넘기는 저장소. 이 앱에서 바꾸면 그때부터 새 시간으로 잰다 (SEC-003, ADR-WEB-002와 같다) */
    val autoLockSetting: AutoLockSettingStore = AppliedAutoLockSettingStore(settings) { rebuildAutoLock() }

    private val readSetting: () -> Duration = settings::read
    private var visible = true
    private var inactive = false
    private var coverListeners: List<(Boolean) -> Unit> = emptyList()

    override var isPrivacyCoverActive: Boolean = false
        private set
    private var autoLock: AutoLock? = null
    private var releases: List<() -> Unit> = emptyList()
    private var cancelRetry: (() -> Unit)? = null

    private val running: Boolean get() = autoLock != null

    override fun start() {
        if (running) return
        autoLock = makeAutoLock()
        releases = listOf(
            session.onUnlock { autoLock?.start() },
            session.onLock {
                autoLock?.stop()
                // 잠기면(로그아웃 포함) 30초를 기다리지 않고 바로 비운다 (COM-001, SEC-003 7번)
                scope.launch { burner.clear() }
            },
            network.subscribe { if (!network.isOffline()) retryPendingSync() },
        )
        monitor.start()
        scheduleRetry()
        // 연결하는 시점에 이미 열려 있으면 알림을 기다리지 않고 바로 잰다
        if (session.isUnlocked()) autoLock?.start()
    }

    override fun stop() {
        val current = autoLock ?: return
        autoLock = null
        current.stop()
        releases.forEach { it() }
        releases = emptyList()
        cancelRetry?.invoke()
        cancelRetry = null
        monitor.stop()
    }

    override fun didEnterBackground() {
        visible = false
        updateCover()
        autoLock?.hidden()
    }

    override fun willEnterForeground() {
        visible = true
        inactive = false
        updateCover()
        autoLock?.visible()
        // 백그라운드에서 비우지 못한 클립보드를 다시 비운다
        scope.launch { burner.retry() }
    }

    override fun didResignActive() {
        inactive = true
        updateCover()
    }

    override fun didBecomeActive() {
        inactive = false
        updateCover()
    }

    override fun onPrivacyCoverChanged(listener: (Boolean) -> Unit): () -> Unit {
        coverListeners = coverListeners + listener
        return { coverListeners = coverListeners - listener }
    }

    private fun updateCover() {
        val covered = !visible || inactive
        if (covered == isPrivacyCoverActive) return
        isPrivacyCoverActive = covered
        coverListeners.forEach { it(covered) }
    }

    override fun userActivity() {
        autoLock?.activity()
    }

    private fun makeAutoLock(): AutoLock = createAutoLock(readSetting(), clock, scheduler, { visible }) { cause ->
        // 앱은 탭이 하나라 범위가 결과를 바꾸지 않는다. 웹과 같은 규칙을 둔다
        session.lock(if (cause == LockCause.TIMER) LockScope.ALL_TABS else LockScope.THIS_TAB)
    }

    private fun rebuildAutoLock() {
        val previous = autoLock ?: return
        previous.stop()
        autoLock = makeAutoLock().also { if (session.isUnlocked()) it.start() }
    }

    private fun scheduleRetry() {
        cancelRetry = scheduler.schedule(PENDING_SYNC_RETRY_INTERVAL) {
            retryPendingSync()
            if (running) scheduleRetry()
        }
    }

    private fun retryPendingSync() {
        scope.launch { session.retryPendingSync() }
    }
}

/** 쓰고 나면 [applied]를 불러 타이머를 새 시간으로 다시 잇는다 */
private class AppliedAutoLockSettingStore(
    private val store: AutoLockSettingStore,
    private val applied: () -> Unit,
) : AutoLockSettingStore by store {
    override fun write(timeout: Duration) {
        store.write(timeout)
        applied()
    }
}
