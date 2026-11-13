@file:OptIn(ExperimentalJsExport::class)

package com.keymanage.websession

import com.carti.ribs.web.ViewStore
import com.carti.ribs.web.WebViewControllable
import com.keymanage.feature.auth.AuthMode
import com.keymanage.feature.auth.AuthPresentable
import com.keymanage.feature.auth.AuthViewContract
import com.keymanage.feature.auth.AuthPresentableListener
import com.keymanage.feature.auth.AuthViewAction
import com.keymanage.feature.auth.AuthViewState
import com.keymanage.feature.unlock.UnlockPresentable
import com.keymanage.feature.unlock.UnlockViewContract
import com.keymanage.feature.unlock.UnlockPresentableListener
import com.keymanage.feature.unlock.UnlockViewAction
import com.keymanage.feature.unlock.UnlockViewState

// 로그인·가입 화면(Auth)과 잠금 해제 화면(Unlock)의 웹 뷰

/** 로그인·가입 화면이 그리는 전부. [mode]는 'login' | 'register' */
@JsExport
data class WebAuthState(
    val mode: String,
    val email: String,
    val password: String,
    val confirmation: String,
    val isLoading: Boolean,
    /** 로그인 화면으로 돌아온 이유(세션 만료 등) */
    val notice: String?,
    val error: WebMessage?,
    /** 서버 볼트가 이전 상태로 돌아갔다는 경고를 받아들일지 묻는 중. [error]와 함께 보이지 않는다 */
    val isConfirmingRollback: Boolean,
    val isRegistering: Boolean,
    /** 가입 화면에서만, 비밀번호가 있을 때 강도 게이지를 보인다 */
    val showsStrengthGauge: Boolean,
    /** 0~100 */
    val passwordStrengthScore: Int,
    val showsWeakPasswordWarning: Boolean,
    /** [isConfirmingRollback]일 때 그리는 확인 창 문구 (VLT-001) */
    val rollbackWarning: WebConfirmText,
    /** 가입 화면에서 비밀번호 칸 설명으로 보이는 안내 (SEC-001) */
    val masterPasswordSetup: WebMessage,
    /** [showsWeakPasswordWarning]일 때 보이는 문구 (SEC-002) */
    val weakPasswordWarning: String,
)

private fun AuthViewState.toWeb() = WebAuthState(
    mode = when (mode) {
        AuthMode.LOGIN -> "login"
        AuthMode.REGISTER -> "register"
    },
    email = email,
    password = password,
    confirmation = confirmation,
    isLoading = isLoading,
    notice = notice,
    error = error?.toWeb(),
    isConfirmingRollback = isConfirmingRollback,
    isRegistering = isRegistering,
    showsStrengthGauge = showsStrengthGauge,
    passwordStrengthScore = passwordStrengthScore,
    showsWeakPasswordWarning = showsWeakPasswordWarning,
    rollbackWarning = ROLLBACK_WARNING_TEXT,
    masterPasswordSetup = MASTER_PASSWORD_SETUP_TEXT,
    weakPasswordWarning = WEAK_MASTER_PASSWORD_TEXT,
)

@JsExport
class WebAuthView : AuthViewContract, WebViewControllable {
    override val screenKey: String = "auth"
    val store: ViewStore<WebAuthState> = ViewStore(AuthViewState().toWeb())

    @JsExport.Ignore
    override var listener: AuthPresentableListener? = null

    fun switchMode() = send(AuthViewAction.SwitchMode)
    fun updateEmail(value: String) = send(AuthViewAction.UpdateEmail(value))
    fun updatePassword(value: String) = send(AuthViewAction.UpdatePassword(value))
    fun updateConfirmation(value: String) = send(AuthViewAction.UpdateConfirmation(value))
    fun submit() = send(AuthViewAction.Submit)
    fun acceptRollback() = send(AuthViewAction.AcceptRollback)
    fun cancelRollback() = send(AuthViewAction.CancelRollback)

    private fun send(action: AuthViewAction) {
        listener?.handle(action)
    }

    @JsExport.Ignore
    override fun render(state: AuthViewState) = store.set(state.toWeb())
}

/** 잠금 해제 화면이 그리는 전부 */
@JsExport
data class WebUnlockState(
    val email: String,
    val password: String,
    /** 잠금 해제 또는 로그아웃 요청이 진행 중 */
    val isLoading: Boolean,
    val error: WebMessage?,
    val isConfirmingRollback: Boolean,
    /** 올리지 못한 변경 사항이 사라진다고 알리고 로그아웃할지 묻는 중 */
    val isConfirmingLogout: Boolean,
    /** 웹은 항상 false다 */
    val isBiometricsAvailable: Boolean,
    /** [isConfirmingRollback]일 때 그리는 확인 창 문구 (VLT-001) */
    val rollbackWarning: WebConfirmText,
    /** [isConfirmingLogout]일 때 그리는 확인 창 문구 (VLT-002) */
    val pendingLogoutConfirm: WebConfirmText,
)

private fun UnlockViewState.toWeb() = WebUnlockState(
    email = email,
    password = password,
    isLoading = isLoading,
    error = error?.toWeb(),
    isConfirmingRollback = isConfirmingRollback,
    isConfirmingLogout = isConfirmingLogout,
    isBiometricsAvailable = isBiometricsAvailable,
    rollbackWarning = ROLLBACK_WARNING_TEXT,
    pendingLogoutConfirm = PENDING_LOGOUT_TEXT,
)

@JsExport
class WebUnlockView : UnlockViewContract, WebViewControllable {
    override val screenKey: String = "unlock"
    val store: ViewStore<WebUnlockState> = ViewStore(UnlockViewState().toWeb())

    @JsExport.Ignore
    override var listener: UnlockPresentableListener? = null

    fun updatePassword(value: String) = send(UnlockViewAction.UpdatePassword(value))
    fun unlock() = send(UnlockViewAction.Unlock)
    fun acceptRollback() = send(UnlockViewAction.AcceptRollback)
    fun cancelRollback() = send(UnlockViewAction.CancelRollback)
    fun requestLogout() = send(UnlockViewAction.RequestLogout)
    fun confirmLogout() = send(UnlockViewAction.ConfirmLogout)
    fun cancelLogout() = send(UnlockViewAction.CancelLogout)

    private fun send(action: UnlockViewAction) {
        listener?.handle(action)
    }

    @JsExport.Ignore
    override fun render(state: UnlockViewState) = store.set(state.toWeb())
}
