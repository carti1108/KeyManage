package com.keymanage.ios

import com.carti.ribs.ViewHost
import com.keymanage.devicestore.FileDeviceStore
import com.keymanage.domain.auth.AuthService
import com.keymanage.domain.auth.MasterPasswordChangedException
import com.keymanage.domain.clipboard.ClipboardBurner
import com.keymanage.domain.platform.SecureRandomBytes
import com.keymanage.domain.session.SessionExpiredException
import com.keymanage.domain.session.VaultSession
import com.keymanage.domain.session.VaultSessionDeps
import com.keymanage.domain.tabs.SingleTab
import com.keymanage.domain.vault.RevisionMemory
import com.keymanage.domain.vault.VaultSync
import com.keymanage.feature.KeyManageApp
import com.keymanage.feature.KeyManageViewFactory
import com.keymanage.feature.SessionPortAdapter
import com.keymanage.feature.messages.describeError
import com.keymanage.feature.root.RootViewControllable
import com.keymanage.serverhttp.ApiClient
import com.keymanage.serverhttp.HttpAuthApi
import com.keymanage.serverhttp.HttpVaultApi
import com.keymanage.serverhttp.InvalidBaseUrlException
import com.keymanage.serverhttp.keyManageHttpClient
import com.keymanage.serverhttp.resolveBaseUrl
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSLog
import platform.Foundation.NSUserDefaults
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

// iOS·macOS 진입점 (domain-migration-plan 12.3의 2단계·12.5 macOS 전환, carti ADR-0006). 이름은 iOS에서 시작한 그대로 둔다. 세션과 플랫폼 포트로 공유 RIB 트리를 만들고,
// Swift 앱 수명주기(백그라운드·앞으로 옴·조작. macOS는 비활성도 백그라운드로 넘긴다)를 세션 배선에 넘긴다. 웹 WebKeyManageApp과 같은 자리다.
// 모든 호출은 메인 스레드에서 한다

/** 로컬 개발 서버. Swift 앱(URLSessionAuthNetworkClient)이 쓰던 주소의 출처다 */
const val LOCAL_DEV_SERVER_ORIGIN = "http://localhost:8080"

/** 첫 화면을 붙이지 못했을 때 보일 문구 */
const val START_FAILED_MESSAGE = "앱을 시작하지 못했습니다. 다시 시도해 주세요."

/** 앱 조립(세션·기기 저장소 등) 실패 */
class AppSetupException(cause: Throwable) : Exception("앱 조립 실패", cause)

/** 시작한 RIB 트리. [shutdown]으로 내린다 */
internal fun interface RunningTree {
    fun shutdown()
}

