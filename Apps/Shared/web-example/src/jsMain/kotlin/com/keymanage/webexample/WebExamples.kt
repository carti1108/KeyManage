@file:OptIn(ExperimentalJsExport::class)

package com.keymanage.webexample

import com.carti.ribs.RibMount
import com.carti.ribs.mount
import com.carti.ribs.web.WebViewControllable
import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.BackupFileSource
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.auth.AuthBuilder
import com.keymanage.feature.auth.AuthDependency
import com.keymanage.feature.auth.AuthViewFactory
import com.keymanage.feature.auth.testing.AuthScenario
import com.keymanage.feature.auth.testing.FakeAuthListener
import com.keymanage.feature.generator.PasswordGeneratorBuildable
import com.keymanage.feature.generator.PasswordGeneratorBuilder
import com.keymanage.feature.generator.PasswordGeneratorDependency
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.generator.testing.PasswordGeneratorScenario
import com.keymanage.feature.generator.testing.RecordingGeneratorListener
import com.keymanage.feature.generator.testing.SeededRandomBytes
import com.keymanage.feature.main.MainBuilder
import com.keymanage.feature.main.MainDependency
import com.keymanage.feature.main.MainViewFactory
import com.keymanage.feature.main.testing.MainScenario
import com.keymanage.feature.main.testing.RecordingMainListener
import com.keymanage.feature.settings.SettingsBuildable
import com.keymanage.feature.settings.SettingsBuilder
import com.keymanage.feature.settings.SettingsDependency
import com.keymanage.feature.settings.SettingsViewFactory
import com.keymanage.feature.settings.testing.FakeSettingsListener
import com.keymanage.feature.settings.testing.SettingsScenario
import com.keymanage.feature.testing.FakeBackupFiles
import com.keymanage.feature.testing.FakeClipboard
import com.keymanage.feature.testing.FakeRandomBytes
import com.keymanage.feature.unlock.UnlockBuilder
import com.keymanage.feature.unlock.UnlockDependency
import com.keymanage.feature.unlock.UnlockViewFactory
import com.keymanage.feature.unlock.testing.FakeUnlockListener
import com.keymanage.feature.unlock.testing.UnlockScenario
import com.keymanage.feature.vaultlist.VaultListBuildable
import com.keymanage.feature.vaultlist.VaultListBuilder
import com.keymanage.feature.vaultlist.VaultListDependency
import com.keymanage.feature.vaultlist.VaultListViewFactory
import com.keymanage.feature.vaultlist.testing.RecordingVaultListListener
import com.keymanage.feature.vaultlist.testing.VaultListScenario
import com.keymanage.websession.WebAuthView
import com.keymanage.websession.WebGeneratorView
import com.keymanage.websession.WebMainView
import com.keymanage.websession.WebSettingsView
import com.keymanage.websession.WebUnlockView
import com.keymanage.websession.WebVaultListView
import kotlin.time.Clock

// 웹 피처 Example. Android의 feature/<이름>/example과 같은 일을 한다:
// 시나리오를 고르면 공유 RIB 하나를 가짜 세션으로 만들어 실제 웹 뷰에 붙인다. 서버와 실제 세션은 쓰지 않는다

/** 피처 하나와 그 시나리오 이름들. 시나리오는 [scenarios]의 순서 번호로 고른다 */
@JsExport
class WebExampleFeature internal constructor(val key: String, val title: String, val scenarios: Array<String>)

/** 띄운 RIB 하나. React는 [screenKey]에 맞는 화면으로 [view]를 그리고, 닫을 때 [stop]을 부른다 */
@JsExport
class WebRunningExample internal constructor(val screenKey: String, val view: Any, private val mount: RibMount) {
    fun stop() {
        if (mount.isMounted) mount.unmount()
    }
}

private class Example<S>(
    val key: String,
    val title: String,
    val scenarios: List<S>,
    val label: (S) -> String,
    val launch: (S, (String) -> Unit) -> WebRunningExample,
) {
    fun describe() = WebExampleFeature(key, title, scenarios.map(label).toTypedArray())
    fun launchAt(index: Int, onEvent: (String) -> Unit) = launch(scenarios[index], onEvent)
}

private fun running(view: WebViewControllable, mount: RibMount) = WebRunningExample(view.screenKey, view, mount)

