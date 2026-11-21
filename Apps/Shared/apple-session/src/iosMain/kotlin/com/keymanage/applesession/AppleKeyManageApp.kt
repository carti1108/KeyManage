package com.keymanage.applesession

import com.carti.ribs.ViewHost
import com.keymanage.domain.auth.AuthService
import com.keymanage.domain.auth.MasterPasswordChangedException
import com.keymanage.domain.clipboard.BURN_AFTER
import com.keymanage.domain.clipboard.BurnState
import com.keymanage.domain.clipboard.ClipboardBurner
import com.keymanage.domain.lock.AutoLock
import com.keymanage.domain.lock.AutoLockSettingStore
import com.keymanage.domain.lock.BiometricUnlock
import com.keymanage.domain.lock.LockCause
import com.keymanage.domain.lock.createAutoLock
import com.keymanage.domain.platform.SecureRandomBytes
import com.keymanage.domain.session.LockScope
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.VaultSession
import com.keymanage.domain.session.VaultSessionDeps
import com.keymanage.domain.tabs.SingleTab
import com.keymanage.domain.vault.RevisionMemory
import com.keymanage.domain.vault.VaultSync
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.KeyManageApp
import com.keymanage.feature.KeyManageViewFactory
import com.keymanage.feature.SessionPortAdapter
import com.keymanage.feature.messages.describeError
import com.keymanage.feature.root.RootViewControllable
import com.keymanage.serverhttp.ApiClient
import com.keymanage.serverhttp.HttpAuthApi
import com.keymanage.serverhttp.HttpVaultApi
import com.keymanage.serverhttp.keyManageHttpClient
import com.keymanage.serverhttp.resolveBaseUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * iOS 앱의 진입점(AndroidKeyManageApp의 짝). 공유 세션과 iOS 쪽 포트를 조립해 RIB 트리를 띄운다.
 * 앱(Apps/Apple)은 화면을 만드는 [viewFactory]와 선택 창을 띄울 화면([presenter])만 넘기고,
 * 생명주기를 [onForeground]·[onBackground]·[onUserInteraction]으로 알린다. 모든 호출은 메인 스레드에서 한다
 *
 * @param serverUrl 서버 주소. localhost가 아니면 HTTPS만 받는다
 * @throws com.keymanage.serverhttp.InvalidBaseUrlException
 */
class AppleKeyManageApp(
    serverUrl: String,
    private val viewFactory: KeyManageViewFactory,
    presenter: () -> UIViewController?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val baseUrl = resolveBaseUrl(serverUrl)
    private val client = ApiClient(baseUrl, keyManageHttpClient())

    private val session = VaultSession(
        VaultSessionDeps(
            auth = AuthService(HttpAuthApi(client)),
            vaultSyncFor = { token, key, memory -> VaultSync(HttpVaultApi(client, token), key, Clock.System, memory) },
            revisionMemory = RevisionMemory(),
            deviceStore = FileDeviceRecordStore(),
            tabs = SingleTab(),
            scope = scope,
            biometric = BiometricUnlock(KeychainBiometricKeyStore(), UserDefaultsBiometricSettingStore(), Clock.System),
        ),
    )

    private val scheduler = CoroutineScheduler(scope)
    private val clipboardBurner = ClipboardBurner(PasteboardClipboardWriter(lifetime = BURN_AFTER), Clock.System, scheduler, scope)

    /** 복사하고 30초 뒤 비운다. 화면에는 비울 시각을 알려 안내와 남은 초를 보이게 한다 (COM-001) */
    private val clipboardPort = object : ClipboardPort {
        override suspend fun copy(text: String) = clipboardBurner.copy(text)

        override val burnAt: Instant? get() = burnAtOf(clipboardBurner.state.value)

        override fun subscribeBurn(listener: (Instant?) -> Unit): () -> Unit {
            val job = scope.launch { clipboardBurner.state.collect { listener(burnAtOf(it)) } }
            return { job.cancel() }
        }

        private fun burnAtOf(state: BurnState): Instant? = (state as? BurnState.Counting)?.expiresAt
    }

    /** 설정 화면이 시간을 바꾸면 그때부터 새 시간으로 잰다 (SEC-003 값 표) */
    private val autoLockSetting = object : AutoLockSettingStore {
        private val stored = UserDefaultsAutoLockSettingStore()
        override fun read(): Duration = stored.read()
        override fun write(timeout: Duration) {
            stored.write(timeout)
            restartAutoLock()
        }
        override fun subscribe(listener: (Duration) -> Unit): () -> Unit = stored.subscribe(listener)
    }
    private val backupFiles = DocumentPickerBackupFiles(presenter)
    private var autoLock: AutoLock = newAutoLock()
    private var isForeground = true
    private var app: KeyManageApp? = null

    init {
        // 볼트가 열려 있는 동안만 타이머가 돈다. 잠기면 클립보드를 바로 비운다 (SEC-003 규칙 2·7)
        session.onUnlock { autoLock.start() }
        session.onLock {
            autoLock.stop()
            scope.launch { clipboardBurner.clear() }
        }
    }

    /** 기기 기록을 읽어 세션 상태를 되살린 뒤, 그 상태에 맞는 첫 화면을 [host]에 띄운다. 이미 시작했으면 아무것도 하지 않는다 (SEC-003 규칙 9) */
    fun start(host: ViewHost<RootViewControllable>) {
        if (app != null) return
        scope.launch {
            val notice = restoreSession()
            if (app != null) return@launch
            val created = KeyManageApp(
                viewFactory = viewFactory,
                session = SessionPortAdapter(session),
                clipboard = clipboardPort,
                backupFiles = backupFiles,
                environment = IosAppEnvironment(autoLockSetting, baseUrl),
                randomBytes = SecureRandomBytes,
                initialNotice = notice,
            )
            app = created
            created.start(host)
        }
    }

    private suspend fun restoreSession(): String? = try {
        session.restore()
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: MasterPasswordChangedException) {
        describeError(error)
    } catch (_: Exception) {
        // 기기 기록을 읽지 못하면 로그인 화면에서 시작한다. 기록은 다음 로그인이 다시 쓴다
        null
    }

    /** RIB 트리를 내린다. 세션은 끝내지 않는다 */
    fun stop() {
        app?.stop()
        app = null
    }

    /** 화면이 다시 보인다. 숨겨진 사이 자동 잠금 시간이 지났으면 잠그고, 올리지 못한 변경 사항과 비우지 못한 클립보드를 다시 처리한다 */
    fun onForeground() {
        isForeground = true
        autoLock.visible()
        scope.launch {
            clipboardBurner.retry()
            session.retryPendingSync()
        }
    }

    /** 화면이 가려진다. 자동 잠금이 "즉시"면 바로 잠근다 */
    fun onBackground() {
        isForeground = false
        autoLock.hidden()
    }

    /** 사용자 조작. 자동 잠금 시간을 다시 잰다 */
    fun onUserInteraction() = autoLock.activity()

    private fun newAutoLock(): AutoLock = createAutoLock(
        timeout = autoLockSetting.read(),
        clock = Clock.System,
        scheduler = scheduler,
        isVisible = { isForeground },
        // 앱은 화면이 하나라 다른 탭에 퍼뜨릴 일이 없다
        onTimeout = { _: LockCause -> session.lock(LockScope.THIS_TAB) },
    )

    private fun restartAutoLock() {
        autoLock.stop()
        autoLock = newAutoLock()
        if (session.state() is SessionState.Unlocked) autoLock.start()
    }
}
