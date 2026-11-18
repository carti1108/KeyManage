package com.keymanage.android.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.keymanage.android.designsystem.generated.DesignTokens
import com.keymanage.android.designsystem.generated.KeyManageColors

private val LocalKeyManageColors = staticCompositionLocalOf { DesignTokens.DarkColors }

/**
 * 앱 공통 테마. 색·글자는 tokens.json에서 생성한 [DesignTokens]를 쓴다(ADR-0026).
 * 다이내믹 컬러는 쓰지 않는다(design-system.md 5장). 기본 모드는 다크다(design-system.md 2장).
 * 라이트 화면은 미리보기·테스트에서 [darkTheme]을 false로 넘겨 확인한다.
 */
@Composable
fun KeyManageTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DesignTokens.DarkColors else DesignTokens.LightColors
    CompositionLocalProvider(LocalKeyManageColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(darkTheme),
            typography = KeyManageTypography,
            content = content,
        )
    }
}

/** 시멘틱 토큰 이름 그대로 색에 접근한다. Material 슬롯에 없는 accent·overlay 등은 여기서 쓴다. */
object KeyManageThemeValues {
    val colors: KeyManageColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKeyManageColors.current
}

/**
 * 시멘틱 토큰 → Material3 ColorScheme 매핑. Material 컴포넌트가 토큰 색을 쓰게 한다.
 * 매핑 표는 Apps/Android/docs/platform-bindings.md에 있다.
 */
fun KeyManageColors.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        background = background,
        onBackground = foreground,
        surface = card,
        onSurface = cardForeground,
        surfaceVariant = muted,
        onSurfaceVariant = mutedForeground,
        surfaceContainerHighest = muted,
        primary = primary,
        onPrimary = primaryForeground,
        secondary = accent,
        onSecondary = accentForeground,
        error = destructive,
        onError = destructiveForeground,
        outline = border,
        outlineVariant = input,
        scrim = overlay,
    )
}

/** 역할 토큰 → Material3 Typography 슬롯(ADR-0027). */
val KeyManageTypography = Typography(
    displaySmall = DesignTokens.Typography.display,
    headlineSmall = DesignTokens.Typography.display,
    titleLarge = DesignTokens.Typography.title,
    titleMedium = DesignTokens.Typography.heading,
    bodyLarge = DesignTokens.Typography.body,
    bodyMedium = DesignTokens.Typography.label,
    labelLarge = DesignTokens.Typography.label,
    bodySmall = DesignTokens.Typography.caption,
    labelSmall = DesignTokens.Typography.caption,
)
