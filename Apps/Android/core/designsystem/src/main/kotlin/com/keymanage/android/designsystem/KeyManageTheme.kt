package com.keymanage.android.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * 앱 공통 테마. 색 값은 4단계에서 tokens.json 생성 코드로 바꾼다(ADR-0026).
 * 그때까지는 Material3 기본 색을 쓴다.
 */
@Composable
fun KeyManageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
