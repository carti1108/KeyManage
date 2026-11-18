package com.keymanage.android.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.keymanage.android.designsystem.generated.DesignTokens
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyManageThemeTest {
    @Test
    fun `다크 토큰이 Material ColorScheme 슬롯에 그대로 들어간다`() {
        // Given
        val tokens = DesignTokens.DarkColors

        // When
        val scheme = tokens.toColorScheme(dark = true)

        // Then
        assertEquals(tokens.background, scheme.background)
        assertEquals(tokens.foreground, scheme.onBackground)
        assertEquals(tokens.card, scheme.surface)
        assertEquals(tokens.primary, scheme.primary)
        assertEquals(tokens.accent, scheme.secondary)
        assertEquals(tokens.destructive, scheme.error)
        assertEquals(tokens.border, scheme.outline)
        assertEquals(tokens.overlay, scheme.scrim)
    }

    @Test
    fun `생성 토큰은 tokens json의 대표 값과 같다`() {
        // accent는 라이트 #059669, 다크 #10B981, overlay는 rgba(0,0,0,0.4)
        assertEquals(Color(0xFF059669), DesignTokens.LightColors.accent)
        assertEquals(Color(0xFF10B981), DesignTokens.DarkColors.accent)
        assertEquals(Color(0x66000000), DesignTokens.DarkColors.overlay)
    }

    @Test
    fun `역할 글자 크기는 웹 rem의 16배 sp다`() {
        assertEquals(24.sp, DesignTokens.Typography.display.fontSize)
        assertEquals(16.sp, DesignTokens.Typography.body.fontSize)
        assertEquals(KeyManageTypography.bodyLarge, DesignTokens.Typography.body)
    }
}
