package com.keymanage.feature.main

import com.keymanage.feature.messages.DEVICE_STORAGE_FAILED
import com.keymanage.feature.messages.ErrorMessage
import com.keymanage.feature.messages.OFFLINE_NOTICE
import com.keymanage.feature.messages.READ_ONLY_NOTICE

enum class MainTab { VAULT, GENERATOR, SETTINGS }

/** 화면이 그리는 전부 */
data class MainViewState(
    val email: String = "",
    val selectedTab: MainTab = MainTab.VAULT,
    /** 새 버전 앱이 저장한 볼트라 읽기만 할 수 있다 */
    val readOnly: Boolean = false,
    /** 서버와 맞추지 못한 상태. 잠금 해제를 오프라인으로 열었거나, 네트워크가 끊겼거나, 올리지 못한 변경 사항이 있다 (VLT-002) */
    val offline: Boolean = false,
    /** 대기 내용 재전송이 분명한 거절을 받았을 때의 오류. 다음 동기화 성공에 걷힌다 (VLT-002) */
    val syncError: ErrorMessage? = null,
    /** 마지막 기기 저장이 실패했다. 다음 저장이 성공할 때까지 남는다 (VLT-003) */
    val showsDeviceStorageWarning: Boolean = false,
    /** 세션 없이 열었다. 마스터 비밀번호를 다시 입력받아야 동기화한다 (SEC-003, ADR-0019) */
    val needsReconnect: Boolean = false,
    val reconnectPassword: String = "",
    val isReconnecting: Boolean = false,
    val reconnectError: ErrorMessage? = null,
    /** 서버 볼트가 이전 상태로 돌아갔다는 경고를 받아들일지 묻는 중 (VLT-001). [reconnectError]와 함께 보이지 않는다 */
    val isConfirmingReconnectRollback: Boolean = false,
    /** 올리지 못한 변경 사항이 사라진다고 알리고 로그아웃할지 묻는 중 (VLT-002) */
    val isConfirmingLogout: Boolean = false,
    val isLoggingOut: Boolean = false,
) {
    val offlineNotice: String? get() = if (offline) OFFLINE_NOTICE else null
    val readOnlyNotice: String? get() = if (readOnly) READ_ONLY_NOTICE else null
    val deviceStorageWarning: String? get() = if (showsDeviceStorageWarning) DEVICE_STORAGE_FAILED else null
}

/** 화면 입력 전부 */
sealed interface MainViewAction {
    data class SelectTab(val tab: MainTab) : MainViewAction
    data object RequestLock : MainViewAction

    /** 로그아웃 버튼. 대기 중인 변경 사항이 있으면 확인을 먼저 묻는다 */
    data object RequestLogout : MainViewAction
    data object ConfirmLogout : MainViewAction
    data object CancelLogout : MainViewAction

    data class UpdateReconnectPassword(val password: String) : MainViewAction
    data object Reconnect : MainViewAction

    /** 되돌림 경고를 받아들이고 처음 입력한 비밀번호로 다시 연결한다 */
    data object AcceptReconnectRollback : MainViewAction
    data object CancelReconnectRollback : MainViewAction
}
