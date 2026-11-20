package com.keymanage.android.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.carti.ribs.compose.ComposeRenderable
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.component.KMButton
import com.keymanage.android.designsystem.component.KMGaugeBar
import com.keymanage.android.designsystem.component.KMPasswordField
import com.keymanage.android.designsystem.component.KMScreen
import com.keymanage.android.designsystem.component.KMTextField
import com.keymanage.android.designsystem.generated.DesignTokens
import com.keymanage.feature.auth.AuthMode
import com.keymanage.feature.auth.AuthPresentableListener
import com.keymanage.feature.auth.AuthViewAction
import com.keymanage.feature.auth.AuthViewContract
import com.keymanage.feature.auth.AuthViewState
import com.keymanage.feature.messages.ErrorMessage
import com.keymanage.feature.messages.MasterPasswordSetup
import com.keymanage.feature.messages.RollbackWarning
import com.keymanage.feature.messages.WEAK_MASTER_PASSWORD_WARNING

/**
 * 로그인·가입 화면의 Compose 뷰. 공유 Auth RIB이 [render]로 상태를 넘기고, 입력은 [listener]로 돌려보낸다.
 * 상태와 흐름은 공유 Kotlin이 맡고 여기에는 그리는 일만 둔다 (modularization-plan 2장)
 */
class ComposeAuthView : AuthViewContract, ComposeRenderable {
    override var listener: AuthPresentableListener? = null
    private var state by mutableStateOf(AuthViewState())

    override fun render(state: AuthViewState) {
        this.state = state
    }

    @Composable
    override fun Content() {
        AuthScreen(state) { listener?.handle(it) }
    }
}

/**
 * 로그인·가입 화면(목업 1·2단계).
 * 로그인은 가운데에 브랜드와 제목을 두고, 가입은 위쪽 바에 뒤로 가기와 제목을 둔다.
 */
@Composable
fun AuthScreen(state: AuthViewState, onAction: (AuthViewAction) -> Unit) {
    if (state.isRegistering) {
        KMScreen(
            title = "계정 만들기",
            onBack = { if (!state.isLoading) onAction(AuthViewAction.SwitchMode) },
            modifier = Modifier.imePadding(),
        ) { Form(state, onAction) }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(DesignTokens.Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space4, Alignment.CenterVertically),
        ) {
            Text("KeyManage", style = MaterialTheme.typography.titleMedium)
            Text("볼트에 로그인", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            Form(state, onAction)
        }
    }
    if (state.isConfirmingRollback) {
        AlertDialog(
            onDismissRequest = { onAction(AuthViewAction.CancelRollback) },
            title = { Text(RollbackWarning.TITLE) },
            text = { Text(RollbackWarning.BODY) },
            confirmButton = {
                TextButton(onClick = { onAction(AuthViewAction.AcceptRollback) }) {
                    Text(RollbackWarning.ACCEPT, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { onAction(AuthViewAction.CancelRollback) }) { Text(RollbackWarning.CANCEL) } },
        )
    }
}

@Composable
private fun ColumnScope.Form(state: AuthViewState, onAction: (AuthViewAction) -> Unit) {
    // 로그인 화면으로 돌아온 이유(세션 만료, 다른 기기의 비밀번호 변경 등)
    state.notice?.let {
        Text(
            it,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    KMTextField(
        label = "이메일",
        text = state.email,
        onTextChange = { onAction(AuthViewAction.UpdateEmail(it)) },
        readOnly = state.isLoading,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
    KMPasswordField(
        text = state.password,
        onTextChange = { onAction(AuthViewAction.UpdatePassword(it)) },
        label = "마스터 비밀번호",
        placeholder = "",
        showCopyAction = false,
        readOnly = state.isLoading,
    )
    if (state.isRegistering) {
        KMPasswordField(
            text = state.confirmation,
            onTextChange = { onAction(AuthViewAction.UpdateConfirmation(it)) },
            label = "마스터 비밀번호 확인",
            placeholder = "",
            showCopyAction = false,
            readOnly = state.isLoading,
        )
        if (state.showsStrengthGauge) {
            KMGaugeBar(score = state.passwordStrengthScore)
            if (state.showsWeakPasswordWarning) {
                Text(WEAK_MASTER_PASSWORD_WARNING, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Text(MasterPasswordSetup.BODY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    state.error?.let { ErrorText(it) }
    KMButton(
        title = if (state.isRegistering) "가입" else "로그인",
        onClick = { onAction(AuthViewAction.Submit) },
        modifier = Modifier.fillMaxWidth(),
        isLoading = state.isLoading,
        isDisabled = state.email.isBlank() || state.password.isEmpty(),
    )
    TextButton(
        onClick = { onAction(AuthViewAction.SwitchMode) },
        enabled = !state.isLoading,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
        Text(if (state.isRegistering) "이미 계정이 있어요" else "새 계정 만들기")
    }
}

@Composable
private fun ErrorText(error: ErrorMessage) {
    Column(modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        error.title?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error) }
        Text(error.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

@Preview
@Composable
private fun AuthScreenLoginPreview() {
    KeyManageTheme { AuthScreen(AuthViewState(email = "dev@example.com", password = "secret"), onAction = {}) }
}

@Preview
@Composable
private fun AuthScreenRegisterPreview() {
    KeyManageTheme { AuthScreen(AuthViewState(mode = AuthMode.REGISTER, password = "abc"), onAction = {}) }
}
