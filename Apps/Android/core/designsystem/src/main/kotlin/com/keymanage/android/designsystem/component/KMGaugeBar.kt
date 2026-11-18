package com.keymanage.android.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.KeyManageThemeValues
import com.keymanage.android.designsystem.generated.DesignTokens

/**
 * 비밀번호 강도 게이지(design-system.md 6.5). 0~59 취약함(destructive), 60~79 보통(primary), 80~100 초강력(accent).
 * [showLabel]을 꺼도 접근성 설명으로 강도 문구를 준다. 애니메이션은 시스템 애니메이션 배율을 따른다.
 */
@Composable
fun KMGaugeBar(
    score: Int,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    animated: Boolean = true,
) {
    val clamped = EntropyLevel.clamp(score)
    val level = EntropyLevel.of(score)
    val color = levelColor(level)
    val target = clamped / EntropyLevel.MAX_SCORE.toFloat()
    val fraction by animateFloatAsState(targetValue = target, label = "gauge")
    val shown = if (animated) fraction else target

    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "비밀번호 강도: ${level.label}"
            stateDescription = "${clamped}점"
        },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showLabel) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("비밀번호 강도", style = DesignTokens.Typography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(level.label, style = DesignTokens.Typography.caption, color = color)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TRACK_HEIGHT.dp)
                .clip(RoundedCornerShape(TRACK_HEIGHT.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(shown).background(color))
        }
    }
}

@Composable
private fun levelColor(level: EntropyLevel): Color = when (level) {
    EntropyLevel.WEAK -> MaterialTheme.colorScheme.error
    EntropyLevel.MEDIUM -> MaterialTheme.colorScheme.primary
    EntropyLevel.STRONG -> KeyManageThemeValues.colors.accent
}

private const val TRACK_HEIGHT = 6

@Preview
@Composable
private fun KMGaugeBarPreview() {
    KeyManageTheme {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KMGaugeBar(score = 35)
            KMGaugeBar(score = 65)
            KMGaugeBar(score = 95)
        }
    }
}
