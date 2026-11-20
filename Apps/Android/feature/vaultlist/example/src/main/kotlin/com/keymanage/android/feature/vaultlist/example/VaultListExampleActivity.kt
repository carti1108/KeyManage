package com.keymanage.android.feature.vaultlist.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.carti.ribs.mount
import com.keymanage.android.example.FeatureExample
import com.keymanage.android.example.RunningRib
import com.keymanage.android.feature.generator.ui.ComposePasswordGeneratorView
import com.keymanage.android.feature.vaultlist.ui.ComposeVaultListView
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.generator.PasswordGeneratorBuildable
import com.keymanage.feature.generator.PasswordGeneratorBuilder
import com.keymanage.feature.generator.PasswordGeneratorDependency
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.generator.testing.SeededRandomBytes
import com.keymanage.feature.vaultlist.VaultListBuilder
import com.keymanage.feature.vaultlist.VaultListDependency
import com.keymanage.feature.vaultlist.VaultListViewFactory
import com.keymanage.feature.vaultlist.testing.RecordingVaultListListener
import com.keymanage.feature.vaultlist.testing.VaultListScenario
import kotlin.time.Clock

/**
 * VaultList 피처만 띄우는 Example 앱(MusicSearch의 Feature<Name>Example에 해당한다).
 * 시나리오를 고르면 공유 VaultList RIB을 가짜 세션으로 만들어 실제 Compose 뷰에 붙인다.
 * 폼 안의 비밀번호 생성기는 자식 RIB이므로 그 구현과 뷰도 여기서 넣는다
 */
class VaultListExampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FeatureExample("VaultList 시나리오", VaultListScenario.entries, VaultListScenario::title, ::launch)
        }
    }

    private fun launch(scenario: VaultListScenario, onEvent: (String) -> Unit): RunningRib {
        val view = ComposeVaultListView()
        val fakeClipboard = scenario.clipboard()
        val dependency = object : VaultListDependency {
            override val vaultListViewFactory = VaultListViewFactory { view }
            override val session: SessionPort = scenario.session()
            // 복사는 부모에 알리는 일이 아니지만, 무엇이 복사됐는지 볼 수 있게 같은 자리에 보인다
            override val clipboard = ClipboardPort { text ->
                fakeClipboard.copy(text)
                onEvent("클립보드에 복사(${text.length}자)")
            }
            override val clock: Clock = Clock.System
            override val passwordGeneratorViewFactory = PasswordGeneratorViewFactory { ComposePasswordGeneratorView() }
            override val randomBytes: RandomBytes = SeededRandomBytes()
            override fun passwordGeneratorBuilder(dependency: PasswordGeneratorDependency): PasswordGeneratorBuildable =
                PasswordGeneratorBuilder(dependency)
        }
        return RunningRib(view, VaultListBuilder(dependency).build(RecordingVaultListListener(onEvent)).mount())
    }
}
