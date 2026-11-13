@file:OptIn(ExperimentalJsExport::class)

package com.keymanage.websession

import com.carti.ribs.web.ViewStore
import com.carti.ribs.web.WebViewControllable
import com.keymanage.domain.lock.AUTO_LOCK_OPTIONS
import com.keymanage.feature.settings.SettingsModal
import com.keymanage.feature.settings.SettingsNoticeKind
import com.keymanage.feature.settings.SettingsPresentable
import com.keymanage.feature.settings.SettingsViewContract
import com.keymanage.feature.settings.SettingsPresentableListener
import com.keymanage.feature.settings.SettingsViewAction
import com.keymanage.feature.settings.SettingsViewState
import kotlin.time.Duration.Companion.milliseconds

// 설정 화면의 웹 뷰. 자동 잠금 시간은 밀리초 숫자로 주고받는다

// 선택지는 바뀌지 않으므로 한 배열을 모든 상태가 나눠 쓴다. 상태 객체끼리 값 비교가 된다
private val AUTO_LOCK_OPTION_MS: Array<Int> = AUTO_LOCK_OPTIONS.map { it.inWholeMilliseconds.toInt() }.toTypedArray()

/** 설정 화면이 그리는 전부. [modal]은 'none' | 'exportConfirm' | 'restorePassword' | 'changePassword' | 'deleteAccount' */
@JsExport
data class WebSettingsState(
    val email: String,
    /** 서버 주소 줄. 같은 출처면 빈 문자열 */
    val serverAddress: String,
    /** true면 "오프라인", false면 "최신 상태 (동기화 완료)" */
    val syncOffline: Boolean,
    val readOnly: Boolean,
    /** 서버에 닿지 못함. 비밀번호 변경·계정 삭제를 막는다 */
    val accountOffline: Boolean,
    /** 고를 수 있는 자동 잠금 시간(ms). 0은 "즉시" */
    val autoLockOptionsMs: Array<Int>,
    val autoLockTimeoutMs: Int,
    val modal: String,
    /** 내보내기·파일 읽기·복원·비밀번호 변경·계정 삭제 중 하나가 진행 중. 하나만 한다 */
    val inProgress: Boolean,
    val restorePassword: String,
    val currentPassword: String,
    val newPassword: String,
    val passwordConfirmation: String,
    val deletionPassword: String,
    val deletionPhrase: String,
    /** 모달이 열려 있으면 모달 안에, 아니면 버튼 아래에 보이는 오류 */
    val error: String?,
    /** 완료·오프라인 안내 */
    val notice: String?,
    /** [notice]의 성격. 'info' | 'error'. 오프라인처럼 하지 못한 이유는 'error'로 오류 모양으로 보인다 */
    val noticeKind: String,
    val canExportBackup: Boolean,
    val canRestoreBackup: Boolean,
    /** 실제로 바꿀 수 있는지(오프라인이면 false). 진입 버튼은 [canStartChangePassword]를 본다 */
    val canChangePassword: Boolean,
    val canDeleteAccount: Boolean,
    /** 비밀번호 변경 진입 버튼을 켤지. 오프라인이어도 눌러 [notice]로 안내를 받는다 */
    val canStartChangePassword: Boolean,
    /** 계정 삭제 진입 버튼을 켤지. 오프라인이어도 눌러 [notice]로 안내를 받는다 */
    val canStartDeleteAccount: Boolean,
    val canSubmitRestore: Boolean,
    val canSubmitChangePassword: Boolean,
    val canSubmitDeletion: Boolean,
    /** [modal]이 'exportConfirm'일 때 그리는 안내 문구 (VLT-004) */
    val exportNotice: WebConfirmText,
    val restoreDialog: WebRestoreDialogText,
    /** 진입 버튼 이름도 [WebChangePasswordDialogText.title]이다 */
    val changePasswordDialog: WebChangePasswordDialogText,
    /** 진입 버튼 이름도 [WebDeleteAccountDialogText.title]이다 */
    val deleteAccountDialog: WebDeleteAccountDialogText,
)

private fun SettingsNoticeKind.wire(): String = when (this) {
    SettingsNoticeKind.INFO -> "info"
    SettingsNoticeKind.ERROR -> "error"
}

private fun SettingsModal.wire(): String = when (this) {
    SettingsModal.NONE -> "none"
    SettingsModal.EXPORT_CONFIRM -> "exportConfirm"
    SettingsModal.RESTORE_PASSWORD -> "restorePassword"
    SettingsModal.CHANGE_PASSWORD -> "changePassword"
    SettingsModal.DELETE_ACCOUNT -> "deleteAccount"
}

