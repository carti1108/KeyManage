@file:OptIn(ExperimentalJsExport::class)

package com.keymanage.websession

import com.keymanage.domain.auth.NewMasterPasswordProblem
import com.keymanage.feature.messages.ChangePasswordDialog
import com.keymanage.feature.messages.DeleteAccountDialog
import com.keymanage.feature.messages.ExportNotice
import com.keymanage.feature.messages.MasterPasswordSetup
import com.keymanage.feature.messages.PendingLogoutConfirm
import com.keymanage.feature.messages.ReconnectPrompt
import com.keymanage.feature.messages.RestoreDialog
import com.keymanage.feature.messages.RollbackWarning
import com.keymanage.feature.messages.WEAK_MASTER_PASSWORD_WARNING
import com.keymanage.feature.messages.newPasswordProblemMessage
import com.keymanage.feature.vaultlist.VaultListMessages

// 웹 State가 들고 내려가는 정책 문구 (SEC-001~005, VLT-001·002·004, GEN-001).
// React가 문구를 직접 갖지 않고 State 값을 그대로 그리도록, 공유 messages 상수를 JS 친화 값으로 옮겨 둔다.
// 문구는 바뀌지 않으므로 한 번 만든 객체를 모든 상태가 나눠 쓴다(상태 객체끼리 값 비교가 된다)

/** 확인 창 하나의 문구. 취소 버튼도 정책 문구라 함께 든다 */
@JsExport
data class WebConfirmText(val title: String, val body: String, val confirm: String, val cancel: String)

/** 세션 없이 연 볼트에서 마스터 비밀번호를 다시 받는 칸의 문구 */
@JsExport
data class WebReconnectPrompt(val body: String, val action: String)

/** 백업 복원 대화상자 문구 */
@JsExport
data class WebRestoreDialogText(
    val title: String,
    val body: String,
    val passwordLabel: String,
    val submit: String,
    val cancel: String,
    val progress: String,
)

/** 마스터 비밀번호 변경 대화상자 문구. [newPasswordRule]은 새 비밀번호 칸 아래에 늘 보이는 규칙 안내 */
@JsExport
data class WebChangePasswordDialogText(
    val title: String,
    val body: String,
    val currentLabel: String,
    val newLabel: String,
    val confirmLabel: String,
    val newPasswordRule: String,
    val submit: String,
    val cancel: String,
    val progress: String,
)

/** 계정 삭제 대화상자 문구 */
@JsExport
data class WebDeleteAccountDialogText(
    val title: String,
    val body: String,
    val passwordLabel: String,
    val phraseLabel: String,
    val submit: String,
    val cancel: String,
    val progress: String,
)

/** 서버 볼트가 이전 상태로 돌아갔다는 경고를 받아들일지 묻는 창 (VLT-001) */
internal val ROLLBACK_WARNING_TEXT = WebConfirmText(
    title = RollbackWarning.TITLE,
    body = RollbackWarning.BODY,
    confirm = RollbackWarning.ACCEPT,
    cancel = RollbackWarning.CANCEL,
)

/** 동기화 대기 중 로그아웃 확인 (VLT-002) */
internal val PENDING_LOGOUT_TEXT = WebConfirmText(
    title = PendingLogoutConfirm.TITLE,
    body = PendingLogoutConfirm.BODY,
    confirm = PendingLogoutConfirm.CONFIRM,
    cancel = PendingLogoutConfirm.CANCEL,
)

internal val RECONNECT_PROMPT_TEXT = WebReconnectPrompt(body = ReconnectPrompt.BODY, action = ReconnectPrompt.ACTION)

/** 가입 시 마스터 비밀번호 입력 안내 (SEC-001) */
internal val MASTER_PASSWORD_SETUP_TEXT = WebMessage(title = MasterPasswordSetup.TITLE, body = MasterPasswordSetup.BODY)

internal const val WEAK_MASTER_PASSWORD_TEXT: String = WEAK_MASTER_PASSWORD_WARNING

/** 항목 삭제 확인 (VLT-001) */
internal val DELETE_CONFIRM_TEXT = WebConfirmText(
    title = VaultListMessages.DeleteConfirm.TITLE,
    body = VaultListMessages.DeleteConfirm.BODY,
    confirm = VaultListMessages.DeleteConfirm.CONFIRM,
    cancel = VaultListMessages.DeleteConfirm.CANCEL,
)

/** 약한 비밀번호 경고. 저장은 허용한다 (GEN-001) */
internal val WEAK_ITEM_PASSWORD_TEXT = WebMessage(
    title = VaultListMessages.WEAK_PASSWORD_TITLE,
    body = VaultListMessages.WEAK_PASSWORD_BODY,
)

internal const val SEQUENTIAL_DIGITS_TEXT: String = VaultListMessages.SEQUENTIAL_DIGITS_NOTICE

internal val EXPORT_NOTICE_TEXT = WebConfirmText(
    title = ExportNotice.TITLE,
    body = ExportNotice.BODY,
    confirm = ExportNotice.CONFIRM,
    cancel = ExportNotice.CANCEL,
)

internal val RESTORE_DIALOG_TEXT = WebRestoreDialogText(
    title = RestoreDialog.TITLE,
    body = RestoreDialog.BODY,
    passwordLabel = RestoreDialog.PASSWORD_LABEL,
    submit = RestoreDialog.SUBMIT,
    cancel = RestoreDialog.CANCEL,
    progress = RestoreDialog.PROGRESS,
)

internal val CHANGE_PASSWORD_DIALOG_TEXT = WebChangePasswordDialogText(
    title = ChangePasswordDialog.TITLE,
    body = ChangePasswordDialog.BODY,
    currentLabel = ChangePasswordDialog.CURRENT_LABEL,
    newLabel = ChangePasswordDialog.NEW_LABEL,
    confirmLabel = ChangePasswordDialog.CONFIRM_LABEL,
    newPasswordRule = newPasswordProblemMessage(NewMasterPasswordProblem.TOO_SHORT),
    submit = ChangePasswordDialog.SUBMIT,
    cancel = ChangePasswordDialog.CANCEL,
    progress = ChangePasswordDialog.PROGRESS,
)

internal val DELETE_ACCOUNT_DIALOG_TEXT = WebDeleteAccountDialogText(
    title = DeleteAccountDialog.TITLE,
    body = DeleteAccountDialog.BODY,
    passwordLabel = DeleteAccountDialog.PASSWORD_LABEL,
    phraseLabel = DeleteAccountDialog.PHRASE_LABEL,
    submit = DeleteAccountDialog.SUBMIT,
    cancel = DeleteAccountDialog.CANCEL,
    progress = DeleteAccountDialog.PROGRESS,
)
