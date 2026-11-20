package com.keymanage.android.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.keymanage.android.designsystem.component.KMPasswordField
import com.keymanage.android.designsystem.component.KMTextField
import com.keymanage.android.designsystem.generated.DesignTokens
import com.keymanage.feature.messages.ChangePasswordDialog
import com.keymanage.feature.messages.DeleteAccountDialog
import com.keymanage.feature.messages.ExportNotice
import com.keymanage.feature.messages.RestoreDialog
import com.keymanage.feature.settings.SettingsModal
import com.keymanage.feature.settings.SettingsViewAction
import com.keymanage.feature.settings.SettingsViewState
import java.text.Normalizer

/** 상태의 [SettingsViewState.modal]에 맞는 대화상자. 문구는 공유 문구를 쓴다 */
@Composable
internal fun SettingsModalDialog(state: SettingsViewState, onAction: (SettingsViewAction) -> Unit) {
    val cancel = { onAction(SettingsViewAction.CancelModal) }
    when (state.modal) {
        SettingsModal.NONE -> Unit
        SettingsModal.EXPORT_CONFIRM -> ModalDialog(
            title = ExportNotice.TITLE,
            body = ExportNotice.BODY,
            confirm = ExportNotice.CONFIRM,
            cancel = ExportNotice.CANCEL,
            state = state,
            canConfirm = state.canExportBackup,
            onConfirm = { onAction(SettingsViewAction.ConfirmExport) },
            onCancel = cancel,
        )
        SettingsModal.RESTORE_PASSWORD -> ModalDialog(
            title = RestoreDialog.TITLE,
            body = RestoreDialog.BODY,
            confirm = RestoreDialog.SUBMIT,
            cancel = RestoreDialog.CANCEL,
            state = state,
            progress = RestoreDialog.PROGRESS,
            canConfirm = state.canSubmitRestore,
            onConfirm = { onAction(SettingsViewAction.SubmitRestore) },
            onCancel = cancel,
        ) {
            Secret(RestoreDialog.PASSWORD_LABEL, state.restorePassword, state.inProgress) { onAction(SettingsViewAction.EditRestorePassword(it)) }
        }
        SettingsModal.CHANGE_PASSWORD -> ModalDialog(
            title = ChangePasswordDialog.TITLE,
            body = ChangePasswordDialog.BODY,
            confirm = ChangePasswordDialog.SUBMIT,
            cancel = ChangePasswordDialog.CANCEL,
            state = state,
            progress = ChangePasswordDialog.PROGRESS,
            canConfirm = state.canSubmitChangePassword,
            onConfirm = { onAction(SettingsViewAction.SubmitChangePassword) },
            onCancel = cancel,
        ) {
            Secret(ChangePasswordDialog.CURRENT_LABEL, state.currentPassword, state.inProgress) { onAction(SettingsViewAction.EditCurrentPassword(it)) }
            Secret(ChangePasswordDialog.NEW_LABEL, state.newPassword, state.inProgress) { onAction(SettingsViewAction.EditNewPassword(it)) }
            Secret(ChangePasswordDialog.CONFIRM_LABEL, state.passwordConfirmation, state.inProgress) {
                onAction(SettingsViewAction.EditPasswordConfirmation(it))
            }
        }
        SettingsModal.DELETE_ACCOUNT -> ModalDialog(
            title = DeleteAccountDialog.TITLE,
            body = DeleteAccountDialog.BODY,
            confirm = DeleteAccountDialog.SUBMIT,
            cancel = DeleteAccountDialog.CANCEL,
            state = state,
            progress = DeleteAccountDialog.PROGRESS,
            canConfirm = state.canSubmitDeletion,
            onConfirm = { onAction(SettingsViewAction.SubmitDeleteAccount) },
            onCancel = cancel,
        ) {
            Secret(DeleteAccountDialog.PASSWORD_LABEL, state.deletionPassword, state.inProgress) { onAction(SettingsViewAction.EditDeletionPassword(it)) }
            KMTextField(
                label = DeleteAccountDialog.PHRASE_LABEL,
                text = state.deletionPhrase,
                // 한글 자모가 나뉘어 들어오면(NFD) 확인 문구와 비교할 수 있게 NFC로 맞춰 보낸다
                onTextChange = { onAction(SettingsViewAction.EditDeletionPhrase(Normalizer.normalize(it, Normalizer.Form.NFC))) },
                readOnly = state.inProgress,
            )
        }
    }
}

@Composable
private fun ModalDialog(
    title: String,
    body: String,
    confirm: String,
    cancel: String,
    state: SettingsViewState,
    canConfirm: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    progress: String? = null,
    fields: @Composable () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = { if (!state.inProgress) onCancel() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space3),
            ) {
                Text(body)
                fields()
                state.error?.let { Message(it, isError = true) }
                if (state.inProgress && progress != null) Message(progress, isError = false)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm) { Text(confirm, color = if (canConfirm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        dismissButton = { TextButton(onClick = onCancel, enabled = !state.inProgress) { Text(cancel) } },
    )
}

@Composable
private fun Secret(label: String, value: String, inProgress: Boolean, onChange: (String) -> Unit) {
    KMPasswordField(text = value, onTextChange = onChange, label = label, placeholder = "", showCopyAction = false, readOnly = inProgress)
}
