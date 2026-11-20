package com.keymanage.android.feature.vaultlist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.keymanage.android.designsystem.component.KMButton
import com.keymanage.android.designsystem.component.KMButtonVariant
import com.keymanage.android.designsystem.component.KMCard
import com.keymanage.android.designsystem.component.KMGaugeBar
import com.keymanage.android.designsystem.component.KMPasswordField
import com.keymanage.android.designsystem.component.KMScreen
import com.keymanage.android.designsystem.component.KMTextField
import com.keymanage.android.designsystem.generated.DesignTokens
import com.keymanage.domain.generator.hasSequentialDigits
import com.keymanage.domain.generator.isWeak
import com.keymanage.domain.generator.scorePassword
import com.keymanage.domain.vault.VaultItem
import com.keymanage.feature.messages.ErrorMessage
import com.keymanage.feature.vaultlist.VaultItemFormField
import com.keymanage.feature.vaultlist.VaultListMessages
import com.keymanage.feature.vaultlist.VaultListScreen
import com.keymanage.feature.vaultlist.VaultListViewAction
import com.keymanage.feature.vaultlist.VaultListViewState

private const val MEMO_MIN_LINES = 3

/** 항목 상세(목업 5단계). 비밀번호는 가린 채 보이고, 복사한 값은 공유 RIB이 30초 뒤 비운다 */
@Composable
internal fun ItemDetailScreen(item: VaultItem, state: VaultListViewState, onAction: (VaultListViewAction) -> Unit) {
    val username = item.username.orEmpty()
    val password = item.password.orEmpty()
    KMScreen(
        title = item.serviceName.orEmpty(),
        onBack = { if (!state.isSaving) onAction(VaultListViewAction.Back) },
        actions = { if (state.canEdit) TextButton(onClick = { onAction(VaultListViewAction.StartEdit) }, enabled = !state.isSaving) { Text("수정") } },
    ) {
        Column {
            DetailRow("서비스 이름", item.serviceName.orEmpty())
            DetailRow("아이디", username.ifEmpty { "(아이디 없음)" })
            item.url?.takeIf { it.isNotEmpty() }?.let { DetailRow("URL", it) }
        }
        KMPasswordField(
            text = password,
            onTextChange = {},
            label = "비밀번호",
            readOnly = true,
            onCopy = { onAction(VaultListViewAction.CopyPassword(item.id)) },
        )
        if (password.isNotEmpty()) {
            val score = scorePassword(password)
            StrengthNotes(score, isWeak(score), hasSequentialDigits(password))
        }
        item.notes?.takeIf { it.isNotEmpty() }?.let { notes ->
            KMCard {
                Text("메모", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(notes, style = MaterialTheme.typography.bodyLarge)
            }
        }
        state.copyError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        state.saveError?.let { ErrorText(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space2)) {
            KMButton(
                title = "아이디 복사",
                onClick = { onAction(VaultListViewAction.CopyUsername(item.id)) },
                modifier = Modifier.weight(1f),
                variant = KMButtonVariant.OUTLINE,
                isDisabled = username.isEmpty(),
            )
            if (state.canEdit) {
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

/** 항목 추가·수정 폼 */
@Composable
internal fun ItemFormScreen(state: VaultListViewState, onAction: (VaultListViewAction) -> Unit) {
    val form = state.form
    fun change(field: VaultItemFormField): (String) -> Unit = { onAction(VaultListViewAction.EditForm(field, it)) }
    KMScreen(
        title = if (state.screen == VaultListScreen.ADD) "항목 추가" else "항목 수정",
        onBack = { if (!state.isSaving) onAction(VaultListViewAction.CancelForm) },
        modifier = Modifier.imePadding(),
    ) {
        TextInput("서비스 이름", form.serviceName, state.isSaving, onChange = change(VaultItemFormField.SERVICE_NAME))
        TextInput("아이디", form.username, state.isSaving, onChange = change(VaultItemFormField.USERNAME))
        KMPasswordField(
            text = form.password,
            onTextChange = change(VaultItemFormField.PASSWORD),
            label = "비밀번호",
            placeholder = "",
            showCopyAction = false,
            readOnly = state.isSaving,
        )
        if (state.showsFormStrength) {
            StrengthNotes(state.formPasswordScore, state.showsFormWeakWarning, state.showsFormSequentialDigitsNotice)
        }
        KMButton(
            title = "비밀번호 생성",
            onClick = { onAction(VaultListViewAction.OpenGenerator) },
            variant = KMButtonVariant.OUTLINE,
            isDisabled = state.isSaving,
        )
        TextInput("URL", form.url, state.isSaving, onChange = change(VaultItemFormField.URL))
        TextInput("메모", form.notes, state.isSaving, singleLine = false, onChange = change(VaultItemFormField.NOTES))
        state.saveError?.let { ErrorText(it) }
        KMButton(
            title = "저장",
            onClick = { onAction(VaultListViewAction.SubmitForm) },
            modifier = Modifier.fillMaxWidth(),
            isLoading = state.isSaving,
        )
    }
}

/** 강도 게이지와 GEN-001 안내. 취약해도 경고만 하고 저장은 막지 않는다 */
@Composable
private fun StrengthNotes(score: Int, isWeak: Boolean, hasSequentialDigits: Boolean) {
    KMGaugeBar(score = score)
    if (isWeak) {
        Text(VaultListMessages.WEAK_PASSWORD_BODY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    if (hasSequentialDigits) {
        Text(VaultListMessages.SEQUENTIAL_DIGITS_NOTICE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 이름표는 왼쪽, 값은 오른쪽에 두는 한 줄(목업 5단계) */
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

@Composable
private fun TextInput(label: String, value: String, isSaving: Boolean, singleLine: Boolean = true, onChange: (String) -> Unit) {
    KMTextField(
        label = label,
        text = value,
        onTextChange = onChange,
        readOnly = isSaving,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else MEMO_MIN_LINES,
    )
}

@Composable
private fun ErrorText(error: ErrorMessage) {
    Column(modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        error.title?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error) }
        Text(error.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}
