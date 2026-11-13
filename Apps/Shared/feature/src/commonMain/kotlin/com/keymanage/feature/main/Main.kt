package com.keymanage.feature.main

import com.carti.ribs.Builder
import com.carti.ribs.Component
import com.carti.ribs.Dependency
import com.carti.ribs.Presentable
import com.carti.ribs.PresentableInteractor
import com.carti.ribs.RibDispatchers
import com.carti.ribs.ViewControllable
import com.carti.ribs.ViewableRouter
import com.carti.ribs.checkMainThread
import com.keymanage.domain.device.DeviceStorageException
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.generator.PasswordGeneratorBuilder
import com.keymanage.feature.generator.PasswordGeneratorDependency
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.messages.DEVICE_STORAGE_FAILED
import com.keymanage.feature.messages.ErrorMessage
import com.keymanage.feature.messages.LOGOUT_CLEAR_FAILED
import com.keymanage.feature.messages.OFFLINE_NOTICE
import com.keymanage.feature.messages.READ_ONLY_NOTICE
import com.keymanage.feature.messages.describeError
import com.keymanage.feature.messages.describeErrorMessage
import com.keymanage.feature.messages.sendsToSignIn
import com.keymanage.feature.settings.SettingsBuilder
import com.keymanage.feature.settings.SettingsDependency
import com.keymanage.feature.settings.SettingsListener
import com.keymanage.feature.settings.SettingsViewFactory
import com.keymanage.feature.vaultlist.VaultListBuilder
import com.keymanage.feature.vaultlist.VaultListDependency
import com.keymanage.feature.vaultlist.VaultListListener
import com.keymanage.feature.vaultlist.VaultListRouter
import com.keymanage.feature.vaultlist.VaultListViewFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.Clock

// 열린 볼트의 틀. 탭(볼트·생성기·설정), 경고·배너, 세션 없이 연 볼트의 재연결, 잠그기·로그아웃 (SEC-003, VLT-002, VLT-003, ADR-0019)
// 원본: Apps/Web/src/ui/screens/VaultHomeScreen.tsx, ui/session/ReconnectPrompt.tsx, ui/session/SessionRoot.tsx의 볼트 화면 부분, iOS Features/Main
// 자식은 선택한 탭이 처음 열릴 때 붙이고, 한 번 붙인 자식은 이 RIB이 내려갈 때까지 유지한다 (iOS와 같다. 웹은 탭을 떠나면 화면을 지운다)

enum class MainTab { VAULT, GENERATOR, SETTINGS }

/** 화면이 그리는 전부 */
data class MainViewState(
    val email: String = "",
    val selectedTab: MainTab = MainTab.VAULT,
    /** 새 버전 앱이 저장한 볼트라 읽기만 할 수 있다 */
    val readOnly: Boolean = false,
    /** 서버와 맞추지 못한 상태. 잠금 해제를 오프라인으로 열었거나, 네트워크가 끊겼거나, 올리지 못한 변경 사항이 있다 (VLT-002) */
    val offline: Boolean = false,
    /** 대기 내용 재전송이 분명한 거절을 받았을 때의 오류. 다음 동기화 성공에 걷힌다 (VLT-002) */
    val syncError: ErrorMessage? = null,
    /** 마지막 기기 저장이 실패했다. 다음 저장이 성공할 때까지 남는다 (VLT-003) */
    val showsDeviceStorageWarning: Boolean = false,
    /** 세션 없이 열었다. 마스터 비밀번호를 다시 입력받아야 동기화한다 (SEC-003, ADR-0019) */
    val needsReconnect: Boolean = false,
    val reconnectPassword: String = "",
    val isReconnecting: Boolean = false,
    val reconnectError: ErrorMessage? = null,
    /** 서버 볼트가 이전 상태로 돌아갔다는 경고를 받아들일지 묻는 중 (VLT-001). [reconnectError]와 함께 보이지 않는다 */
    val isConfirmingReconnectRollback: Boolean = false,
    /** 올리지 못한 변경 사항이 사라진다고 알리고 로그아웃할지 묻는 중 (VLT-002) */
    val isConfirmingLogout: Boolean = false,
    val isLoggingOut: Boolean = false,
) {
    val offlineNotice: String? get() = if (offline) OFFLINE_NOTICE else null
    val readOnlyNotice: String? get() = if (readOnly) READ_ONLY_NOTICE else null
    val deviceStorageWarning: String? get() = if (showsDeviceStorageWarning) DEVICE_STORAGE_FAILED else null
}

