// 생성 파일입니다. 직접 고치지 말고 Tools/design-tokens/tokens.json을 고친 뒤 node Tools/design-tokens/generate.mjs를 실행하세요 (ADR-0026).
package com.keymanage.android.designsystem.generated

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 시멘틱 색 토큰 한 벌(라이트 또는 다크). 이름은 웹·Apple과 같다. */
@Immutable
data class KeyManageColors(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val cardForeground: Color,
    val primary: Color,
    val primaryForeground: Color,
    val accent: Color,
    val accentForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val destructive: Color,
    val destructiveForeground: Color,
    val border: Color,
    val input: Color,
    val ring: Color,
    val overlay: Color,
)

object DesignTokens {
    val LightColors = KeyManageColors(
        background = Color(0xFFFFFFFF),
        foreground = Color(0xFF0A0A0A),
        card = Color(0xFFFFFFFF),
        cardForeground = Color(0xFF0A0A0A),
        primary = Color(0xFF0A0A0A),
        primaryForeground = Color(0xFFFFFFFF),
        accent = Color(0xFF059669),
        accentForeground = Color(0xFFFFFFFF),
        muted = Color(0xFFF5F5F5),
        mutedForeground = Color(0xFF737373),
        destructive = Color(0xFFDC2626),
        destructiveForeground = Color(0xFFFFFFFF),
        border = Color(0xFFE5E5E5),
        input = Color(0xFFE5E5E5),
        ring = Color(0xFF0A0A0A),
        overlay = Color(0x66000000),
    )

    val DarkColors = KeyManageColors(
        background = Color(0xFF000000),
        foreground = Color(0xFFFAFAFA),
        card = Color(0xFF141414),
        cardForeground = Color(0xFFFAFAFA),
        primary = Color(0xFFFAFAFA),
        primaryForeground = Color(0xFF0A0A0A),
        accent = Color(0xFF10B981),
        accentForeground = Color(0xFF0A0A0A),
        muted = Color(0xFF262626),
        mutedForeground = Color(0xFFA3A3A3),
        destructive = Color(0xFFEF4444),
        destructiveForeground = Color(0xFFFFFFFF),
        border = Color(0x1FFFFFFF),
        input = Color(0xFF262626),
        ring = Color(0xFFFAFAFA),
        overlay = Color(0x66000000),
    )

    object Spacing {
        val space1 = 4.dp
        val space2 = 8.dp
        val space3 = 12.dp
        val space4 = 16.dp
        val space5 = 20.dp
        val space6 = 24.dp
    }

    object Radius {
        val sm = 6.dp
        val md = 8.dp
        val lg = 12.dp
    }

    object Typography {
        val display = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight(700))
        val displayMono = display.copy(fontFamily = FontFamily.Monospace)
        val title = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight(600))
        val titleMono = title.copy(fontFamily = FontFamily.Monospace)
        val heading = TextStyle(fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight(600))
        val headingMono = heading.copy(fontFamily = FontFamily.Monospace)
        val body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight(400))
        val bodyMono = body.copy(fontFamily = FontFamily.Monospace)
        val label = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(400))
        val labelMono = label.copy(fontFamily = FontFamily.Monospace)
        val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight(400))
        val captionMono = caption.copy(fontFamily = FontFamily.Monospace)
    }
}
