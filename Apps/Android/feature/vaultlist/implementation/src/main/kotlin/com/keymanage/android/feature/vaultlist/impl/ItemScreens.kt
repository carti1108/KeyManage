package com.keymanage.android.feature.vaultlist.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.keymanage.android.designsystem.component.KMButton
import com.keymanage.android.designsystem.component.KMButtonVariant
import com.keymanage.android.designsystem.component.KMCard
import com.keymanage.android.designsystem.component.KMPasswordField
import com.keymanage.android.designsystem.generated.DesignTokens

/** 항목 상세(목업 5단계). 비밀번호는 가린 채 보이고 복사하면 30초 뒤 비운다. */
@Composable
internal fun ItemDetailScreen(screen: VaultScreen.Detail, state: VaultListViewState, onAction: (VaultListViewAction) -> Unit) {
    val fields = screen.fields
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space3),
    ) {
        TopBar(fields.serviceName, onBack = { onAction(VaultListViewAction.Back) }) {
            if (!state.isReadOnly) TextButton(onClick = { onAction(VaultListViewAction.StartEdit) }) { Text("수정") }
        }
        KMCard {
            Field("서비스 이름", fields.serviceName)
            Field("아이디", fields.username.ifEmpty { "(아이디 없음)" })
            if (fields.url.isNotEmpty()) Field("URL", fields.url)
        }
        KMPasswordField(
            text = fields.password,
            onTextChange = {},
            label = "비밀번호",
            readOnly = true,
            onCopy = { onAction(VaultListViewAction.CopyPassword) },
        )
        if (fields.notes.isNotEmpty()) KMCard { Field("메모", fields.notes) }
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space2)) {
            KMButton(
                title = "아이디 복사",
                onClick = { onAction(VaultListViewAction.CopyUsername) },
                modifier = Modifier.weight(1f),
                variant = KMButtonVariant.OUTLINE,
                isDisabled = fields.username.isEmpty(),
            )
            if (!state.isReadOnly) {
                KMButton(
                    title = "항목 삭제",
                    onClick = { onAction(VaultListViewAction.RequestDelete) },
                    modifier = Modifier.weight(1f),
                    variant = KMButtonVariant.DESTRUCTIVE,
                    isLoading = state.isSaving,
                )
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** 항목 추가·수정 폼. */
@Composable
internal fun ItemFormScreen(screen: VaultScreen.Edit, state: VaultListViewState, onAction: (VaultListViewAction) -> Unit) {
    val fields = screen.fields
    fun change(next: com.keymanage.android.domain.vault.ItemFields) = onAction(VaultListViewAction.FormChanged(next))
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).imePadding(),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space3),
    ) {
        TopBar(if (screen.id == null) "항목 추가" else "항목 수정", onBack = { onAction(VaultListViewAction.Back) })
        TextInput("서비스 이름", fields.serviceName, !state.isSaving) { change(fields.copy(serviceName = it)) }
        TextInput("아이디", fields.username, !state.isSaving) { change(fields.copy(username = it)) }
        KMPasswordField(
            text = fields.password,
            onTextChange = { change(fields.copy(password = it)) },
            label = "비밀번호",
            placeholder = "",
            showCopyAction = false,
            readOnly = state.isSaving,
        )
        TextInput("URL", fields.url, !state.isSaving) { change(fields.copy(url = it)) }
        TextInput("메모", fields.notes, !state.isSaving, singleLine = false) { change(fields.copy(notes = it)) }
        KMButton(
            title = "저장",
            onClick = { onAction(VaultListViewAction.Save) },
            modifier = Modifier.fillMaxWidth(),
            isLoading = state.isSaving,
        )
    }
}

@Composable
private fun TextInput(label: String, value: String, enabled: Boolean, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        enabled = enabled,
    )
}
