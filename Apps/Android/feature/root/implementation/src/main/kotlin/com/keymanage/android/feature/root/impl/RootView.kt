package com.keymanage.android.feature.root.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.generated.DesignTokens

/** Root 화면. 하위 RIB이 [childContent]를 채우고, 남길 안내가 있으면 위에 띠로 보인다. */
@Composable
fun RootView(
    state: RootViewState,
    onAction: (RootViewAction) -> Unit,
    childContent: @Composable () -> Unit,
) {
    LaunchedEffect(Unit) { onAction(RootViewAction.ViewAppeared) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            state.notice?.let { notice ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = DesignTokens.Spacing.space4, vertical = DesignTokens.Spacing.space2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            notice,
                            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = { onAction(RootViewAction.DismissNotice) }) { Text("닫기") }
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                if (state.hasAppeared) childContent()
            }
            if (state.isAskingBiometrics) {
                AlertDialog(
                    onDismissRequest = { onAction(RootViewAction.AnswerBiometricPrompt(enable = false)) },
                    title = { Text(RootMessages.BIOMETRIC_PROMPT_TITLE) },
                    text = { Text(RootMessages.BIOMETRIC_PROMPT_BODY) },
                    confirmButton = { TextButton(onClick = { onAction(RootViewAction.AnswerBiometricPrompt(enable = true)) }) { Text("켜기") } },
                    dismissButton = { TextButton(onClick = { onAction(RootViewAction.AnswerBiometricPrompt(enable = false)) }) { Text("나중에") } },
                )
            }
            if (state.isConfirmingRollback) {
                AlertDialog(
                    onDismissRequest = { onAction(RootViewAction.AnswerRollback(accept = false)) },
                    title = { Text(RootMessages.ROLLBACK_TITLE) },
                    text = { Text(RootMessages.ROLLBACK_BODY) },
                    confirmButton = {
                        TextButton(onClick = { onAction(RootViewAction.AnswerRollback(accept = true)) }) {
                            Text(RootMessages.ROLLBACK_ACCEPT, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = { TextButton(onClick = { onAction(RootViewAction.AnswerRollback(accept = false)) }) { Text("취소") } },
                )
            }
        }
    }
}

@Preview
@Composable
private fun RootViewPreview() {
    KeyManageTheme {
        RootView(state = RootViewState(hasAppeared = true, notice = RootMessages.VAULT_NOT_READY), onAction = {}, childContent = {})
    }
}
