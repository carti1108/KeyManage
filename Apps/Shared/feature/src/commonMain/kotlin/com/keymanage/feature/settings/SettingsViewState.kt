package com.keymanage.feature.settings

import com.keymanage.domain.lock.AUTO_LOCK_OPTIONS
import com.keymanage.domain.lock.DEFAULT_AUTO_LOCK
import com.keymanage.feature.messages.ACCOUNT_DELETION_PHRASE
import kotlin.time.Duration

/** 열려 있는 대화상자. 안내·입력 칸의 문구는 messages 패키지(ExportNotice, RestoreDialog, ChangePasswordDialog, DeleteAccountDialog)를 쓴다 */
enum class SettingsModal { NONE, EXPORT_CONFIRM, RESTORE_PASSWORD, CHANGE_PASSWORD, DELETE_ACCOUNT }

/** [SettingsViewState.notice]의 성격. 완료는 INFO, 오프라인처럼 하지 못한 이유는 ERROR다(옛 웹은 오프라인 안내를 오류 모양으로 보였다) */
enum class SettingsNoticeKind { INFO, ERROR }

data class SettingsViewState(
    val email: String = "",
    /** 서버 주소 줄. 같은 출처면 빈 문자열 */
    val serverAddress: String = "",
    /** 동기화 상태 줄. true면 "오프라인", false면 "최신 상태 (동기화 완료)" (네트워크 끊김, 올리지 못한 변경 사항, 세션 없음) */
    val syncOffline: Boolean = false,
    /** 새 버전 앱이 저장한 볼트. 복원·비밀번호 변경을 막는다 */
    val readOnly: Boolean = false,
    /** 서버에 닿지 못함(네트워크 끊김 또는 세션 없이 연 볼트). 비밀번호 변경·계정 삭제를 막는다 (SEC-004·SEC-005 규칙 1) */
    val accountOffline: Boolean = false,
    val autoLockOptions: List<Duration> = AUTO_LOCK_OPTIONS,
    val autoLockTimeout: Duration = DEFAULT_AUTO_LOCK,
    val modal: SettingsModal = SettingsModal.NONE,
    /** 내보내기·파일 읽기·복원·비밀번호 변경·계정 삭제 중 하나가 진행 중. 하나만 한다 */
    val inProgress: Boolean = false,
    val restorePassword: String = "",
    val currentPassword: String = "",
    val newPassword: String = "",
    val passwordConfirmation: String = "",
    val deletionPassword: String = "",
    /** 앞뒤 공백까지 [ACCOUNT_DELETION_PHRASE]와 같아야 한다. 한글 자모가 나뉘어 들어오면(NFD) 뷰가 NFC로 맞춰 보낸다 */
    val deletionPhrase: String = "",
    /** 모달이 열려 있으면 모달 안에, 아니면 버튼 아래에 보이는 오류 */
    val error: String? = null,
    /** 완료·오프라인 안내 */
    val notice: String? = null,
    /** [notice]의 성격. [notice]가 있을 때만 뜻이 있다 */
    val noticeKind: SettingsNoticeKind = SettingsNoticeKind.INFO,
) {
    val canExportBackup: Boolean get() = !inProgress
    val canRestoreBackup: Boolean get() = !readOnly && !inProgress
    val canChangePassword: Boolean get() = !readOnly && !accountOffline && !inProgress
    val canDeleteAccount: Boolean get() = !accountOffline && !inProgress

    /**
     * 비밀번호 변경 진입 버튼을 켤지. 오프라인이어도 눌러 안내([notice])를 받아야 하므로 [accountOffline]은 보지 않는다.
     * 실제로 바꿀 수 있는지는 [canChangePassword]
     */
    val canStartChangePassword: Boolean get() = !readOnly && !inProgress

    /** 계정 삭제 진입 버튼을 켤지. 오프라인이어도 눌러 안내를 받는다. 실제로 지울 수 있는지는 [canDeleteAccount] */
    val canStartDeleteAccount: Boolean get() = !inProgress
    val canSubmitRestore: Boolean get() = restorePassword.isNotEmpty() && !inProgress
    val canSubmitChangePassword: Boolean get() = currentPassword.isNotEmpty() && !inProgress
    val canSubmitDeletion: Boolean get() = deletionPassword.isNotEmpty() && deletionPhrase == ACCOUNT_DELETION_PHRASE && !inProgress
}

sealed interface SettingsViewAction {
    data class SelectAutoLock(val timeout: Duration) : SettingsViewAction
    data object RequestLock : SettingsViewAction
    data object RequestLogout : SettingsViewAction

    /** 내보내기 안내를 연다 */
    data object StartExport : SettingsViewAction
    data object ConfirmExport : SettingsViewAction

    /** 파일 선택 창을 연다. 고른 파일의 형식이 맞으면 비밀번호 모달이 열린다 */
    data object StartRestore : SettingsViewAction
    data class EditRestorePassword(val value: String) : SettingsViewAction
    data object SubmitRestore : SettingsViewAction

    data object StartChangePassword : SettingsViewAction
    data class EditCurrentPassword(val value: String) : SettingsViewAction
    data class EditNewPassword(val value: String) : SettingsViewAction
    data class EditPasswordConfirmation(val value: String) : SettingsViewAction
    data object SubmitChangePassword : SettingsViewAction

    data object StartDeleteAccount : SettingsViewAction
    data class EditDeletionPassword(val value: String) : SettingsViewAction
    data class EditDeletionPhrase(val value: String) : SettingsViewAction
    data object SubmitDeleteAccount : SettingsViewAction

    /** 모달을 닫고 입력을 비운다. 진행 중에는 무시한다 */
    data object CancelModal : SettingsViewAction

    /** 오류·안내를 지운다 */
    data object DismissMessage : SettingsViewAction
}
