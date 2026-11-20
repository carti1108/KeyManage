package com.keymanage.android.feature.unlock.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.carti.ribs.mount
import com.keymanage.android.example.FeatureExample
import com.keymanage.android.example.RunningRib
import com.keymanage.android.feature.unlock.ui.ComposeUnlockView
import com.keymanage.feature.SessionPort
import com.keymanage.feature.unlock.UnlockBuilder
import com.keymanage.feature.unlock.UnlockDependency
import com.keymanage.feature.unlock.UnlockViewFactory
import com.keymanage.feature.unlock.testing.FakeUnlockListener
import com.keymanage.feature.unlock.testing.UnlockScenario

/**
 * Unlock 피처만 띄우는 Example 앱(MusicSearch의 Feature<Name>Example에 해당한다).
 * 시나리오를 고르면 공유 Unlock RIB을 가짜 세션으로 만들어 실제 Compose 뷰에 붙인다
 */
class UnlockExampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FeatureExample("Unlock 시나리오", UnlockScenario.entries, UnlockScenario::title, ::launch)
        }
    }

    private fun launch(scenario: UnlockScenario, onEvent: (String) -> Unit): RunningRib {
        val view = ComposeUnlockView()
        val dependency = object : UnlockDependency {
            override val unlockViewFactory = UnlockViewFactory { view }
            override val session: SessionPort = scenario.session()
        }
        return RunningRib(view, UnlockBuilder(dependency).build(FakeUnlockListener(onEvent)).mount())
    }
}
