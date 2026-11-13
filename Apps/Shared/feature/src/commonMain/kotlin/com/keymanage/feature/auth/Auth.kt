package com.keymanage.feature.auth

import com.carti.ribs.Builder
import com.carti.ribs.Dependency
import com.carti.ribs.Presentable
import com.carti.ribs.PresentableInteractor
import com.carti.ribs.ViewControllable
import com.carti.ribs.ViewableRouter
import com.carti.ribs.checkMainThread
import com.keymanage.domain.auth.checkNewMasterPassword
import com.keymanage.domain.generator.isWeak
import com.keymanage.domain.generator.scorePassword
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.feature.SessionPort
import com.keymanage.feature.messages.ErrorMessage
import com.keymanage.feature.messages.describeErrorMessage
import com.keymanage.feature.messages.newPasswordProblemMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// 로그인·가입 화면 (SEC-001, SEC-002)
// 원본: Apps/Web/src/ui/screens/AuthScreen.tsx, ui/session/useSessionAction.ts, iOS Features/Auth

enum class AuthMode { LOGIN, REGISTER }

/** 화면이 그리는 전부 */
data class AuthViewState(
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val confirmation: String = "",
    val isLoading: Boolean = false,
    /** 로그인 화면으로 돌아온 이유(세션 만료 등). 사용자가 다시 시도하면 지운다 */
    val notice: String? = null,
    val error: ErrorMessage? = null,
    /** 서버 볼트가 이전 상태로 돌아갔다는 경고를 받아들일지 묻는 중 (VLT-001). [error]와 함께 보이지 않는다 */
    val isConfirmingRollback: Boolean = false,
) {
    val isRegistering: Boolean get() = mode == AuthMode.REGISTER

    /** 가입 화면에서만, 비밀번호가 있을 때 강도 게이지를 보인다 (SEC-002) */
    val showsStrengthGauge: Boolean get() = isRegistering && password.isNotEmpty()

    /** GEN-001 점수(0~100) */
    val passwordStrengthScore: Int get() = scorePassword(password)

    /** 안내만 하고 가입은 막지 않는다 (SEC-002) */
    val showsWeakPasswordWarning: Boolean get() = showsStrengthGauge && isWeak(passwordStrengthScore)
}

/** 화면 입력 전부 */
sealed interface AuthViewAction {
    data object SwitchMode : AuthViewAction
    data class UpdateEmail(val email: String) : AuthViewAction
    data class UpdatePassword(val password: String) : AuthViewAction
    data class UpdateConfirmation(val confirmation: String) : AuthViewAction
    data object Submit : AuthViewAction

    /** 되돌림 경고를 받아들이고 같은 요청을 다시 보낸다 */
    data object AcceptRollback : AuthViewAction
    data object CancelRollback : AuthViewAction
}

interface AuthPresentableListener {
    fun handle(action: AuthViewAction)
}

/** Interactor가 쓰는 화면 계약 (RIBs-iOS Presentable, carti ADR-0010) */
interface AuthPresentable : Presentable {
    /** 활성 구간에만 설정된다. 비활성이면 null이라 입력이 무시된다 */
    var listener: AuthPresentableListener?
    fun render(state: AuthViewState)
}

/** Router가 쓰는 화면 계약 */
interface AuthViewControllable : ViewControllable

/** 플랫폼 뷰가 구현하는 둘의 합 */
interface AuthViewContract : AuthPresentable, AuthViewControllable

fun interface AuthViewFactory {
    fun makeAuthView(): AuthViewContract
}

interface AuthDependency : Dependency {
    val authViewFactory: AuthViewFactory
    val session: SessionPort
}

/** 자식 → 부모 */
interface AuthListener {
    /** 로그인 또는 가입에 성공했다. 부모가 세션 상태를 다시 읽고 화면을 바꾼다 */
    fun authDidComplete()
}

class AuthBuilder(dependency: AuthDependency) : Builder<AuthDependency>(dependency) {
    /**
     * @param notice 로그인 화면에 띄울 안내(세션 만료, 다른 기기 비밀번호 변경, 계정 삭제 완료 등).
     * 사용자가 다시 시도하거나 모드를 바꾸면 이 RIB이 화면에서 지운다. 부모는 넘긴 뒤 자기 값을 비운다
     */
    fun build(listener: AuthListener, notice: String? = null): AuthRouter {
        val view = dependency.authViewFactory.makeAuthView()
        val interactor = AuthInteractor(view, dependency.session, listener, notice)
        return AuthRouter(interactor, view)
    }
}

