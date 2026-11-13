package com.keymanage.feature.auth

import com.keymanage.domain.generator.isWeak
import com.keymanage.domain.generator.scorePassword
import com.keymanage.feature.messages.ErrorMessage

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
