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
    /** 생체 해제 버튼을 보일지: 켜져 있고 감싼 키가 있으며 마지막 비밀번호 해제 뒤 14일 안이다. 웹은 늘 false (SEC-003 규칙 4) */
    val isBiometricsAvailable: Boolean = false,
    /** 생체 해제를 켜 두었는데 지금은 마스터 비밀번호가 필요한 이유(세션 만료). 없으면 null (SEC-003 규칙 8) */
    val biometricNotice: String? = null,
)

/** 화면 입력 전부 */
sealed interface UnlockViewAction {
    data class UpdatePassword(val password: String) : UnlockViewAction
    data object Unlock : UnlockViewAction

    /** 생체 인증 창을 띄워 연다. [UnlockViewState.isBiometricsAvailable]이 아니면 무시한다 */
    data object UnlockWithBiometrics : UnlockViewAction

    /** 되돌림 경고를 받아들이고 처음 입력한 비밀번호로 다시 연다 */
    data object AcceptRollback : UnlockViewAction
    data object CancelRollback : UnlockViewAction

    /** 로그아웃 버튼. 대기 중인 변경 사항이 있으면 확인을 먼저 묻는다 */
    data object RequestLogout : UnlockViewAction
    data object ConfirmLogout : UnlockViewAction
    data object CancelLogout : UnlockViewAction
}
