package com.keymanage.android.feature.auth.impl

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.component.KMButton
import com.keymanage.android.designsystem.component.KMGaugeBar
import com.keymanage.android.designsystem.component.KMPasswordField
import com.keymanage.android.designsystem.component.KMScreen
import com.keymanage.android.designsystem.component.KMTextField
import com.keymanage.android.designsystem.generated.DesignTokens

/**
 * 로그인·가입 화면(목업 1·2단계). 상태를 그리고 동작만 보낸다.
 * 로그인은 가운데에 브랜드와 제목을 두고, 가입은 위쪽 바에 뒤로 가기와 제목을 둔다.
 */
@Composable
fun AuthView(state: AuthViewState, onAction: (AuthViewAction) -> Unit) {
    if (state.mode == AuthMode.SIGNUP) {
        KMScreen(
            title = "계정 만들기",
            onBack = { if (!state.isSubmitting) onAction(AuthViewAction.SwitchMode) },
            modifier = Modifier.imePadding(),
        ) { Form(state, isSignup = true, onAction) }
        return
    }
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
        Form(state, isSignup = false, onAction)
    }
}

@Composable
private fun ColumnScope.Form(state: AuthViewState, isSignup: Boolean, onAction: (AuthViewAction) -> Unit) {
    KMTextField(
        label = "이메일",
        text = state.email,
        onTextChange = { onAction(AuthViewAction.EmailChanged(it)) },
        readOnly = state.isSubmitting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
    KMPasswordField(
        text = state.password,
        onTextChange = { onAction(AuthViewAction.PasswordChanged(it)) },
        label = "마스터 비밀번호",
        placeholder = "",
        showCopyAction = false,
        readOnly = state.isSubmitting,
    )
    if (isSignup) {
        KMPasswordField(
            text = state.confirmation,
            onTextChange = { onAction(AuthViewAction.ConfirmationChanged(it)) },
            label = "마스터 비밀번호 확인",
            placeholder = "",
            showCopyAction = false,
            readOnly = state.isSubmitting,
        )
        state.passwordScore?.let { score ->
            KMGaugeBar(score = score)
            if (state.isWeakPassword) {
                Text(AuthMessages.WEAK_PASSWORD, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Text(SETUP_NOTICE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    state.errorMessage?.let {
        Text(
            it,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    KMButton(
        title = if (isSignup) "가입" else "로그인",
        onClick = { onAction(AuthViewAction.Submit) },
        modifier = Modifier.fillMaxWidth(),
        isLoading = state.isSubmitting,
        isDisabled = !state.canSubmit,
    )
    TextButton(
        onClick = { onAction(AuthViewAction.SwitchMode) },
        enabled = !state.isSubmitting,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
        Text(if (isSignup) "이미 계정이 있어요" else "새 계정 만들기")
    }
}

/** 가입 안내(목업 setupBody). */
private const val SETUP_NOTICE = "볼트를 열기 위한 마스터 비밀번호를 입력해주세요. 마스터 비밀번호를 잃어버리면 복구할 수 없습니다."

@Preview
@Composable
private fun AuthViewLoginPreview() {
    KeyManageTheme { AuthView(AuthViewState(email = "dev@example.com", password = "secret"), onAction = {}) }
}

@Preview
@Composable
private fun AuthViewSignupPreview() {
    KeyManageTheme { AuthView(AuthViewState(mode = AuthMode.SIGNUP, errorMessage = AuthMessages.TOO_SHORT), onAction = {}) }
}
