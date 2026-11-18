package com.keymanage.android.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.generated.DesignTokens

/** Android 버튼 변형. `secondary`(회색 채움)는 Apple 전용이라 없다(design-system.md 6.2). */
enum class KMButtonVariant { PRIMARY, OUTLINE, DESTRUCTIVE, GHOST }

/** 최소 높이(design-system.md 6.2). 터치 영역은 M3 기본 48dp 최소 터치 크기가 맞춘다. */
enum class KMButtonSize(val minHeight: Int) { SM(32), MD(40), LG(48) }

/**
 * 주요 액션 버튼(design-system.md 6.2). 모양은 Android 목업대로 높이 40, 캡슐(반지름 20), 좌우 24다.
 * 로딩 중에는 다시 누를 수 없다. 부모 너비를 채우려면 [modifier]에 `fillMaxWidth()`를 넘긴다.
 */
@Composable
fun KMButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: KMButtonVariant = KMButtonVariant.PRIMARY,
    size: KMButtonSize = KMButtonSize.MD,
    isLoading: Boolean = false,
    isDisabled: Boolean = false,
    accessibilityLabel: String? = null,
) {
    val enabled = !isDisabled && !isLoading
    val sized = modifier
        .heightIn(min = size.minHeight.dp)
        .semantics { accessibilityLabel?.let { contentDescription = it } }
    val padding = PaddingValues(horizontal = HORIZONTAL_PADDING.dp)
    val colors = MaterialTheme.colorScheme
    val content: @Composable () -> Unit = {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(LOADING_SIZE.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
        } else {
            Text(text = title, style = DesignTokens.Typography.label)
        }
    }
    when (variant) {
        KMButtonVariant.PRIMARY -> Button(
            onClick = onClick, modifier = sized, enabled = enabled, contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
        ) { content() }
        KMButtonVariant.DESTRUCTIVE -> Button(
            onClick = onClick, modifier = sized, enabled = enabled, contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError),
        ) { content() }
        KMButtonVariant.OUTLINE -> OutlinedButton(
            onClick = onClick, modifier = sized, enabled = enabled, contentPadding = padding,
            border = BorderStroke(1.dp, colors.onSurfaceVariant),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onBackground),
        ) { content() }
        KMButtonVariant.GHOST -> TextButton(
            onClick = onClick, modifier = sized, enabled = enabled, contentPadding = padding,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurfaceVariant),
        ) { content() }
    }
}

private const val HORIZONTAL_PADDING = 24
private const val LOADING_SIZE = 18

@Preview
@Composable
private fun KMButtonPreview() {
    KeyManageTheme {
        androidx.compose.foundation.layout.Column {
            KMButton(title = "로그인", onClick = {})
            KMButton(title = "아이디 복사", onClick = {}, variant = KMButtonVariant.OUTLINE)
            KMButton(title = "항목 삭제", onClick = {}, variant = KMButtonVariant.DESTRUCTIVE)
            KMButton(title = "저장", onClick = {}, isLoading = true)
        }
    }
}
