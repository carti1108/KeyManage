package com.keymanage.android.feature.auth.impl

/** 로그인과 가입은 입력 칸만 다르므로 한 RIB의 두 모드로 둔다. */
enum class AuthMode { LOGIN, SIGNUP }

/** Auth 화면 상태. 마스터 비밀번호는 인증이 끝나면 지운다. */
data class AuthViewState(
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val confirmation: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    /** 빈 칸이 있으면 제출할 수 없다. 형식·길이 검사는 제출 때 문구로 알린다. */
    val canSubmit: Boolean
        get() = !isSubmitting && email.isNotBlank() && password.isNotEmpty() &&
            (mode == AuthMode.LOGIN || confirmation.isNotEmpty())

    override fun toString() = "AuthViewState(mode=$mode, isSubmitting=$isSubmitting, errorMessage=$errorMessage)"
}

/** Auth 화면에서 View가 보내는 동작. */
sealed interface AuthViewAction {
    data class EmailChanged(val value: String) : AuthViewAction
    data class PasswordChanged(val value: String) : AuthViewAction
    data class ConfirmationChanged(val value: String) : AuthViewAction
    data object SwitchMode : AuthViewAction
    data object Submit : AuthViewAction
}
