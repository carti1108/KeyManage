package com.keymanage.feature.main

import com.carti.ribs.PresentableInteractor
import com.carti.ribs.RibDispatchers
import com.carti.ribs.checkMainThread
import com.keymanage.domain.device.DeviceStorageException
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.messages.LOGOUT_CLEAR_FAILED
import com.keymanage.feature.messages.describeError
import com.keymanage.feature.messages.describeErrorMessage
import com.keymanage.feature.messages.sendsToSignIn
import com.keymanage.feature.settings.SettingsListener
import com.keymanage.feature.vaultlist.VaultListListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// 열린 볼트의 틀. 탭(볼트·생성기·설정), 경고·배너, 세션 없이 연 볼트의 재연결, 잠그기·로그아웃 (SEC-003, VLT-002, VLT-003, ADR-0019)
// 원본: Apps/Web/src/ui/screens/VaultHomeScreen.tsx, ui/session/ReconnectPrompt.tsx, ui/session/SessionRoot.tsx의 볼트 화면 부분, iOS Features/Main
// 자식은 선택한 탭이 처음 열릴 때 붙이고, 한 번 붙인 자식은 이 RIB이 내려갈 때까지 유지한다 (iOS와 같다. 웹은 탭을 떠나면 화면을 지운다)

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
