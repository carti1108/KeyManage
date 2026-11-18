package com.keymanage.android.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.generated.DesignTokens
import kotlinx.coroutines.delay

/** 평문으로 바꾼 뒤 조작이 없으면 다시 가리는 시간(design-system.md 6.4). */
const val KM_PASSWORD_AUTO_MASK_MILLIS = 10_000L

/**
 * 비밀번호 입력 필드(design-system.md 6.4). M3 Filled `TextField`를 감싸고 값은 고정폭 글꼴로 보인다.
 * 평문 표시 뒤 10초 동안 입력이 없으면 다시 가린다. 입력하면 10초를 다시 센다.
 * 클립보드 쓰기와 30초 뒤 비우기(COM-001)는 [onCopy]를 받은 쪽이 맡는다.
 */
@Composable
fun KMPasswordField(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "비밀번호 입력",
    showCopyAction: Boolean = true,
    onCopy: () -> Unit = {},
    readOnly: Boolean = false,
) {
    var isSecured by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(isSecured, text) {
        if (!isSecured) {
            delay(KM_PASSWORD_AUTO_MASK_MILLIS)
            isSecured = true
        }
    }

    TextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier.fillMaxWidth(),
        readOnly = readOnly,
        singleLine = true,
        textStyle = DesignTokens.Typography.bodyMono,
        label = label?.let { { Text(it) } },
        placeholder = { Text(placeholder) },
        visualTransformation = if (isSecured) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            Row {
                TextButton(
                    onClick = { isSecured = !isSecured },
                    modifier = Modifier.semantics { contentDescription = if (isSecured) "비밀번호 표시" else "비밀번호 숨김" },
                ) { Text(if (isSecured) "보기" else "숨기기") }
                if (showCopyAction) {
                    TextButton(
                        onClick = onCopy,
                        modifier = Modifier.semantics { contentDescription = "비밀번호 복사" },
                    ) { Text("복사") }
                }
            }
        },
    )
}

@Preview
@Composable
private fun KMPasswordFieldPreview() {
    KeyManageTheme { KMPasswordField(text = "k7#Qv2!mR9", onTextChange = {}, label = "비밀번호") }
}
