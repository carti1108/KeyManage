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