class IosKeyManageApp internal constructor(
    private val scope: CoroutineScope,
    private val restore: suspend () -> Unit,
    private val launchTree: (ViewHost<RootViewControllable>, String?) -> RunningTree,
    private val lifecycle: AppLifecycle,
) {
    private var booting: Job? = null
    private var tree: RunningTree? = null

    /**
     * 기기 기록을 읽은 뒤 세션 상태에 맞는 첫 화면(로그인·잠금 해제)을 [host]에 붙인다 (SEC-003, ADR-0019).
     * 이미 시작했으면 아무것도 하지 않는다. 화면을 붙이지 못하면 붙인 것을 거두고 [onFailure]에 사용자 문구를 넘긴다. 그 뒤 다시 [start]할 수 있다
     */
    fun start(host: ViewHost<RootViewControllable>, onFailure: (String) -> Unit) {
        if (booting != null) return
        booting = scope.launch {
            val notice = try {
                restore()
                null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                signInNoticeOf(error)
            }
            try {
                tree = launchTree(host, notice)
                lifecycle.start()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                NSLog("KeyManage 시작 실패: ${error::class.simpleName ?: "unknown"}")
                tree?.let {
                    tree = null
                    it.shutdown()
                }
                booting = null
                onFailure(START_FAILED_MESSAGE)
            }
        }
    }

    /** RIB 트리와 수명주기 배선을 내린다. 세션은 끝내지 않는다. 다시 [start]할 수 있다 */
    fun stop() {
        val job = booting ?: return
        booting = null
        job.cancel()
        tree?.let {
            tree = null
            it.shutdown()
            lifecycle.stop()
        }
    }

    fun didEnterBackground() {
        if (tree != null) lifecycle.didEnterBackground()
    }

    fun willEnterForeground() {
        if (tree != null) lifecycle.willEnterForeground()
    }

    fun userActivity() {
        if (tree != null) lifecycle.userActivity()
    }

    companion object {
        /**
         * UI 테스트가 같은 상태에서 시작하도록 이 기기의 볼트 기록, 자동 잠금 설정, 생체 해제 키체인 항목과 표시를 지운다.
         * 앱은 디버그 빌드에서 실행 인자 `-resetForUITests`가 있을 때만 [create] 전에 부른다
         */
        @Throws(Exception::class)
        fun resetForUITests() {
            runBlocking { FileDeviceStore.inApplicationSupport().clear() }
            NSUserDefaults.standardUserDefaults.removeObjectForKey(AUTO_LOCK_DEFAULTS_KEY)
            KeychainBiometricUnlock().forgetAll()
        }

        /**
         * 실제 앱 조립.
         * @param serverOrigin 서버 출처(scheme://host[:port]). localhost가 아니면 HTTPS만 받는다
         * @throws InvalidBaseUrlException
         */
        @Throws(InvalidBaseUrlException::class, AppSetupException::class)
        fun create(
            serverOrigin: String,
            viewFactory: KeyManageViewFactory,
            backupDocuments: BackupDocumentPresenter,
        ): IosKeyManageApp = try {
            assemble(serverOrigin, viewFactory, backupDocuments)
        } catch (error: InvalidBaseUrlException) {
            throw error
        } catch (error: Exception) {
            // Kotlin 예외가 Swift 경계를 그냥 넘으면 앱이 죽는다. 선언한 예외로 바꿔 Swift가 잡게 한다
            throw AppSetupException(error)
        }

        private fun assemble(
            serverOrigin: String,
            viewFactory: KeyManageViewFactory,
            backupDocuments: BackupDocumentPresenter,
        ): IosKeyManageApp {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + loggingHandler)
            val session = buildSession(serverOrigin, scope)
            val clock = Clock.System
            val scheduler = CoroutineScheduler(scope)
            val burner = ClipboardBurner(systemClipboardWriter(), clock, scheduler, scope)
            val network = NetworkStatus()
            val wiring = AppLifecycleWiring(
                session = VaultLifecycleSession(session),
                settings = UserDefaultsAutoLockSettingStore(),
                clock = clock,
                scheduler = scheduler,
                burner = burner,
                network = network,
                monitor = NetworkPathMonitor(network),
                scope = scope,
            )
            val environment = IosAppEnvironment(serverOrigin, network, wiring.autoLockSetting, clock)
            return IosKeyManageApp(scope, session::restore, { host, notice ->
                val port = SessionPortAdapter(session)
                val app = KeyManageApp(
                    viewFactory = viewFactory,
                    session = port,
                    clipboard = burner::copy,
                    backupFiles = DocumentsBackupFiles(backupDocuments),
                    environment = environment,
                    randomBytes = SecureRandomBytes,
                    initialNotice = notice,
                    biometricUnlock = KeychainBiometricUnlock(),
                    unlockKeySession = port,
                )
                app.start(host)
                RunningTree(app::stop)
            }, wiring)
        }
    }
}

/** 복원 중 로그인 화면으로 보낸 이유. 기록을 읽지 못한 것 등은 안내 없이 로그인 화면으로 시작한다 (웹 bootKotlinApp과 같다) */
private fun signInNoticeOf(error: Exception): String? =
    if (error is SessionExpiredException || error is MasterPasswordChangedException) describeError(error) else null

// 세션 작업이 놓친 예외로 앱이 죽지 않게 기록만 남긴다. 화면 RIB은 자기 오류를 직접 처리한다
private val loggingHandler = CoroutineExceptionHandler { _, error ->
    NSLog("KeyManage 백그라운드 작업 실패: ${error::class.simpleName ?: "unknown"}")
}

private fun buildSession(serverOrigin: String, scope: CoroutineScope): VaultSession {
    val client = ApiClient(resolveBaseUrl(serverOrigin), keyManageHttpClient())
    return VaultSession(
        VaultSessionDeps(
            auth = AuthService(HttpAuthApi(client)),
            vaultSyncFor = { token, key, memory -> VaultSync(HttpVaultApi(client, token), key, Clock.System, memory) },
            revisionMemory = RevisionMemory(),
            deviceStore = DeviceRecordStoreAdapter(FileDeviceStore.inApplicationSupport()),
            tabs = SingleTab(),
            scope = scope,
        ),
    )
}
