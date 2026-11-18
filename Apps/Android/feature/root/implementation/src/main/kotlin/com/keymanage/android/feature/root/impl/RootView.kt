package com.keymanage.android.feature.root.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.generated.DesignTokens

/** Root 화면. 상태를 그리고 동작만 보낸다. 하위 RIB이 붙으면 [childContent]가 화면을 채운다. */
@Composable
fun RootView(
    state: RootViewState,
    onAction: (RootViewAction) -> Unit,
    childContent: @Composable () -> Unit,
) {
    LaunchedEffect(Unit) { onAction(RootViewAction.ViewAppeared) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val email = state.signedInEmail
        if (email == null) {
            if (state.hasAppeared) childContent()
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(DesignTokens.Spacing.space5), contentAlignment = Alignment.Center) {
                Text(text = "$email 계정으로 로그인했습니다.", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Preview
@Composable
private fun RootViewPreview() {
    KeyManageTheme { RootView(state = RootViewState(hasAppeared = true, signedInEmail = "dev@example.com"), onAction = {}, childContent = {}) }
}
