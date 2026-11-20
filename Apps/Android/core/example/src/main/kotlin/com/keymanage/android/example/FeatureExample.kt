package com.keymanage.android.example

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.carti.ribs.RibMount
import com.carti.ribs.compose.ComposeRenderable
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.generated.DesignTokens

/**
 * 피처 Example 앱의 공통 틀(MusicSearch의 DemoListViewController에 해당한다).
 * 시나리오 목록을 보여 주고, 고르면 [launch]가 만든 RIB 하나의 화면을 띄운다. 화면 아래에는 RIB이 부모에 알린 마지막 내용을 보인다.
 * 서버와 실제 세션은 쓰지 않는다. 사람이 기기에서 화면을 확인하는 용도다 (modularization-plan 3장 5번)
 *
 * @param launch 고른 시나리오로 RIB을 만들어 붙인다. `onEvent`로 넘긴 문자열이 "부모에 알림"으로 보인다
 */
@Composable
fun <S> FeatureExample(
    title: String,
    scenarios: List<S>,
    label: (S) -> String,
    launch: (scenario: S, onEvent: (String) -> Unit) -> RunningRib,
) {
    KeyManageTheme {
        Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            var picked by remember { mutableStateOf<S?>(null) }
            val scenario = picked
            if (scenario == null) {
                ScenarioList(title, scenarios, label, onPick = { picked = it })
            } else {
                ScenarioScreen(scenario, launch, onClose = { picked = null })
            }
        }
    }
}

/** 띄운 RIB 하나. [view]를 그리고, 닫을 때 [mount]를 내린다 */
class RunningRib(val view: ComposeRenderable, val mount: RibMount)

@Composable
private fun <S> ScenarioList(title: String, scenarios: List<S>, label: (S) -> String, onPick: (S) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                title,
                modifier = Modifier.padding(DesignTokens.Spacing.space4).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        items(scenarios) { scenario ->
            Text(
                label(scenario),
                modifier = Modifier.fillMaxWidth().clickable { onPick(scenario) }.padding(DesignTokens.Spacing.space4),
                style = MaterialTheme.typography.bodyLarge,
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun <S> ScenarioScreen(scenario: S, launch: (S, (String) -> Unit) -> RunningRib, onClose: () -> Unit) {
    val events = remember(scenario) { mutableStateListOf<String>() }
    val running = remember(scenario) { launch(scenario) { events += it } }
    DisposableEffect(running) { onDispose { running.mount.unmount() } }
    BackHandler(onBack = onClose)
    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onClose) { Text("닫기") }
        Box(modifier = Modifier.weight(1f)) { running.view.Content() }
        if (events.isNotEmpty()) {
            HorizontalDivider()
            Text(
                "부모에 알림: " + events.last(),
                modifier = Modifier.fillMaxWidth().padding(DesignTokens.Spacing.space4),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