/** 화면 입력 전부 */
sealed interface MainViewAction {
    data class SelectTab(val tab: MainTab) : MainViewAction
    data object RequestLock : MainViewAction

    /** 로그아웃 버튼. 대기 중인 변경 사항이 있으면 확인을 먼저 묻는다 */
    data object RequestLogout : MainViewAction
    data object ConfirmLogout : MainViewAction
    data object CancelLogout : MainViewAction

    data class UpdateReconnectPassword(val password: String) : MainViewAction
    data object Reconnect : MainViewAction

    /** 되돌림 경고를 받아들이고 처음 입력한 비밀번호로 다시 연결한다 */
    data object AcceptReconnectRollback : MainViewAction
    data object CancelReconnectRollback : MainViewAction
}

interface MainPresentableListener {
    fun handle(action: MainViewAction)
}

/** Interactor가 쓰는 화면 계약 (RIBs-iOS Presentable, carti ADR-0010) */
interface MainPresentable : Presentable {
    /** 활성 구간에만 설정된다. 비활성이면 null이라 입력이 무시된다 */
    var listener: MainPresentableListener?
    fun render(state: MainViewState)
}

/** Router가 쓰는 화면 계약 */
interface MainViewControllable : ViewControllable {
    /** 고른 탭의 자식 화면을 보인다. 이전 탭의 화면은 먼저 [hideScreen]으로 내린다 */
    fun showScreen(view: ViewControllable)
    fun hideScreen(view: ViewControllable)
}

/** 플랫폼 뷰가 구현하는 둘의 합 */
interface MainViewContract : MainPresentable, MainViewControllable

fun interface MainViewFactory {
    fun makeMainView(): MainViewContract
}

interface MainDependency : Dependency {
    val mainViewFactory: MainViewFactory
    val session: SessionPort
    val environment: AppEnvironment
    val clipboard: ClipboardPort
    val backupFiles: BackupFilesPort
    val randomBytes: RandomBytes

    // 자식 RIB이 쓸 뷰 팩토리
    val vaultListViewFactory: VaultListViewFactory
    val passwordGeneratorViewFactory: PasswordGeneratorViewFactory
    val settingsViewFactory: SettingsViewFactory
}

/** 자식 → 부모 */
interface MainListener {
    /** 잠그기를 눌렀다(세션은 이미 잠갔다). 또는 세션이 끝나 이 화면을 내려야 한다. 부모가 세션 상태를 다시 읽는다 */
    fun didLock()

    /**
     * 로그아웃했다. 이 기기의 볼트 기록을 지우지 못했으면 로그인 화면에 보일 [notice]를 담는다 (ADR-0019).
     * 서버 토큰 폐기만 실패한 경우는 이 기기가 이미 로그아웃됐으므로 알리지 않는다 (SEC-002)
     */
    fun didLogout(notice: String?)

    /** 세션이 끝났다: 재연결에서 만료·다른 곳의 비밀번호 변경, 계정 삭제 완료 등. 로그인 화면에 [notice]를 보인다 (SEC-002~005) */
    fun didRequireSignIn(notice: String)
}

/**
 * 계정 삭제를 서버가 받아들인 뒤에는 이 탭이 먼저 로그인 전 상태로 바뀌어 잠금 알림이 나가고, 그 뒤에 기기 기록을 지운다.
 * 알림을 받은 부모가 이 화면을 내려 진행 중인 작업을 취소하면 기록이 남으므로, 진행 중인지 이 포트가 센다
 */
class MainSessionPort(private val delegate: SessionPort) : SessionPort by delegate {
    var deletesInFlight = 0
        private set

    override suspend fun deleteAccount(password: String) {
        deletesInFlight += 1
        try {
            delegate.deleteAccount(password)
        } finally {
            deletesInFlight -= 1
        }
    }
}

/** 볼트 목록·생성기·설정이 쓰는 의존성을 이 RIB의 의존성에서 만든다. 세션은 끝내는 작업을 세는 [MainSessionPort]로 감싼다 */
class MainComponent(dependency: MainDependency) :
    Component<MainDependency>(dependency), VaultListDependency, PasswordGeneratorDependency, SettingsDependency {
    val trackedSession: MainSessionPort by shared { MainSessionPort(dependency.session) }

    override val session: SessionPort get() = trackedSession
    override val vaultListViewFactory: VaultListViewFactory get() = dependency.vaultListViewFactory
    override val passwordGeneratorViewFactory: PasswordGeneratorViewFactory get() = dependency.passwordGeneratorViewFactory
    override val settingsViewFactory: SettingsViewFactory get() = dependency.settingsViewFactory
    override val clipboard: ClipboardPort get() = dependency.clipboard
    override val clock: Clock get() = dependency.environment.clock
    override val randomBytes: RandomBytes get() = dependency.randomBytes
    override val backupFiles: BackupFilesPort get() = dependency.backupFiles
    override val environment: AppEnvironment get() = dependency.environment
}

