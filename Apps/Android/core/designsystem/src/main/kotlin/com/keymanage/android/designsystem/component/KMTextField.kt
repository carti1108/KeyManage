package com.keymanage.android.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.keymanage.android.designsystem.KeyManageTheme

/**
 * 이름표가 붙은 입력 필드(design-system.md 6.10). M3 Filled `TextField`를 감싼다.
 * 이름표는 M3 방식대로 칸 안에 있다가 입력하면 위로 올라가고, 보조 기술에는 필드의 이름으로 읽힌다.
 */
@Composable
fun KMTextField(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    TextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier.fillMaxWidth(),
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        keyboardOptions = keyboardOptions,
    )
}

@Preview
@Composable
private fun KMTextFieldPreview() {
    KeyManageTheme { KMTextField(label = "이메일", text = "dev@example.com", onTextChange = {}) }
}
