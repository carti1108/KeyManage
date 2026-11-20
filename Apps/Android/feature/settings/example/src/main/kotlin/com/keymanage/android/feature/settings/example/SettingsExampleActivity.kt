package com.keymanage.android.feature.settings.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.carti.ribs.mount
import com.keymanage.android.example.FeatureExample
import com.keymanage.android.example.RunningRib
import com.keymanage.android.feature.settings.ui.ComposeSettingsView
import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.BackupFileSource
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.settings.SettingsBuilder
import com.keymanage.feature.settings.SettingsDependency
import com.keymanage.feature.settings.SettingsViewFactory
import com.keymanage.feature.settings.testing.FakeSettingsListener
import com.keymanage.feature.settings.testing.SettingsScenario

/**
 * Settings 피처만 띄우는 Example 앱(MusicSearch의 Feature<Name>Example에 해당한다).
 * 시나리오를 고르면 공유 Settings RIB을 가짜 세션·백업 파일·환경으로 만들어 실제 Compose 뷰에 붙인다
 */
class SettingsExampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FeatureExample("Settings 시나리오", SettingsScenario.entries, SettingsScenario::title, ::launch)
        }
    }

    private fun launch(scenario: SettingsScenario, onEvent: (String) -> Unit): RunningRib {
        val view = ComposeSettingsView()
        val fakeFiles = scenario.backupFiles()
        val dependency = object : SettingsDependency {
            override val settingsViewFactory = SettingsViewFactory { view }
            override val session: SessionPort = scenario.session()
            // 파일 저장은 부모에 알리는 일이 아니지만, 무엇이 저장됐는지 볼 수 있게 같은 자리에 보인다
            override val backupFiles = object : BackupFilesPort {
                override suspend fun save(file: BackupFile) {
                    fakeFiles.save(file)
                    onEvent("백업 파일 저장(${file.name})")
                }

                override suspend fun pick(): BackupFileSource? = fakeFiles.pick()
            }
            override val environment: AppEnvironment = scenario.environment()
        }
        return RunningRib(view, SettingsBuilder(dependency).build(FakeSettingsListener(onEvent)).mount())
    }
}
