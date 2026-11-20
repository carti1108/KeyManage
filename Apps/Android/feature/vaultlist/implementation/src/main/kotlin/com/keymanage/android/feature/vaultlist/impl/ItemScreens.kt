package com.keymanage.android.feature.vaultlist.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.keymanage.android.designsystem.component.KMScreen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.keymanage.android.designsystem.component.KMButton
import com.keymanage.android.designsystem.component.KMButtonVariant
import com.keymanage.android.designsystem.component.KMCard
import com.keymanage.android.designsystem.component.KMGaugeBar
import com.keymanage.android.designsystem.component.KMPasswordField
import com.keymanage.android.designsystem.component.KMTextField
import com.keymanage.android.designsystem.generated.DesignTokens
import com.keymanage.android.domain.generator.PasswordStrength

/** 항목 상세(목업 5단계). 비밀번호는 가린 채 보이고 복사하면 30초 뒤 비운다. */
@Composable
internal fun ItemDetailScreen(screen: VaultScreen.Detail, state: VaultListViewState, onAction: (VaultListViewAction) -> Unit) {
    val fields = screen.fields
    KMScreen(
        title = fields.serviceName,
        onBack = { onAction(VaultListViewAction.Back) },
        actions = { if (!state.isReadOnly) TextButton(onClick = { onAction(VaultListViewAction.StartEdit) }) { Text("수정") } },
    ) {
        Column {
            DetailRow("서비스 이름", fields.serviceName)
            DetailRow("아이디", fields.username.ifEmpty { "(아이디 없음)" })
            if (fields.url.isNotEmpty()) DetailRow("URL", fields.url)
        }
        KMPasswordField(
            text = fields.password,
            onTextChange = {},
            label = "비밀번호",
            readOnly = true,
            onCopy = { onAction(VaultListViewAction.CopyPassword) },
        )
        StrengthNotes(fields.password)
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

/** 강도 게이지와 GEN-001 안내. 취약해도 경고만 하고 저장은 막지 않는다. */
@Composable
private fun StrengthNotes(password: String) {
    val score = PasswordStrength.score(password)
    KMGaugeBar(score = score)
    if (PasswordStrength.isWeak(score)) {
        Text(VaultMessages.WEAK_PASSWORD, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    if (PasswordStrength.hasSequentialDigits(password)) {
        Text(VaultMessages.SEQUENTIAL_DIGITS, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 이름표는 왼쪽, 값은 오른쪽에 두는 한 줄(목업 5단계). */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = DesignTokens.Spacing.space3),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

private const val MEMO_MIN_LINES = 3

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
    KMScreen(
        title = if (screen.id == null) "항목 추가" else "항목 수정",
        onBack = { onAction(VaultListViewAction.Back) },
        modifier = Modifier.imePadding(),
    ) {
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
        if (fields.password.isNotEmpty()) StrengthNotes(fields.password)
        KMButton(
            title = "비밀번호 생성",
            onClick = { onAction(VaultListViewAction.GeneratePassword) },
            variant = KMButtonVariant.OUTLINE,
            isDisabled = state.isSaving,
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
    KMTextField(
        label = label,
        text = value,
        onTextChange = onChange,
        readOnly = !enabled,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else MEMO_MIN_LINES,
    )
}
