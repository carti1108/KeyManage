package com.keymanage.android.feature.unlock.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.keymanage.android.designsystem.component.KMButtonSize
import com.keymanage.android.designsystem.component.KMButtonVariant
import com.keymanage.android.designsystem.component.KMPasswordField
import com.keymanage.android.designsystem.generated.DesignTokens

/** 잠금 화면(목업 3단계). */
@Composable
fun UnlockView(state: UnlockViewState, onAction: (UnlockViewAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = DesignTokens.Spacing.space5, vertical = DesignTokens.Spacing.space6),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("볼트가 잠겼습니다", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        Text(state.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        KMPasswordField(
            text = state.password,
            onTextChange = { onAction(UnlockViewAction.PasswordChanged(it)) },
            label = "마스터 비밀번호",
            placeholder = "",
            showCopyAction = false,
            readOnly = state.isSubmitting,
        )
        state.errorMessage?.let {
            Text(
                it,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        KMButton(
            title = "잠금 해제",
            onClick = { onAction(UnlockViewAction.Submit) },
            modifier = Modifier.fillMaxWidth(),
            size = KMButtonSize.LG,
            isLoading = state.isSubmitting,
            isDisabled = !state.canSubmit,
        )
        if (state.canUseBiometrics) {
            KMButton(
                title = UnlockMessages.BIOMETRIC_BUTTON,
                onClick = { onAction(UnlockViewAction.UnlockWithBiometrics) },
                modifier = Modifier.fillMaxWidth(),
                variant = KMButtonVariant.OUTLINE,
                size = KMButtonSize.LG,
                isDisabled = state.isSubmitting,
            )
        }
        TextButton(onClick = { onAction(UnlockViewAction.Logout) }, enabled = !state.isSubmitting) {
            Text("로그아웃", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview
@Composable
private fun UnlockViewPreview() {
    KeyManageTheme { UnlockView(UnlockViewState(email = "dev@example.com", errorMessage = UnlockMessages.WRONG_PASSWORD), onAction = {}) }
}
