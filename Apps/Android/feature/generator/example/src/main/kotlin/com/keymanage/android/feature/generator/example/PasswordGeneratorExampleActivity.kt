package com.keymanage.android.feature.generator.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.carti.ribs.mount
import com.keymanage.android.example.FeatureExample
import com.keymanage.android.example.RunningRib
import com.keymanage.android.feature.generator.ui.ComposePasswordGeneratorView
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.generator.PasswordGeneratorBuilder
import com.keymanage.feature.generator.PasswordGeneratorDependency
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.generator.testing.PasswordGeneratorScenario
import com.keymanage.feature.generator.testing.RecordingGeneratorListener
import com.keymanage.feature.generator.testing.SeededRandomBytes

/**
 * PasswordGenerator 피처만 띄우는 Example 앱(MusicSearch의 Feature<Name>Example에 해당한다).
 * 시나리오를 고르면 공유 PasswordGenerator RIB을 가짜 클립보드로 만들어 실제 Compose 뷰에 붙인다
 */
class PasswordGeneratorExampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FeatureExample("PasswordGenerator 시나리오", PasswordGeneratorScenario.entries, PasswordGeneratorScenario::title, ::launch)
        }
    }

    private fun launch(scenario: PasswordGeneratorScenario, onEvent: (String) -> Unit): RunningRib {
        val view = ComposePasswordGeneratorView()
        val fakeClipboard = scenario.clipboard()
        val dependency = object : PasswordGeneratorDependency {
            override val passwordGeneratorViewFactory = PasswordGeneratorViewFactory { view }
            // 복사는 부모에 알리는 일이 아니지만, 무엇이 복사됐는지 볼 수 있게 같은 자리에 보인다
            override val clipboard = ClipboardPort { text ->
                fakeClipboard.copy(text)
                onEvent("클립보드에 복사(${text.length}자)")
            }
            override val randomBytes: RandomBytes = SeededRandomBytes()
        }
        val listener = if (scenario.insideForm) RecordingGeneratorListener(onEvent) else null
        return RunningRib(view, PasswordGeneratorBuilder(dependency).build(listener, scenario.allowCopy).mount())
    }
}
