package com.keymanage.android.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.generated.DesignTokens

/** 배지 문구. 남은 초는 0 아래로 내려가지 않는다. */
object KMBurnBadgeText {
    fun visible(remainingSeconds: Int): String = "클립보드 삭제까지 ${remainingSeconds.coerceAtLeast(0)}초"
    fun accessibilityValue(remainingSeconds: Int): String = "${remainingSeconds.coerceAtLeast(0)}초 남음"
}

/**
 * 클립보드 자동 삭제 카운트다운 배지(design-system.md 6.6, COM-001). 목업대로 `destructive` 12% 배경의 캡슐이다.
 * 실제 카운트다운과 클립보드 비우기는 이 배지를 쓰는 쪽이 맡고, 배지는 [remainingSeconds]를 그린다.
 * 0초가 되면 [onBurned]를 한 번 부른다.
 */
@Composable
fun KMBurnBadge(
    remainingSeconds: Int,
    modifier: Modifier = Modifier,
    onBurned: () -> Unit = {},
) {
    val currentOnBurned = rememberUpdatedState(onBurned)
    val burned = remainingSeconds <= 0
    LaunchedEffect(burned) { if (burned) currentOnBurned.value() }

    Text(
        text = KMBurnBadgeText.visible(remainingSeconds),
        style = DesignTokens.Typography.captionMono,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier
            .clearAndSetSemantics {
                contentDescription = "클립보드 자동 삭제"
                stateDescription = KMBurnBadgeText.accessibilityValue(remainingSeconds)
            }
            .background(MaterialTheme.colorScheme.error.copy(alpha = BACKGROUND_ALPHA), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private const val BACKGROUND_ALPHA = 0.12f

@Preview
@Composable
private fun KMBurnBadgePreview() {
    KeyManageTheme { KMBurnBadge(remainingSeconds = 28) }
}