class MainBuilder(dependency: MainDependency) : Builder<MainDependency>(dependency) {
    /**
     * @param offline 잠금 해제를 서버에 닿지 못해 기기에 저장한 볼트로 열었다. 동기화에 성공하면 이 RIB이 걷는다 (VLT-002)
     */
    fun build(listener: MainListener, offline: Boolean = false): MainRouter {
        val component = MainComponent(dependency)
        val view = dependency.mainViewFactory.makeMainView()
        val interactor = MainInteractor(view, component.trackedSession, dependency.environment, listener, offline)
        return MainRouter(
            interactor = interactor,
            view = view,
            vaultListBuilder = VaultListBuilder(component),
            passwordGeneratorBuilder = PasswordGeneratorBuilder(component),
            settingsBuilder = SettingsBuilder(component),
        )
    }
}

class MainInteractor(
    presenter: MainPresentable,
    private val session: MainSessionPort,
    private val environment: AppEnvironment,
    private val listener: MainListener,
    /** 잠금 해제를 오프라인으로 열었는지. 동기화·재연결에 성공하면 내린다 */
    private var openedOffline: Boolean,
) : PresentableInteractor<MainPresentable>(presenter), MainPresentableListener, VaultListListener, SettingsListener {
    /** Router가 만들어질 때 자신을 넣는다 */
    var router: MainRouter? = null
    private var state = MainViewState()
    private var unsubscribes: List<() -> Unit> = emptyList()

    /** 되돌림 경고를 받아들이면 다시 보낼 비밀번호. 입력 칸은 비우지만 처음 요청의 값을 쓴다 */
    private var rollbackPassword: String? = null

    // 다른 탭의 대기 내용을 확인하는 동안 로그아웃을 다시 받지 않는다
    private var isCheckingLogout = false
    private var logoutInFlight = false

    /**
     * 세션을 끝내는 작업(로그아웃, 계정 삭제)이 진행 중이다. 세션이 먼저 로그인 전 상태로 알려도 부모는 이 작업이 끝나
     * 결과를 알릴 때까지 이 화면을 내리지 않는다. 내리면 기기 기록 정리가 취소돼 볼트 기록이 남는다 (ADR-0019, SEC-005)
     */
    val isFinishingSession: Boolean get() = logoutInFlight || session.deletesInFlight > 0

    override fun didBecomeActive() {
        presenter.listener = this
        // 콜백은 어느 스레드에서 올지 모르니 메인 스코프에서 처리한다. 알림 도중 이 RIB이 내려갔으면 무시한다
        unsubscribes = listOf(
            session.onSyncChange { error -> post { handleSyncChange(error) } },
            session.onRemoteChange { post { refreshSession() } },
            environment.subscribeNetwork { post { refreshSession() } },
        )
        refreshSession()
        router?.routeTo(MainTab.VAULT)
    }

    override fun willResignActive() {
        presenter.listener = null
        unsubscribes.forEach { it() }
        unsubscribes = emptyList()
        rollbackPassword = null
    }

    private fun post(work: () -> Unit) {
        if (isActive) coroutineScope.launch { work() }
    }

    override fun handle(action: MainViewAction) {
        // 내려간 뒤 뷰가 남긴 입력은 무시한다 (옛 Presenter가 onAction을 비우던 동작)
        if (!isActive) return
        when (action) {
            is MainViewAction.SelectTab -> selectTab(action.tab)
            MainViewAction.RequestLock -> lock()
            MainViewAction.RequestLogout -> requestLogout()
            MainViewAction.ConfirmLogout -> if (state.isConfirmingLogout) logout()
            MainViewAction.CancelLogout -> update { copy(isConfirmingLogout = false) }
            is MainViewAction.UpdateReconnectPassword -> update { copy(reconnectPassword = action.password) }
            MainViewAction.Reconnect -> reconnect(state.reconnectPassword, acceptRollback = false)
            MainViewAction.AcceptReconnectRollback -> rollbackPassword?.let { reconnect(it, acceptRollback = true) }
            MainViewAction.CancelReconnectRollback -> {
                rollbackPassword = null
                update { copy(isConfirmingReconnectRollback = false) }
            }
        }
    }

    // --- 상태 ---

    private fun update(change: MainViewState.() -> MainViewState) {
        state = state.change()
        if (isActive) render()
    }

    private fun render() {
        checkMainThread("MainPresentable.render")
        presenter.render(state)
    }

    /**
     * 세션이 알려 주는 값(이메일, 읽기 전용, 동기화 대기, 기기 저장, 세션 유무)을 다시 읽는다. 입력과 확인 창은 건드리지 않는다.
     * 열려 있지 않으면 부모가 이 화면을 내리므로 아무것도 하지 않는다
     * @return 열려 있는지
     */
    private fun refreshSession(): Boolean {
        if (!isActive) return false
        val current = session.state() as? SessionState.Unlocked ?: return false
        update {
            copy(
                email = current.email,
                readOnly = current.vault.readOnly,
                offline = openedOffline || environment.isNetworkOffline() || current.pendingSync,
                showsDeviceStorageWarning = !current.storedOnDevice,
                needsReconnect = !current.signedIn,
            )
        }
        return true
    }

    private fun handleSyncChange(error: Throwable?) {
        when {
            error == null -> {
                openedOffline = false
                update { copy(syncError = null) }
            }
            // 세션 만료·다른 곳의 비밀번호 변경은 로그인 화면 안내로 보인다. 부모가 안다
            sendsToSignIn(error) -> update { copy(syncError = null) }
            // 기기 저장 실패는 세션 상태(storedOnDevice)로 경고한다. 다음 저장이 성공할 때까지 남아야 한다
            error is DeviceStorageException -> Unit
            else -> update { copy(syncError = describeErrorMessage(error)) }
        }
        refreshSession()
    }

    // --- 탭 ---

    private fun selectTab(tab: MainTab) {
        if (tab == state.selectedTab) return
        update { copy(selectedTab = tab) }
        router?.routeTo(tab)
        // 다른 탭에서 한 복원 같은 알림 없는 변경을 이 탭이 다시 읽는다
        if (tab == MainTab.VAULT) router?.refreshVaultList()
    }

    // --- 잠금 ---

    private fun lock() {
        session.lock()
        listener.didLock()
    }

    // --- 로그아웃 ---

    /**
     * 올리지 못한 변경 사항이 있으면 사라진다고 먼저 알린다 (VLT-002).
     * 이 탭에 없어도 다른 탭이 기기 기록에 남겼을 수 있으니 기록을 확인한다 (ADR-WEB-001)
     */
    private fun requestLogout() {
        if (state.isLoggingOut || isCheckingLogout) return
        if ((session.state() as? SessionState.Unlocked)?.pendingSync == true) {
            update { copy(isConfirmingLogout = true) }
            return
        }
        isCheckingLogout = true
        coroutineScope.launch {
            try {
                // 읽지 못하면 다른 탭의 대기 내용을 알 수 없으니 있다고 보고 확인을 받는다
                val pending = try {
                    session.pendingOnDevice()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    true
                }
                if (pending) update { copy(isConfirmingLogout = true) } else logout()
            } finally {
                isCheckingLogout = false
            }
        }
    }

    /**
     * 세션은 이 기기 기록을 정리하는 도중 잠금 알림을 보내 부모가 이 화면을 내릴 수 있다. 그래도 정리와 서버 토큰 폐기는
     * 끝까지 해야 하고 결과(기록을 지우지 못했다는 안내)도 알려야 하므로 이 RIB의 스코프가 아닌 별도 스코프에서 한다.
     * 웹은 서버 응답을 기다리지 않고 화면을 바꿨지만, 여기서는 끝난 결과로 알린다
     */
    private fun logout() {
        if (state.isLoggingOut) return
        logoutInFlight = true
        update { copy(isLoggingOut = true, isConfirmingLogout = false) }
        CoroutineScope(SupervisorJob() + RibDispatchers.MainImmediate).launch {
            val notice = try {
                session.logout()
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: DeviceStorageException) {
                // 기기 기록을 지우지 못했으면 공용 기기에 볼트가 남으므로 알린다 (ADR-0019)
                LOGOUT_CLEAR_FAILED
            } catch (_: Exception) {
                // 서버 토큰 폐기만 실패했다. 이 기기는 이미 로그아웃됐고 서버 토큰은 만료(30일)를 기다린다 (SEC-002)
                null
            }
            logoutInFlight = false
            listener.didLogout(notice)
        }
    }

    // --- 재연결 ---

    private fun reconnect(password: String, acceptRollback: Boolean) {
        if (state.isReconnecting || !state.needsReconnect) return
        rollbackPassword = null
        // 입력은 요청에만 담고 화면 상태에서는 바로 버린다
        update { copy(isReconnecting = true, reconnectError = null, isConfirmingReconnectRollback = false, reconnectPassword = "") }
        coroutineScope.launch {
            val failure = try {
                session.reconnect(password, acceptRollback)
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                error
            }
            when {
                failure == null -> {
                    // 서버에 닿아 맞췄다. 맞추지 못했으면 동기화 대기(pendingSync)로 계속 오프라인으로 보인다
                    openedOffline = false
                    update { copy(isReconnecting = false, reconnectError = null, syncError = null) }
                    refreshSession()
                    // 재연결은 서버와 맞추며 알림 없이 볼트를 바꿀 수 있다
                    router?.refreshVaultList()
                }
                failure is RevisionRollbackException && !acceptRollback -> {
                    rollbackPassword = password
                    update { copy(isReconnecting = false, isConfirmingReconnectRollback = true) }
                    refreshSession()
                }
                sendsToSignIn(failure) -> {
                    update { copy(isReconnecting = false) }
                    // 부모가 이 RIB을 내릴 수 있으므로 알림은 마지막에 한다
                    listener.didRequireSignIn(describeError(failure))
                }
                else -> {
                    update { copy(isReconnecting = false, reconnectError = describeErrorMessage(failure)) }
                    refreshSession()
                }
            }
        }
    }

    // --- VaultListListener ---

    override fun vaultListDidSave() {
        update { copy(syncError = null) }
    }

    // --- SettingsListener ---

    override fun settingsDidRequestLock() = lock()

    override fun settingsDidRequestLogout() = requestLogout()

    /** 복원·비밀번호 변경·계정 삭제를 시도했다. 세션이나 볼트가 바뀌었을 수 있으니 다시 읽고, 더 열려 있지 않으면 부모가 알게 한다 */
    override fun settingsDidChangeSession() {
        if (refreshSession()) router?.refreshVaultList() else if (isActive) listener.didLock()
    }

    override fun settingsDidRestoreBackup() {
        update { copy(syncError = null) }
    }

    override fun settingsDidChangeMasterPassword() {
        openedOffline = false
        update { copy(syncError = null) }
        refreshSession()
    }

    override fun settingsDidEndSession(notice: String) {
        listener.didRequireSignIn(notice)
    }
}

