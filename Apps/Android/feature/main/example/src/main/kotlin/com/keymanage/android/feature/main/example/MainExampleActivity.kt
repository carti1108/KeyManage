package com.keymanage.android.feature.main.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.carti.ribs.mount
import com.keymanage.android.example.FeatureExample
import com.keymanage.android.example.RunningRib
import com.keymanage.android.feature.generator.ui.ComposePasswordGeneratorView
import com.keymanage.android.feature.main.ui.ComposeMainView
import com.keymanage.android.feature.settings.ui.ComposeSettingsView
import com.keymanage.android.feature.vaultlist.ui.ComposeVaultListView
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.generator.PasswordGeneratorBuildable
import com.keymanage.feature.generator.PasswordGeneratorBuilder
import com.keymanage.feature.generator.PasswordGeneratorDependency
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.main.MainBuilder
import com.keymanage.feature.main.MainDependency
import com.keymanage.feature.main.MainViewFactory
import com.keymanage.feature.main.testing.MainScenario
import com.keymanage.feature.main.testing.RecordingMainListener
import com.keymanage.feature.settings.SettingsBuildable
import com.keymanage.feature.settings.SettingsBuilder
import com.keymanage.feature.settings.SettingsDependency
import com.keymanage.feature.settings.SettingsViewFactory
import com.keymanage.feature.testing.FakeBackupFiles
import com.keymanage.feature.testing.FakeClipboard
import com.keymanage.feature.testing.FakeRandomBytes
import com.keymanage.feature.vaultlist.VaultListBuildable
import com.keymanage.feature.vaultlist.VaultListBuilder
import com.keymanage.feature.vaultlist.VaultListDependency
import com.keymanage.feature.vaultlist.VaultListViewFactory

/**
 * Main 피처만 띄우는 Example 앱(MusicSearch의 Feature<Name>Example에 해당한다).
 * 시나리오를 고르면 공유 Main RIB을 가짜 세션으로 만들어 실제 Compose 뷰에 붙인다.
 * 탭 안의 볼트 목록·생성기·설정은 자식 RIB이므로 그 구현과 뷰도 여기서 넣는다
 */
class MainExampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FeatureExample("Main 시나리오", MainScenario.entries, MainScenario::title, ::launch)
        }
    }

    private fun launch(scenario: MainScenario, onEvent: (String) -> Unit): RunningRib {
        val view = ComposeMainView()
        val dependency = object : MainDependency {
            override val mainViewFactory = MainViewFactory { view }
            override val vaultListViewFactory = VaultListViewFactory { ComposeVaultListView() }
            override val passwordGeneratorViewFactory = PasswordGeneratorViewFactory { ComposePasswordGeneratorView() }
            override val settingsViewFactory = SettingsViewFactory { ComposeSettingsView() }
            override val session: SessionPort = scenario.session()
            override val environment: AppEnvironment = scenario.environment()
            override val clipboard: ClipboardPort = FakeClipboard()
            override val backupFiles: BackupFilesPort = FakeBackupFiles()
            override val randomBytes: RandomBytes = FakeRandomBytes()

            override fun vaultListBuilder(dependency: VaultListDependency): VaultListBuildable = VaultListBuilder(dependency)
            override fun passwordGeneratorBuilder(dependency: PasswordGeneratorDependency): PasswordGeneratorBuildable =
                PasswordGeneratorBuilder(dependency)
            override fun settingsBuilder(dependency: SettingsDependency): SettingsBuildable = SettingsBuilder(dependency)
        }
        return RunningRib(view, MainBuilder(dependency).build(RecordingMainListener(onEvent), scenario.offline).mount())
    }
}
