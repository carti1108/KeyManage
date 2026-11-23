package com.keymanage.android.feature.generator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import com.carti.ribs.compose.ComposeRenderable
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.component.KMButton
import com.keymanage.android.designsystem.component.KMButtonVariant
import com.keymanage.android.designsystem.component.KMCard
import com.keymanage.android.designsystem.component.KMGaugeBar
import com.keymanage.android.designsystem.component.KMScreen
import com.keymanage.android.designsystem.generated.DesignTokens
import com.keymanage.domain.generator.DEFAULT_GENERATOR_OPTIONS
import com.keymanage.domain.generator.MAX_GENERATED_LENGTH
import com.keymanage.domain.generator.MIN_GENERATED_LENGTH
import com.keymanage.domain.generator.isWeak
import com.keymanage.feature.generator.GeneratedPassword
import com.keymanage.feature.generator.GeneratorCharset
import com.keymanage.feature.generator.PasswordGeneratorMessages
import com.keymanage.feature.generator.PasswordGeneratorPresentableListener
import com.keymanage.feature.generator.PasswordGeneratorViewAction
import com.keymanage.feature.generator.PasswordGeneratorViewContract
import com.keymanage.feature.generator.PasswordGeneratorViewState
import kotlin.math.roundToInt

/**
 * 비밀번호 생성기의 Compose 뷰. 공유 PasswordGenerator RIB이 [render]로 상태를 넘기고, 입력은 [listener]로 돌려보낸다.
 * 상태와 흐름은 공유 Kotlin이 맡고 여기에는 그리는 일만 둔다
 */
class ComposePasswordGeneratorView : PasswordGeneratorViewContract, ComposeRenderable {
    override var listener: PasswordGeneratorPresentableListener? = null
    private var state by mutableStateOf(PasswordGeneratorViewState())

    override fun render(state: PasswordGeneratorViewState) {
        this.state = state
    }

    @Composable
    override fun Content() {
        PasswordGeneratorScreen(state) { listener?.handle(it) }
    }
}

/** 생성기 화면(목업 6단계). 길이는 슬라이더로 고르고, 고른 값을 공유 상태의 길이 글자로 보낸다 */
@Composable
fun PasswordGeneratorScreen(state: PasswordGeneratorViewState, onAction: (PasswordGeneratorViewAction) -> Unit) {
    val length = state.lengthText.toIntOrNull()?.coerceIn(MIN_GENERATED_LENGTH, MAX_GENERATED_LENGTH) ?: DEFAULT_GENERATOR_OPTIONS.length
    val generated = state.generated
    KMScreen(title = "생성기", onBack = if (state.showsClose) ({ onAction(PasswordGeneratorViewAction.Close) }) else null) {
        KMCard(modifier = Modifier.fillMaxWidth()) {
            // 보조 기술에는 비밀번호 원문 대신 새로 만들었다는 사실만 알린다
            Text(
                generated?.text ?: " ",
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    if (state.announcement.isNotEmpty()) contentDescription = state.announcement
                },
                style = DesignTokens.Typography.bodyMono,
            )
            if (generated != null) KMGaugeBar(score = generated.score)
        }
        state.problemMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (generated != null && isWeak(generated.score)) {
            Text(PasswordGeneratorMessages.WEAK_WARNING, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (generated?.hasSequentialDigits == true) {
            Text(PasswordGeneratorMessages.SEQUENTIAL_DIGITS_NOTICE, style = MaterialTheme.typography.bodySmall)
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("길이", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text("$length", style = DesignTokens.Typography.bodyMono)
        }
        Slider(
            value = length.toFloat(),
            onValueChange = { onAction(PasswordGeneratorViewAction.ChangeLength(it.roundToInt().toString())) },
            onValueChangeFinished = { onAction(PasswordGeneratorViewAction.FinishEditingLength) },
            valueRange = MIN_GENERATED_LENGTH.toFloat()..MAX_GENERATED_LENGTH.toFloat(),
            steps = MAX_GENERATED_LENGTH - MIN_GENERATED_LENGTH - 1,
            modifier = Modifier.semantics { stateDescription = "${length}자" },
        )
        CharsetRow("대문자", state.uppercase) { onAction(PasswordGeneratorViewAction.SetCharset(GeneratorCharset.UPPERCASE, it)) }
        CharsetRow("소문자", state.lowercase) { onAction(PasswordGeneratorViewAction.SetCharset(GeneratorCharset.LOWERCASE, it)) }
        CharsetRow("숫자", state.digits) { onAction(PasswordGeneratorViewAction.SetCharset(GeneratorCharset.DIGITS, it)) }
        CharsetRow("특수기호", state.symbols) { onAction(PasswordGeneratorViewAction.SetCharset(GeneratorCharset.SYMBOLS, it)) }
        state.copyError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space2)) {
            KMButton("다시 생성", onClick = { onAction(PasswordGeneratorViewAction.Regenerate) }, modifier = Modifier.weight(1f), variant = KMButtonVariant.OUTLINE)
            if (state.showsCopy) {
                KMButton("복사", onClick = { onAction(PasswordGeneratorViewAction.Copy) }, modifier = Modifier.weight(1f), isDisabled = !state.canUseGenerated)
            }
            if (state.showsUse) {
                KMButton("사용", onClick = { onAction(PasswordGeneratorViewAction.Use) }, modifier = Modifier.weight(1f), isDisabled = !state.canUseGenerated)
            }
        }
    }
}

@Composable
private fun CharsetRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Preview
@Composable
private fun PasswordGeneratorScreenPreview() {
    KeyManageTheme {
        PasswordGeneratorScreen(
            PasswordGeneratorViewState(generated = GeneratedPassword("k7#Qv2!mR9\$wLx4&Tz8p", score = 92, hasSequentialDigits = false), showsCopy = true),
            onAction = {},
        )
    }
}
