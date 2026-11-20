package com.keymanage.android.feature.unlock.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import com.carti.ribs.mount
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.generated.DesignTokens
import com.keymanage.android.feature.unlock.ui.ComposeUnlockView
import com.keymanage.feature.SessionPort
import com.keymanage.feature.unlock.UnlockBuilder
import com.keymanage.feature.unlock.UnlockDependency
import com.keymanage.feature.unlock.UnlockViewFactory
import com.keymanage.feature.unlock.testing.FakeUnlockListener
import com.keymanage.feature.unlock.testing.UnlockScenario

/**
 * Unlock 피처만 띄우는 Example 앱(MusicSearch의 Feature<Name>Example에 해당한다).
 * 시나리오를 고르면 공유 Unlock RIB을 가짜 세션으로 만들어 실제 Compose 뷰에 붙인다. 서버와 실제 세션은 쓰지 않는다
 */
class UnlockExampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KeyManageTheme {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) { UnlockExample() }
            }
        }
    }
}

@Composable
private fun UnlockExample() {
    var scenario by remember { mutableStateOf<UnlockScenario?>(null) }
    val picked = scenario
    if (picked == null) {
        ScenarioList(onPick = { scenario = it })
    } else {
        ScenarioScreen(picked, onClose = { scenario = null })
    }
}

@Composable
private fun ScenarioList(onPick: (UnlockScenario) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "Unlock 시나리오",
                modifier = Modifier.padding(DesignTokens.Spacing.space4).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        items(UnlockScenario.entries) { scenario ->
            Text(
                scenario.title,
                modifier = Modifier.fillMaxWidth().clickable { onPick(scenario) }.padding(DesignTokens.Spacing.space4),
                style = MaterialTheme.typography.bodyLarge,
            )
            HorizontalDivider()
        }
    }
}

/** 고른 시나리오로 띄운 RIB 하나. 화면에서 나가면 내린다 */
private class RunningScenario(scenario: UnlockScenario) {
    val view = ComposeUnlockView()

    /** RIB이 부모에 알린 내용. 실제 앱에서는 Root가 받아 화면을 바꾼다 */
    val events = mutableStateListOf<String>()
    private val mount: RibMount

    init {
        val dependency = object : UnlockDependency {
            override val unlockViewFactory = UnlockViewFactory { view }
            override val session: SessionPort = scenario.session()
        }
        mount = UnlockBuilder(dependency).build(FakeUnlockListener { events += it }).mount()
    }

    fun close() = mount.unmount()
}

@Composable
private fun ScenarioScreen(scenario: UnlockScenario, onClose: () -> Unit) {
    val running = remember(scenario) { RunningScenario(scenario) }
    DisposableEffect(running) { onDispose { running.close() } }
    BackHandler(onBack = onClose)
    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onClose) { Text("닫기") }
        Box(modifier = Modifier.weight(1f)) { running.view.Content() }
        if (running.events.isNotEmpty()) {
            HorizontalDivider()
            Text(
                "부모에 알림: " + running.events.last(),
                modifier = Modifier.fillMaxWidth().padding(DesignTokens.Spacing.space4),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
