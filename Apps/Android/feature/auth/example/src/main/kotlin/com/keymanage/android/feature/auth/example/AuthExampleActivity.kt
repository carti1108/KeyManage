package com.keymanage.android.feature.auth.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.carti.ribs.mount
import com.keymanage.android.example.FeatureExample
import com.keymanage.android.example.RunningRib
import com.keymanage.android.feature.auth.ui.ComposeAuthView
import com.keymanage.feature.SessionPort
import com.keymanage.feature.auth.AuthBuilder
import com.keymanage.feature.auth.AuthDependency
import com.keymanage.feature.auth.AuthViewFactory
import com.keymanage.feature.auth.testing.AuthScenario
import com.keymanage.feature.auth.testing.FakeAuthListener

/**
 * Auth 피처만 띄우는 Example 앱(MusicSearch의 Feature<Name>Example에 해당한다).
 * 시나리오를 고르면 공유 Auth RIB을 가짜 세션으로 만들어 실제 Compose 뷰에 붙인다
 */
class AuthExampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FeatureExample("Auth 시나리오", AuthScenario.entries, AuthScenario::title, ::launch)
        }
    }

    private fun launch(scenario: AuthScenario, onEvent: (String) -> Unit): RunningRib {
        val view = ComposeAuthView()
        val dependency = object : AuthDependency {
            override val authViewFactory = AuthViewFactory { view }
            override val session: SessionPort = scenario.session()
        }
        return RunningRib(view, AuthBuilder(dependency).build(FakeAuthListener(onEvent), scenario.notice).mount())
    }
}
