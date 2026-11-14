package com.keymanage.feature.unlock

import com.keymanage.feature.messages.ErrorMessage

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
    /** 이 계정에 생체 해제를 켜 두어 버튼을 보인다 (웹에는 없고 iOS만 쓰는 선택 기능) */
    val isBiometricsAvailable: Boolean = false,
    /** 기기가 생체 인증을 쓸 수 있지만 이 계정은 아직 켜지 않았다. 다음부터 생체 해제를 쓸지 고르는 칸을 보인다 */
    val offersBiometricEnrollment: Boolean = false,
    /** 비밀번호로 열면 생체 해제를 켠다 */
    val enrollsBiometrics: Boolean = false,
) {
    val biometricButtonTitle: String get() = UnlockMessages.BIOMETRIC_BUTTON
    val biometricEnrollmentTitle: String get() = UnlockMessages.BIOMETRIC_ENROLL
}

/** 화면 입력 전부 */
sealed interface UnlockViewAction {
    data class UpdatePassword(val password: String) : UnlockViewAction
    data object Unlock : UnlockViewAction

    /** 생체 해제. 포트가 없거나 켜지 않았으면 무시한다 */
    data object UnlockWithBiometrics : UnlockViewAction

    /** 비밀번호로 열 때 생체 해제를 켤지 */
    data class SetBiometricEnrollment(val enabled: Boolean) : UnlockViewAction

    /** 되돌림 경고를 받아들이고 처음 입력한 비밀번호로 다시 연다 */
    data object AcceptRollback : UnlockViewAction
    data object CancelRollback : UnlockViewAction

    /** 로그아웃 버튼. 대기 중인 변경 사항이 있으면 확인을 먼저 묻는다 */
    data object RequestLogout : UnlockViewAction
    data object ConfirmLogout : UnlockViewAction
    data object CancelLogout : UnlockViewAction
}