private fun SettingsViewState.toWeb() = WebSettingsState(
    email = email,
    serverAddress = serverAddress,
    syncOffline = syncOffline,
    readOnly = readOnly,
    accountOffline = accountOffline,
    autoLockOptionsMs = if (autoLockOptions == AUTO_LOCK_OPTIONS) AUTO_LOCK_OPTION_MS else autoLockOptions.map { it.inWholeMilliseconds.toInt() }.toTypedArray(),
    autoLockTimeoutMs = autoLockTimeout.inWholeMilliseconds.toInt(),
    modal = modal.wire(),
    inProgress = inProgress,
    restorePassword = restorePassword,
    currentPassword = currentPassword,
    newPassword = newPassword,
    passwordConfirmation = passwordConfirmation,
    deletionPassword = deletionPassword,
    deletionPhrase = deletionPhrase,
    error = error,
    notice = notice,
    noticeKind = noticeKind.wire(),
    canExportBackup = canExportBackup,
    canRestoreBackup = canRestoreBackup,
    canChangePassword = canChangePassword,
    canDeleteAccount = canDeleteAccount,
    canStartChangePassword = canStartChangePassword,
    canStartDeleteAccount = canStartDeleteAccount,
    canSubmitRestore = canSubmitRestore,
    canSubmitChangePassword = canSubmitChangePassword,
    canSubmitDeletion = canSubmitDeletion,
    exportNotice = EXPORT_NOTICE_TEXT,
    restoreDialog = RESTORE_DIALOG_TEXT,
    changePasswordDialog = CHANGE_PASSWORD_DIALOG_TEXT,
    deleteAccountDialog = DELETE_ACCOUNT_DIALOG_TEXT,
)

@JsExport
class WebSettingsView : SettingsViewContract, WebViewControllable {
    override val screenKey: String = "settings"
    val store: ViewStore<WebSettingsState> = ViewStore(SettingsViewState().toWeb())

    @JsExport.Ignore
    override var listener: SettingsPresentableListener? = null

    /** @param ms [WebSettingsState.autoLockOptionsMs] 중 하나 */
    fun selectAutoLock(ms: Int) = send(SettingsViewAction.SelectAutoLock(ms.milliseconds))
    fun requestLock() = send(SettingsViewAction.RequestLock)
    fun requestLogout() = send(SettingsViewAction.RequestLogout)

    /** 내보내기 안내를 연다 */
    fun startExport() = send(SettingsViewAction.StartExport)
    fun confirmExport() = send(SettingsViewAction.ConfirmExport)

    /** 파일 선택 창을 연다. 고른 파일의 형식이 맞으면 비밀번호 모달이 열린다 */
    fun startRestore() = send(SettingsViewAction.StartRestore)
    fun editRestorePassword(value: String) = send(SettingsViewAction.EditRestorePassword(value))
    fun submitRestore() = send(SettingsViewAction.SubmitRestore)

    fun startChangePassword() = send(SettingsViewAction.StartChangePassword)
    fun editCurrentPassword(value: String) = send(SettingsViewAction.EditCurrentPassword(value))
    fun editNewPassword(value: String) = send(SettingsViewAction.EditNewPassword(value))
    fun editPasswordConfirmation(value: String) = send(SettingsViewAction.EditPasswordConfirmation(value))
    fun submitChangePassword() = send(SettingsViewAction.SubmitChangePassword)

    fun startDeleteAccount() = send(SettingsViewAction.StartDeleteAccount)
    fun editDeletionPassword(value: String) = send(SettingsViewAction.EditDeletionPassword(value))

    /** 한글 자모가 나뉘어 들어와도(NFD) RIB이 NFC로 맞춘다 */
    fun editDeletionPhrase(value: String) = send(SettingsViewAction.EditDeletionPhrase(value))
    fun submitDeleteAccount() = send(SettingsViewAction.SubmitDeleteAccount)

    /** 모달을 닫고 입력을 비운다. 진행 중에는 무시한다 */
    fun cancelModal() = send(SettingsViewAction.CancelModal)

    /** 오류·안내를 지운다 */
    fun dismissMessage() = send(SettingsViewAction.DismissMessage)

    private fun send(action: SettingsViewAction) {
        listener?.handle(action)
    }

    @JsExport.Ignore
    override fun render(state: SettingsViewState) = store.set(state.toWeb())
}
