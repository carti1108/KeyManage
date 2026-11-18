package com.keymanage.android.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.generated.DesignTokens

/** 카드 안쪽 여백(design-system.md 6.3): space-3·4·5. */
enum class KMCardPadding(val value: Dp) {
    SM(DesignTokens.Spacing.space3),
    MD(DesignTokens.Spacing.space4),
    LG(DesignTokens.Spacing.space5),
}

/**
 * 항목·섹션 카드(design-system.md 6.3). 목업대로 `card` 배경, `border` 테두리, `radius-lg` 모서리다.
 * [onClick]을 넘기면 탭할 수 있는 목록 항목이 되고 M3 press 피드백을 쓴다.
 */
@Composable
fun KMCard(
    modifier: Modifier = Modifier,
    padding: KMCardPadding = KMCardPadding.MD,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(DesignTokens.Radius.lg)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    val colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    val inner: @Composable ColumnScope.() -> Unit = {
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(padding.value), content = content)
    }
    if (onClick != null) {
        OutlinedCard(onClick = onClick, modifier = modifier, shape = shape, border = border, colors = colors, content = inner)
    } else {
        Card(modifier = modifier, shape = shape, border = border, colors = colors, content = inner)
    }
}

@Preview
@Composable
private fun KMCardPreview() {
    KeyManageTheme {
        KMCard(onClick = {}) {
            Text("GitHub", style = DesignTokens.Typography.body)
            Text("dev@example.com", style = DesignTokens.Typography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
