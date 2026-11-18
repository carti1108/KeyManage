package com.keymanage.android.feature.auth.impl

import com.keymanage.android.domain.auth.AuthError
import com.keymanage.android.domain.auth.NewPasswordProblem
import com.keymanage.android.domain.crypto.CryptoError
import com.keymanage.android.domain.remote.ApiError
import com.keymanage.android.domain.remote.ServerErrorCode

/** 인증 실패를 화면 문구로 바꾼다. 문구는 SEC-002·COM-002 사용자 문구 표 그대로다. */
object AuthMessages {
    const val INVALID_EMAIL = "올바른 이메일 주소를 입력해 주세요."
    const val TOO_SHORT = "마스터 비밀번호는 12자 이상이어야 합니다."
    const val MISMATCH = "비밀번호 확인이 일치하지 않습니다."
    const val INVALID_CHARACTER = "마스터 비밀번호에 쓸 수 없는 문자가 있습니다."
    const val LOGIN_FAILED = "이메일 또는 마스터 비밀번호가 올바르지 않습니다."
    const val TOO_MANY_ATTEMPTS = "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요."
    const val EMAIL_TAKEN = "이미 가입된 이메일입니다."
    const val REGISTRATION_DISABLED = "현재 새 계정을 만들 수 없습니다."
    const val NETWORK = "서버에 연결할 수 없습니다. 네트워크 연결을 확인해 주세요."
    const val UNKNOWN = "알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."

    fun of(error: Throwable): String = when (error) {
        is AuthError.InvalidEmail -> INVALID_EMAIL
        is AuthError.WeakNewPassword -> if (error.problem == NewPasswordProblem.TOO_SHORT) TOO_SHORT else MISMATCH
        is CryptoError.InvalidPassword -> INVALID_CHARACTER
        is ApiError.Network -> NETWORK
        is ApiError.Server -> when (error.code) {
            ServerErrorCode.AUTH_FAILED -> LOGIN_FAILED
            ServerErrorCode.TOO_MANY_ATTEMPTS -> TOO_MANY_ATTEMPTS
            ServerErrorCode.EMAIL_ALREADY_REGISTERED -> EMAIL_TAKEN
            ServerErrorCode.REGISTRATION_DISABLED -> REGISTRATION_DISABLED
            else -> UNKNOWN
        }
        else -> UNKNOWN
    }
}
