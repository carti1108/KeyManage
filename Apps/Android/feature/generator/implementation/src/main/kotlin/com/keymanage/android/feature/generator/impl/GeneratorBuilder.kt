package com.keymanage.android.feature.generator.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.keymanage.android.designsystem.component.KMCard
import com.keymanage.android.designsystem.component.KMScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.component.KMBurnBadge
import com.keymanage.android.designsystem.component.KMButton
import com.keymanage.android.designsystem.component.KMButtonVariant
import com.keymanage.android.designsystem.component.KMGaugeBar
import com.keymanage.android.designsystem.generated.DesignTokens
import com.keymanage.android.domain.generator.GeneratorOptions
import com.keymanage.android.feature.generator.GeneratorBuildable
import com.keymanage.android.feature.generator.GeneratorDependency
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.BasicComposeRouter
import com.uber.rib.core.ComposePresenter
import com.uber.rib.core.Router
import kotlin.math.roundToInt

class GeneratorPresenter(
    private val store: ViewStateStore<GeneratorViewState>,
    private val actions: ViewActionStream<GeneratorViewAction>,
) : ComposePresenter() {
    override val composable: @Composable () -> Unit = {
        val state by store.state.collectAsState()
        GeneratorView(state = state, onAction = { actions.send(it) })
    }
}

class GeneratorBuilder(private val dependency: GeneratorDependency) : GeneratorBuildable {
    override fun build(slot: MutableState<@Composable () -> Unit>): Router<*> {
        val store = ViewStateStore(GeneratorViewState())
        val actions = ViewActionStream<GeneratorViewAction>()
        val presenter = GeneratorPresenter(store, actions)
        return BasicComposeRouter(presenter, GeneratorInteractor(presenter, store, actions, dependency.generator, dependency.clipboard), slot)
    }
}

/** 생성기 화면(목업 6단계). */
@Composable
fun GeneratorView(state: GeneratorViewState, onAction: (GeneratorViewAction) -> Unit) {
    KMScreen(title = "생성기", onBack = null) {
        KMCard(modifier = Modifier.fillMaxWidth()) {
            Text(state.password.ifEmpty { " " }, style = DesignTokens.Typography.bodyMono)
            if (state.password.isNotEmpty()) KMGaugeBar(score = state.score)
        }
        state.problemMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (state.isWeak) Text(GeneratorMessages.WEAK, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (state.hasSequentialDigits) Text(GeneratorMessages.SEQUENTIAL, style = MaterialTheme.typography.bodySmall)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("길이", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text("${state.options.length}", style = DesignTokens.Typography.bodyMono)
        }
        Slider(
            value = state.options.length.toFloat(),
            onValueChange = { onAction(GeneratorViewAction.LengthChanged(it.roundToInt())) },
            valueRange = GeneratorOptions.MIN_LENGTH.toFloat()..GeneratorOptions.MAX_LENGTH.toFloat(),
            steps = GeneratorOptions.MAX_LENGTH - GeneratorOptions.MIN_LENGTH - 1,
            modifier = Modifier.semantics { stateDescription = "${state.options.length}자" },
        )
        CharsetRow("대문자", state.options.uppercase) { onAction(GeneratorViewAction.Toggle(Charset.UPPERCASE, it)) }
        CharsetRow("소문자", state.options.lowercase) { onAction(GeneratorViewAction.Toggle(Charset.LOWERCASE, it)) }
        CharsetRow("숫자", state.options.digits) { onAction(GeneratorViewAction.Toggle(Charset.DIGITS, it)) }
        CharsetRow("특수기호", state.options.symbols) { onAction(GeneratorViewAction.Toggle(Charset.SYMBOLS, it)) }
        if (state.copyFailed) Text(GeneratorMessages.COPY_FAILED, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        state.burnSeconds?.let { KMBurnBadge(remainingSeconds = it) }
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space2)) {
            KMButton("다시 생성", onClick = { onAction(GeneratorViewAction.Regenerate) }, modifier = Modifier.weight(1f), variant = KMButtonVariant.OUTLINE)
            KMButton(
                "복사",
                onClick = { onAction(GeneratorViewAction.Copy) },
                modifier = Modifier.weight(1f),
                isDisabled = state.password.isEmpty(),
            )
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
private fun GeneratorViewPreview() {
    KeyManageTheme { GeneratorView(GeneratorViewState(password = "k7#Qv2!mR9\$wLx4&Tz8p", burnSeconds = 28), onAction = {}) }
}
