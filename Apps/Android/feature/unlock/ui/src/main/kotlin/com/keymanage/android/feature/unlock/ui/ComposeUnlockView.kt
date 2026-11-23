package com.keymanage.android.feature.unlock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.carti.ribs.compose.ComposeRenderable
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.component.KMButton
import com.keymanage.android.designsystem.component.KMButtonVariant
import com.keymanage.android.designsystem.component.KMIcons
import com.keymanage.android.designsystem.component.KMPasswordField
import com.keymanage.android.designsystem.generated.DesignTokens
import com.keymanage.feature.messages.BiometricUnlockMessages
import com.keymanage.feature.messages.ErrorMessage
import com.keymanage.feature.messages.PendingLogoutConfirm
import com.keymanage.feature.messages.RollbackWarning
import com.keymanage.feature.unlock.UnlockPresentableListener
import com.keymanage.feature.unlock.UnlockViewAction
import com.keymanage.feature.unlock.UnlockViewContract
import com.keymanage.feature.unlock.UnlockViewState

/**
 * 잠금 화면의 Compose 뷰. 공유 Unlock RIB이 [render]로 상태를 넘기고, 입력은 [listener]로 돌려보낸다.
 * 상태와 흐름은 공유 Kotlin이 맡고 여기에는 그리는 일만 둔다
 */
class ComposeUnlockView : UnlockViewContract, ComposeRenderable {
    override var listener: UnlockPresentableListener? = null
    private var state by mutableStateOf(UnlockViewState())

    override fun render(state: UnlockViewState) {
        this.state = state
    }

    @Composable
    override fun Content() {
        UnlockScreen(state) { listener?.handle(it) }
    }
}

/** 잠금 화면(목업 3단계) */
@Composable
fun UnlockScreen(state: UnlockViewState, onAction: (UnlockViewAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = DesignTokens.Spacing.space4, vertical = DesignTokens.Spacing.space4),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(KMIcons.Lock, contentDescription = null, modifier = Modifier.size(DesignTokens.Spacing.space6 * 2), tint = MaterialTheme.colorScheme.primary)
        Text("볼트가 잠겼습니다", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        Text(state.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        KMPasswordField(
            text = state.password,
            onTextChange = { onAction(UnlockViewAction.UpdatePassword(it)) },
            label = "마스터 비밀번호",
            placeholder = "",
            showCopyAction = false,
            readOnly = state.isLoading,
        )
        // 생체 해제를 켜 두었는데 마스터 비밀번호가 필요한 이유(세션 만료)
        state.biometricNotice?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.error?.let { ErrorText(it) }
        KMButton(
            title = "잠금 해제",
            onClick = { onAction(UnlockViewAction.Unlock) },
            modifier = Modifier.fillMaxWidth(),
            isLoading = state.isLoading,
            isDisabled = state.password.isEmpty(),
        )
        if (state.isBiometricsAvailable) {
            KMButton(
                title = BiometricUnlockMessages.ACTION,
                onClick = { onAction(UnlockViewAction.UnlockWithBiometrics) },
                modifier = Modifier.fillMaxWidth(),
                variant = KMButtonVariant.OUTLINE,
                isDisabled = state.isLoading,
            )
        }
        TextButton(onClick = { onAction(UnlockViewAction.RequestLogout) }, enabled = !state.isLoading) {
            Text("로그아웃", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (state.isConfirmingRollback) {
        ConfirmDialog(
            title = RollbackWarning.TITLE,
            body = RollbackWarning.BODY,
            confirm = RollbackWarning.ACCEPT,
            cancel = RollbackWarning.CANCEL,
            onConfirm = { onAction(UnlockViewAction.AcceptRollback) },
            onCancel = { onAction(UnlockViewAction.CancelRollback) },
        )
    }
    // 올리지 못한 변경 사항이 있을 때 로그아웃 전에 묻는다 (VLT-002)
    if (state.isConfirmingLogout) {
        ConfirmDialog(
            title = PendingLogoutConfirm.TITLE,
            body = PendingLogoutConfirm.BODY,
            confirm = PendingLogoutConfirm.CONFIRM,
            cancel = PendingLogoutConfirm.CANCEL,
            onConfirm = { onAction(UnlockViewAction.ConfirmLogout) },
            onCancel = { onAction(UnlockViewAction.CancelLogout) },
        )
    }
}

@Composable
private fun ErrorText(error: ErrorMessage) {
    Column(modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        error.title?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error) }
        Text(error.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, cancel: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(cancel) } },
    )
}

@Preview
@Composable
private fun UnlockScreenPreview() {
    KeyManageTheme {
        UnlockScreen(UnlockViewState(email = "dev@example.com", error = ErrorMessage(body = "마스터 비밀번호가 올바르지 않습니다.")), onAction = {})
    }
}