class MainRouter(
    interactor: MainInteractor,
    view: MainViewControllable,
    private val vaultListBuilder: VaultListBuilder,
    private val passwordGeneratorBuilder: PasswordGeneratorBuilder,
    private val settingsBuilder: SettingsBuilder,
) : ViewableRouter<MainInteractor, MainViewControllable>(interactor, view) {
    private var vaultList: VaultListRouter? = null
    private var generator: ViewControllable? = null
    private var settings: ViewControllable? = null
    private var shown: ViewControllable? = null

    init {
        interactor.router = this
    }

    /** 고른 탭의 화면만 보인다. 처음 고른 탭은 이때 붙이고, 이후에는 붙어 있는 화면을 다시 보인다 */
    fun routeTo(tab: MainTab) {
        val screen = when (tab) {
            MainTab.VAULT -> (vaultList ?: attachVaultList().also { vaultList = it }).viewControllable
            MainTab.GENERATOR -> generator ?: attachGenerator().also { generator = it }
            MainTab.SETTINGS -> settings ?: attachSettings().also { settings = it }
        }
        if (shown === screen) return
        shown?.let(viewController::hideScreen)
        viewController.showScreen(screen)
        shown = screen
    }

    /** 세션이 알림 없이 볼트를 바꿨을 수 있을 때 볼트 목록이 붙어 있으면 세션에서 다시 읽게 한다 (복원·재연결·탭 전환) */
    fun refreshVaultList() {
        vaultList?.refreshFromSession()
    }

    private fun attachVaultList(): VaultListRouter {
        val child = vaultListBuilder.build(interactor)
        attachChild(child)
        return child
    }

    private fun attachGenerator(): ViewControllable {
        // 생성기 탭은 혼자 쓰므로 부모가 받을 결과가 없고 복사 버튼을 둔다
        val child = passwordGeneratorBuilder.build(listener = null, allowCopy = true)
        attachChild(child)
        return child.viewControllable
    }

    private fun attachSettings(): ViewControllable {
        val child = settingsBuilder.build(interactor)
        attachChild(child)
        return child.viewControllable
    }
}
