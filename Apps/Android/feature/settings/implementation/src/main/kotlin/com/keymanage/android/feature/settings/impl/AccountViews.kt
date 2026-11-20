package com.keymanage.android.feature.settings.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.keymanage.android.designsystem.component.KMTextField
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import com.keymanage.android.designsystem.component.KMScreen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.component.KMButton
import com.keymanage.android.designsystem.component.KMButtonVariant
import com.keymanage.android.designsystem.component.KMGaugeBar
import com.keymanage.android.designsystem.component.KMPasswordField
import com.keymanage.android.designsystem.generated.DesignTokens

/** 마스터 비밀번호 변경 화면(목업 9단계, SEC-004). */
@Composable
internal fun PasswordChangeView(state: PasswordChangeState, onAction: (SettingsViewAction) -> Unit) {
    fun change(current: String = state.current, new: String = state.new, confirmation: String = state.confirmation) =
        onAction(SettingsViewAction.PasswordFieldsChanged(current, new, confirmation))
    SubScreen(SettingsMessages.PASSWORD_TITLE, enabled = !state.isWorking, onBack = { onAction(SettingsViewAction.Back) }) {
        Text(SettingsMessages.PASSWORD_BODY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Secret("현재 마스터 비밀번호", state.current, state.isWorking) { change(current = it) }
        Secret("새 마스터 비밀번호", state.new, state.isWorking) { change(new = it) }
        Secret("새 마스터 비밀번호 확인", state.confirmation, state.isWorking) { change(confirmation = it) }
        state.newScore?.let { KMGaugeBar(score = it) }
        KMButton(
            title = "변경",
            onClick = { onAction(SettingsViewAction.SubmitPasswordChange) },
            modifier = Modifier.fillMaxWidth(),
            isLoading = state.isWorking,
            isDisabled = !state.canSubmit,
        )
        state.message?.let { Message(it, if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
    }
}

/** 계정 삭제 대화상자(목업 10단계, SEC-005). 설정 화면 위에 M3 대화상자로 띄우고, 확인 문구가 정확히 같아야 삭제 버튼이 켜진다. */
@Composable
internal fun DeleteAccountView(state: DeleteAccountState, onAction: (SettingsViewAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!state.isWorking) onAction(SettingsViewAction.Back) },
        title = { Text(SettingsMessages.DELETE_TITLE, color = MaterialTheme.colorScheme.error) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space4),
            ) {
                Text(SettingsMessages.DELETE_BODY)
                Secret("마스터 비밀번호", state.password, state.isWorking) { onAction(SettingsViewAction.DeleteFieldsChanged(it, state.phrase)) }
                KMTextField(
                    label = SettingsMessages.DELETE_PHRASE_LABEL,
                    text = state.phrase,
                    onTextChange = { onAction(SettingsViewAction.DeleteFieldsChanged(state.password, it)) },
                    readOnly = state.isWorking,
                )
                state.message?.let { Message(it, MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            KMButton(
                title = "삭제",
                onClick = { onAction(SettingsViewAction.SubmitDeleteAccount) },
                variant = KMButtonVariant.DESTRUCTIVE,
                isLoading = state.isWorking,
                isDisabled = !state.canDelete,
            )
        },
        dismissButton = {
            KMButton(
                title = "취소",
                onClick = { onAction(SettingsViewAction.Back) },
                variant = KMButtonVariant.OUTLINE,
                isDisabled = state.isWorking,
            )
        },
    )
}

@Composable
private fun SubScreen(title: String, enabled: Boolean, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
    KMScreen(title = title, onBack = { if (enabled) onBack() }, modifier = Modifier.imePadding()) { content() }
}

@Composable
private fun Secret(label: String, value: String, isWorking: Boolean, onChange: (String) -> Unit) {
    KMPasswordField(text = value, onTextChange = onChange, label = label, placeholder = "", showCopyAction = false, readOnly = isWorking)
}

@Composable
private fun Message(text: String, color: Color) {
    Text(text, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Preview
@Composable
private fun PasswordChangePreview() {
    KeyManageTheme { PasswordChangeView(PasswordChangeState(new = "new-password!", message = SettingsMessages.SAME_PASSWORD, isError = true), onAction = {}) }
}

@Preview
@Composable
private fun DeleteAccountPreview() {
    KeyManageTheme { DeleteAccountView(DeleteAccountState(phrase = "계정 삭제"), onAction = {}) }
}
