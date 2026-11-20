package com.keymanage.android.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.generated.DesignTokens

/**
 * 화면 위쪽 바. M3 `TopAppBar`를 감싼다(design-system.md 5.1: 내비게이션 바·뒤로 가기는 플랫폼 컨트롤).
 * [onBack]이 null이면 뒤로 가기 없이 제목만 보인다. 상태 표시줄 여백은 Root가 이미 준다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KMTopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, modifier = Modifier.semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        navigationIcon = {
            onBack?.let { IconButton(onClick = it) { Icon(KMIcons.Back, contentDescription = "뒤로") } }
        },
        actions = actions,
        windowInsets = WindowInsets(),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Preview
@Composable
private fun KMTopBarPreview() {
    KeyManageTheme { KMTopBar(title = "GitHub", onBack = {}) }
}

/**
 * 위쪽 바와 본문을 묶은 화면 틀. 본문은 좌우 `space-4` 여백과 `space-4` 간격을 쓴다.
 * 본문이 스스로 스크롤하면(`LazyColumn`) [scrollable]을 끈다.
 */
@Composable
fun KMScreen(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        KMTopBar(title = title, onBack = onBack, actions = actions)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .let { if (scrollable) it.verticalScroll(rememberScrollState()) else it }
                .padding(horizontal = DesignTokens.Spacing.space4)
                .padding(bottom = DesignTokens.Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space4),
            content = content,
        )
    }
}
