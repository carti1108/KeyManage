package com.keymanage.android.feature.settings.impl

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.keymanage.android.designsystem.component.KMScreen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.component.KMButton
import com.keymanage.android.designsystem.component.KMButtonVariant
import com.keymanage.android.designsystem.component.KMPasswordField
import com.keymanage.android.designsystem.generated.DesignTokens

/** 백업 화면(목업 8단계, VLT-004). 파일 위치는 저장소 접근 프레임워크로 고르고, 읽기·쓰기는 Interactor가 한다. */
@Composable
internal fun BackupView(state: BackupState, onAction: (SettingsViewAction) -> Unit) {
    BackHandler { onAction(SettingsViewAction.Back) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON)) { uri ->
        uri?.let { onAction(SettingsViewAction.ExportTo(it.toString())) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onAction(SettingsViewAction.RestoreFilePicked(it.toString())) }
    }
    KMScreen(title = "백업", onBack = { onAction(SettingsViewAction.Back) }, modifier = Modifier.imePadding()) {
        Text(SettingsMessages.EXPORT_TITLE, style = MaterialTheme.typography.titleMedium)
        Text(SettingsMessages.EXPORT_BODY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(state.fileName, style = DesignTokens.Typography.captionMono, color = MaterialTheme.colorScheme.onSurfaceVariant)
        KMButton(
            title = "내보내기",
            onClick = { exportLauncher.launch(state.fileName) },
            modifier = Modifier.fillMaxWidth(),
            isDisabled = state.isWorking,
        )
        Text(SettingsMessages.RESTORE_TITLE, style = MaterialTheme.typography.titleMedium)
        Text(SettingsMessages.RESTORE_BODY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        KMButton(
            title = if (state.restoreUri == null) "파일 선택" else "다른 파일 선택",
            onClick = { restoreLauncher.launch(arrayOf(JSON, "*/*")) },
            modifier = Modifier.fillMaxWidth(),
            variant = KMButtonVariant.OUTLINE,
            isDisabled = state.isWorking,
        )
        if (state.restoreUri != null) {
            KMPasswordField(
                text = state.restorePassword,
                onTextChange = { onAction(SettingsViewAction.RestorePasswordChanged(it)) },
                label = "백업 파일의 마스터 비밀번호",
                placeholder = "",
                showCopyAction = false,
                readOnly = state.isWorking,
            )
            KMButton(
                title = "복원",
                onClick = { onAction(SettingsViewAction.RequestRestore) },
                modifier = Modifier.fillMaxWidth(),
                isLoading = state.isWorking,
                isDisabled = !state.canRestore,
            )
        }
        state.message?.let {
            Text(
                it,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    if (state.isConfirmingRestore) {
        AlertDialog(
            onDismissRequest = { onAction(SettingsViewAction.DismissDialog) },
            title = { Text(SettingsMessages.RESTORE_TITLE) },
            text = { Text(SettingsMessages.RESTORE_BODY) },
            confirmButton = { TextButton(onClick = { onAction(SettingsViewAction.ConfirmRestore) }) { Text("복원") } },
            dismissButton = { TextButton(onClick = { onAction(SettingsViewAction.DismissDialog) }) { Text("취소") } },
        )
    }
}

private const val JSON = "application/json"

@Preview
@Composable
private fun BackupViewPreview() {
    KeyManageTheme {
        BackupView(BackupState(fileName = "keymanage-backup-20261118.json", restoreUri = "content://x", message = SettingsMessages.restored(3)), onAction = {})
    }
}
