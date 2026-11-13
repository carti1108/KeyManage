package com.keymanage.feature.unlock

import com.carti.ribs.Builder
import com.carti.ribs.Dependency
import com.carti.ribs.Presentable
import com.carti.ribs.PresentableInteractor
import com.carti.ribs.RibDispatchers
import com.carti.ribs.ViewControllable
import com.carti.ribs.ViewableRouter
import com.carti.ribs.checkMainThread
import com.keymanage.domain.device.DeviceStorageException
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.feature.SessionPort
import com.keymanage.feature.messages.ErrorMessage
import com.keymanage.feature.messages.LOGOUT_CLEAR_FAILED
import com.keymanage.feature.messages.describeError
import com.keymanage.feature.messages.describeErrorMessage
import com.keymanage.feature.messages.sendsToSignIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// 잠금 화면 (SEC-003). 마스터 비밀번호로 열고, 동기화 대기 변경을 확인한 뒤 로그아웃한다 (VLT-002)
// 원본: Apps/Web/src/ui/screens/UnlockScreen.tsx, ui/session/SessionRoot.tsx의 unlock·logout 흐름, iOS Features/Unlock

/** 화면이 그리는 전부 */
data class UnlockViewState(
    val email: String = "",
    val password: String = "",
    /** 잠금 해제 또는 로그아웃 요청이 진행 중 */
    val isLoading: Boolean = false,
    val error: ErrorMessage? = null,
    /** 서버 볼트가 이전 상태로 돌아갔다는 경고를 받아들일지 묻는 중 (VLT-001). [error]와 함께 보이지 않는다 */
    val isConfirmingRollback: Boolean = false,
    /** 올리지 못한 변경 사항이 사라진다고 알리고 로그아웃할지 묻는 중 (VLT-002) */
    val isConfirmingLogout: Boolean = false,
    /** 생체 해제를 쓸 수 있음. 아직 연결하지 않았으므로 항상 false다 (웹에는 없고 iOS만 쓰는 선택 기능) */
    val isBiometricsAvailable: Boolean = false,
)

/** 화면 입력 전부 */
sealed interface UnlockViewAction {
    data class UpdatePassword(val password: String) : UnlockViewAction
    data object Unlock : UnlockViewAction

    /** 연결 전이라 아무 일도 하지 않는다 */
    data object UnlockWithBiometrics : UnlockViewAction

    /** 되돌림 경고를 받아들이고 처음 입력한 비밀번호로 다시 연다 */
    data object AcceptRollback : UnlockViewAction
    data object CancelRollback : UnlockViewAction

    /** 로그아웃 버튼. 대기 중인 변경 사항이 있으면 확인을 먼저 묻는다 */
    data object RequestLogout : UnlockViewAction
    data object ConfirmLogout : UnlockViewAction
    data object CancelLogout : UnlockViewAction
}

interface UnlockPresentableListener {
    fun handle(action: UnlockViewAction)
}

/** Interactor가 쓰는 화면 계약 (RIBs-iOS Presentable, carti ADR-0010) */
interface UnlockPresentable : Presentable {
    /** 활성 구간에만 설정된다. 비활성이면 null이라 입력이 무시된다 */
    var listener: UnlockPresentableListener?
    fun render(state: UnlockViewState)
}

/** Router가 쓰는 화면 계약 */
interface UnlockViewControllable : ViewControllable

/** 플랫폼 뷰가 구현하는 둘의 합 */
interface UnlockViewContract : UnlockPresentable, UnlockViewControllable

fun interface UnlockViewFactory {
    fun makeUnlockView(): UnlockViewContract
}

interface UnlockDependency : Dependency {
    val unlockViewFactory: UnlockViewFactory
    val session: SessionPort
}

/** 자식 → 부모 */
interface UnlockListener {
    /** 볼트를 열었다. [offline]이 true면 서버에 닿지 못해 기기에 저장한 볼트로 열었다 (VLT-002) */
    fun didUnlock(offline: Boolean)

    /**
     * 로그아웃했다. 이 기기의 볼트 기록을 지우지 못했으면 로그인 화면에 보일 [notice]를 담는다 (ADR-0019).
     * 서버 토큰 폐기만 실패한 경우는 이 기기가 이미 로그아웃됐으므로 알리지 않는다 (SEC-002)
     */
    fun didLogout(notice: String?)

    /** 세션이 만료됐거나 다른 기기에서 마스터 비밀번호를 바꿨다. 로그인 화면에 [notice]를 보인다 (SEC-002, SEC-003) */
    fun didRequireSignIn(notice: String)
}

class UnlockBuilder(dependency: UnlockDependency) : Builder<UnlockDependency>(dependency) {
    fun build(listener: UnlockListener): UnlockRouter {
        val view = dependency.unlockViewFactory.makeUnlockView()
        val interactor = UnlockInteractor(view, dependency.session, listener)
        return UnlockRouter(interactor, view)
    }
}

