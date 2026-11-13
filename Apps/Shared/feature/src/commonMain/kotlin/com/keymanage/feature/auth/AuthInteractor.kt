package com.keymanage.feature.auth

import com.carti.ribs.PresentableInteractor
import com.carti.ribs.checkMainThread
import com.keymanage.domain.auth.checkNewMasterPassword
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.feature.SessionPort
import com.keymanage.feature.messages.ErrorMessage
import com.keymanage.feature.messages.describeErrorMessage
import com.keymanage.feature.messages.newPasswordProblemMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// 로그인·가입 화면 (SEC-001, SEC-002)
// 원본: Apps/Web/src/ui/screens/AuthScreen.tsx, ui/session/useSessionAction.ts, iOS Features/Auth

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