private val examples: List<Example<*>> = listOf(
    Example("auth", "Auth", AuthScenario.entries, AuthScenario::title, ::launchAuth),
    Example("unlock", "Unlock", UnlockScenario.entries, UnlockScenario::title, ::launchUnlock),
    Example("main", "Main", MainScenario.entries, MainScenario::title, ::launchMain),
    Example("vaultlist", "VaultList", VaultListScenario.entries, VaultListScenario::title, ::launchVaultList),
    Example("generator", "PasswordGenerator", PasswordGeneratorScenario.entries, PasswordGeneratorScenario::title, ::launchGenerator),
    Example("settings", "Settings", SettingsScenario.entries, SettingsScenario::title, ::launchSettings),
)

/** Example로 띄울 수 있는 피처 목록 */
@JsExport
fun exampleFeatures(): Array<WebExampleFeature> = examples.map { it.describe() }.toTypedArray()

/**
 * [featureKey] 피처의 [scenarioIndex]번째 시나리오로 RIB을 만들어 붙인다.
 * @param onEvent RIB이 부모에 알린 내용 같은 결과가 문자열로 온다
 */
@JsExport
fun launchExample(featureKey: String, scenarioIndex: Int, onEvent: (String) -> Unit): WebRunningExample {
    val example = examples.firstOrNull { it.key == featureKey } ?: throw IllegalArgumentException("모르는 피처: $featureKey")
    require(scenarioIndex in example.scenarios.indices) { "없는 시나리오 번호: $scenarioIndex" }
    return example.launchAt(scenarioIndex, onEvent)
}

private fun launchAuth(scenario: AuthScenario, onEvent: (String) -> Unit): WebRunningExample {
    val view = WebAuthView()
    val dependency = object : AuthDependency {
        override val authViewFactory = AuthViewFactory { view }
        override val session: SessionPort = scenario.session()
    }
    return running(view, AuthBuilder(dependency).build(FakeAuthListener(onEvent), scenario.notice).mount())
}

private fun launchUnlock(scenario: UnlockScenario, onEvent: (String) -> Unit): WebRunningExample {
    val view = WebUnlockView()
    val dependency = object : UnlockDependency {
        override val unlockViewFactory = UnlockViewFactory { view }
        override val session: SessionPort = scenario.session()
    }
    return running(view, UnlockBuilder(dependency).build(FakeUnlockListener(onEvent)).mount())
}

private fun launchMain(scenario: MainScenario, onEvent: (String) -> Unit): WebRunningExample {
    val view = WebMainView()
    val dependency = object : MainDependency {
        override val mainViewFactory = MainViewFactory { view }
        override val vaultListViewFactory = VaultListViewFactory { WebVaultListView() }
        override val passwordGeneratorViewFactory = PasswordGeneratorViewFactory { WebGeneratorView() }
        override val settingsViewFactory = SettingsViewFactory { WebSettingsView() }
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
    return running(view, MainBuilder(dependency).build(RecordingMainListener(onEvent), scenario.offline).mount())
}

private fun launchVaultList(scenario: VaultListScenario, onEvent: (String) -> Unit): WebRunningExample {
    val view = WebVaultListView()
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
        override val passwordGeneratorViewFactory = PasswordGeneratorViewFactory { WebGeneratorView() }
        override val randomBytes: RandomBytes = SeededRandomBytes()
        override fun passwordGeneratorBuilder(dependency: PasswordGeneratorDependency): PasswordGeneratorBuildable =
            PasswordGeneratorBuilder(dependency)
    }
    return running(view, VaultListBuilder(dependency).build(RecordingVaultListListener(onEvent)).mount())
}

private fun launchGenerator(scenario: PasswordGeneratorScenario, onEvent: (String) -> Unit): WebRunningExample {
    val view = WebGeneratorView()
    val fakeClipboard = scenario.clipboard()
    val dependency = object : PasswordGeneratorDependency {
        override val passwordGeneratorViewFactory = PasswordGeneratorViewFactory { view }
        override val clipboard = ClipboardPort { text ->
            fakeClipboard.copy(text)
            onEvent("클립보드에 복사(${text.length}자)")
        }
        override val randomBytes: RandomBytes = SeededRandomBytes()
    }
    val listener = if (scenario.insideForm) RecordingGeneratorListener(onEvent) else null
    return running(view, PasswordGeneratorBuilder(dependency).build(listener, scenario.allowCopy).mount())
}

private fun launchSettings(scenario: SettingsScenario, onEvent: (String) -> Unit): WebRunningExample {
    val view = WebSettingsView()
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
    return running(view, SettingsBuilder(dependency).build(FakeSettingsListener(onEvent)).mount())
}