/** 상태를 들고 입력을 해석한다. 요청은 활성 구간 스코프에서 실행하므로 detach되면 취소된다 */
class UnlockInteractor(
    presenter: UnlockPresentable,
    private val session: SessionPort,
    private val listener: UnlockListener,
) : PresentableInteractor<UnlockPresentable>(presenter), UnlockPresentableListener {
    private var state = UnlockViewState()

    /** 되돌림 경고를 받아들이면 다시 보낼 비밀번호. 입력 칸은 비우지만 처음 요청의 값을 쓴다 */
    private var rollbackPassword: String? = null

    // 다른 탭의 대기 내용을 확인하는 동안 로그아웃을 다시 받지 않는다
    private var isCheckingLogout = false

    private var logoutInFlight = false

    /**
     * 로그아웃이 진행 중이다. 세션이 먼저 로그인 전 상태로 알려도 부모는 이 작업이 끝나 결과를 알릴 때까지 이 화면을 내리지 않는다.
     * 내리면 기기 기록 정리와 서버 토큰 폐기가 취소된다 (ADR-0019, SEC-002)
     */
    val isFinishingSession: Boolean get() = logoutInFlight

    override fun didBecomeActive() {
        presenter.listener = this
        state = state.copy(email = (session.state() as? SessionState.Locked)?.email.orEmpty())
        render()
    }

    override fun willResignActive() {
        presenter.listener = null
        rollbackPassword = null
    }

    override fun handle(action: UnlockViewAction) {
        // 내려간 뒤 늦게 들어온 입력(뷰가 쥐고 있던 listener)은 무시한다
        if (!isActive) return
        when (action) {
            is UnlockViewAction.UpdatePassword -> update { copy(password = action.password) }
            UnlockViewAction.Unlock -> unlock(state.password, acceptRollback = false)
            // 생체 해제는 아직 연결하지 않는다
            UnlockViewAction.UnlockWithBiometrics -> Unit
            UnlockViewAction.AcceptRollback -> acceptRollback()
            UnlockViewAction.CancelRollback -> {
                rollbackPassword = null
                update { copy(isConfirmingRollback = false) }
            }
            UnlockViewAction.RequestLogout -> requestLogout()
            UnlockViewAction.ConfirmLogout -> if (state.isConfirmingLogout) logout()
            UnlockViewAction.CancelLogout -> update { copy(isConfirmingLogout = false) }
        }
    }

    private fun update(change: UnlockViewState.() -> UnlockViewState) {
        state = state.change()
        // 로그아웃은 이 RIB이 내려간 뒤에도 끝까지 하므로 그때는 그리지 않는다
        if (isActive) render()
    }

    private fun render() {
        checkMainThread("UnlockPresentable.render")
        presenter.render(state)
    }

    private fun acceptRollback() {
        val password = rollbackPassword ?: return
        unlock(password, acceptRollback = true)
    }

    private fun unlock(password: String, acceptRollback: Boolean) {
        if (state.isLoading) return
        rollbackPassword = null
        update { copy(isLoading = true, error = null, isConfirmingRollback = false) }
        coroutineScope.launch {
            val result = try {
                Result.success(session.unlock(password, acceptRollback))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            // 실패한 비밀번호를 입력 칸에 남기지 않는다. 되돌림 확인용 재시도는 요청에 담긴 값을 쓴다
            val failure = result.exceptionOrNull()
            when {
                failure == null -> {
                    update { copy(isLoading = false, password = "") }
                    // 부모가 이 RIB을 내릴 수 있으므로 알림은 마지막에 한다
                    listener.didUnlock(offline = result.getOrDefault(false))
                }
                failure is RevisionRollbackException && !acceptRollback -> {
                    rollbackPassword = password
                    update { copy(isLoading = false, password = "", isConfirmingRollback = true) }
                }
                sendsToSignIn(failure) -> {
                    update { copy(isLoading = false, password = "") }
                    listener.didRequireSignIn(describeError(failure))
                }
                else -> update { copy(isLoading = false, password = "", error = describeErrorMessage(failure)) }
            }
        }
    }

    /**
     * 올리지 못한 변경 사항이 있으면 사라진다고 먼저 알린다 (VLT-002).
     * 이 탭에 없어도 다른 탭이 기기 기록에 남겼을 수 있으니 기록을 확인한다 (ADR-WEB-001)
     */
    private fun requestLogout() {
        if (state.isLoading || isCheckingLogout) return
        if ((session.state() as? SessionState.Locked)?.pendingSync == true) {
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
     * 서버 토큰 폐기까지 기다린 결과로 부모에 알린다 (웹은 기다리지 않고 화면을 바꿨다).
     * 세션은 이 기기 기록을 정리하는 도중 알림을 보내 부모가 이 화면을 내릴 수 있다. 그래도 정리와 토큰 폐기는 끝까지 해야 하고
     * 결과(기록을 지우지 못했다는 안내)도 알려야 하므로 이 RIB의 스코프가 아닌 별도 스코프에서 한다
     */
    private fun logout() {
        if (state.isLoading) return
        logoutInFlight = true
        update { copy(isLoading = true, error = null, isConfirmingLogout = false) }
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
            update { copy(isLoading = false, password = "") }
            listener.didLogout(notice)
        }
    }
}

class UnlockRouter(interactor: UnlockInteractor, view: UnlockViewControllable) :
    ViewableRouter<UnlockInteractor, UnlockViewControllable>(interactor, view)