/** 상태를 들고 입력을 해석한다. 요청은 활성 구간 스코프에서 실행하므로 detach되면 취소된다 */
class AuthInteractor(
    presenter: AuthPresentable,
    private val session: SessionPort,
    private val listener: AuthListener,
    notice: String? = null,
) : PresentableInteractor<AuthPresentable>(presenter), AuthPresentableListener {
    private var state = AuthViewState(notice = notice)

    /** 되돌림 경고를 받아들이면 다시 보낼 로그인 요청. 입력 칸이 바뀌어도 처음 요청의 값을 쓴다 */
    private var rollbackRetry: LoginRequest? = null

    override fun didBecomeActive() {
        presenter.listener = this
        render()
    }

    override fun willResignActive() {
        presenter.listener = null
        rollbackRetry = null
    }

    override fun handle(action: AuthViewAction) {
        // 비활성 뒤 늦게 온 입력은 무시한다(옛 Presenter가 onAction을 비워 막던 것)
        if (!isActive) return
        when (action) {
            AuthViewAction.SwitchMode -> switchMode()
            is AuthViewAction.UpdateEmail -> update { copy(email = action.email) }
            is AuthViewAction.UpdatePassword -> update { copy(password = action.password) }
            is AuthViewAction.UpdateConfirmation -> update { copy(confirmation = action.confirmation) }
            AuthViewAction.Submit -> submit()
            AuthViewAction.AcceptRollback -> acceptRollback()
            AuthViewAction.CancelRollback -> {
                rollbackRetry = null
                update { copy(isConfirmingRollback = false) }
            }
        }
    }

    private fun update(change: AuthViewState.() -> AuthViewState) {
        state = state.change()
        render()
    }

    private fun render() {
        checkMainThread("AuthPresentable.render")
        presenter.render(state)
    }

    private fun switchMode() {
        if (state.isLoading) return
        rollbackRetry = null
        update {
            copy(
                mode = if (isRegistering) AuthMode.LOGIN else AuthMode.REGISTER,
                confirmation = "",
                notice = null,
                error = null,
                isConfirmingRollback = false,
            )
        }
    }

    private fun submit() {
        if (state.isLoading) return
        update { copy(notice = null) }
        val current = state
        if (!current.isRegistering) {
            val request = LoginRequest(current.email, current.password)
            launchRequest(rollbackRetry = request) { session.login(request.email, request.password, acceptRollback = false) }
            return
        }
        val problem = checkNewMasterPassword(current.password, current.confirmation)
        if (problem != null) {
            update { copy(error = ErrorMessage(body = newPasswordProblemMessage(problem)), isConfirmingRollback = false) }
            return
        }
        launchRequest(rollbackRetry = null) { session.register(current.email, current.password) }
    }

    private fun acceptRollback() {
        val request = rollbackRetry ?: return
        if (state.isLoading) return
        launchRequest(rollbackRetry = null) { session.login(request.email, request.password, acceptRollback = true) }
    }

    /**
     * 요청을 한 번에 하나만 보내고 결과로 상태를 바꾼다.
     * @param rollbackRetry 서버 볼트 되돌림이 나왔을 때 수락하면 다시 보낼 요청. 없으면 되돌림도 일반 오류로 보인다
     */
    private fun launchRequest(rollbackRetry: LoginRequest?, call: suspend () -> Unit) {
        this.rollbackRetry = null
        update { copy(isLoading = true, error = null, isConfirmingRollback = false) }
        coroutineScope.launch {
            val failure = try {
                call()
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                error
            }
            when {
                failure == null -> {
                    // 비밀번호를 화면에 남기지 않는다. 부모가 이 RIB을 내릴 수 있으므로 알림은 마지막에 한다
                    update { copy(isLoading = false, password = "", confirmation = "") }
                    listener.authDidComplete()
                }
                failure is RevisionRollbackException && rollbackRetry != null -> {
                    this@AuthInteractor.rollbackRetry = rollbackRetry
                    update { copy(isLoading = false, isConfirmingRollback = true) }
                }
                else -> update { copy(isLoading = false, error = describeErrorMessage(failure)) }
            }
        }
    }

    private class LoginRequest(val email: String, val password: String)
}

class AuthRouter(interactor: AuthInteractor, view: AuthViewControllable) :
    ViewableRouter<AuthInteractor, AuthViewControllable>(interactor, view)
