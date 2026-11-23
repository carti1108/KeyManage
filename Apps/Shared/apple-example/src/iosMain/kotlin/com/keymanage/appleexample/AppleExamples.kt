package com.keymanage.appleexample

import com.carti.ribs.RibMount
import com.carti.ribs.mount
import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.BackupFileSource
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.auth.AuthBuilder
import com.keymanage.feature.auth.AuthDependency
import com.keymanage.feature.auth.AuthViewContract
import com.keymanage.feature.auth.AuthViewFactory
import com.keymanage.feature.auth.testing.AuthScenario
import com.keymanage.feature.auth.testing.FakeAuthListener
import com.keymanage.feature.generator.PasswordGeneratorBuildable
import com.keymanage.feature.generator.PasswordGeneratorBuilder
import com.keymanage.feature.generator.PasswordGeneratorDependency
import com.keymanage.feature.generator.PasswordGeneratorViewContract
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.generator.testing.PasswordGeneratorScenario
import com.keymanage.feature.generator.testing.RecordingGeneratorListener
import com.keymanage.feature.generator.testing.SeededRandomBytes
import com.keymanage.feature.generator.testing.FakeGeneratorClipboard
import com.keymanage.feature.main.MainBuilder
import com.keymanage.feature.main.MainDependency
import com.keymanage.feature.main.MainViewFactory
import com.keymanage.feature.main.testing.MainScenario
import com.keymanage.feature.main.testing.RecordingMainListener
import com.keymanage.feature.settings.SettingsBuildable
import com.keymanage.feature.settings.SettingsBuilder
import com.keymanage.feature.settings.SettingsDependency
import com.keymanage.feature.settings.SettingsViewContract
import com.keymanage.feature.settings.SettingsViewFactory
import com.keymanage.feature.settings.testing.FakeSettingsListener
import com.keymanage.feature.settings.testing.SettingsScenario
import com.keymanage.feature.testing.FakeBackupFiles
import com.keymanage.feature.testing.FakeClipboard
import com.keymanage.feature.testing.FakeRandomBytes
import com.keymanage.feature.unlock.UnlockBuilder
import com.keymanage.feature.unlock.UnlockDependency
import com.keymanage.feature.unlock.UnlockViewContract
import com.keymanage.feature.unlock.UnlockViewFactory
import com.keymanage.feature.unlock.testing.FakeUnlockListener
import com.keymanage.feature.unlock.testing.UnlockScenario
import com.keymanage.feature.vaultlist.VaultListBuildable
import com.keymanage.feature.vaultlist.VaultListBuilder
import com.keymanage.feature.vaultlist.VaultListDependency
import com.keymanage.feature.vaultlist.VaultListViewContract
import com.keymanage.feature.vaultlist.VaultListViewFactory
import com.keymanage.feature.vaultlist.testing.RecordingVaultListListener
import com.keymanage.feature.vaultlist.testing.VaultListScenario
import com.keymanage.feature.main.MainViewContract
import kotlin.time.Clock

// iOS 피처 Example. 웹 web-example, Android feature/<이름>/example과 같은 조립이다:
// 시나리오 번호로 공유 RIB 하나를 가짜 세션으로 만들어 Swift가 넘긴 뷰에 붙인다. Swift는 뷰만 만들고 결과로 받은 RibMount를 내린다

/** Swift Example이 고를 피처 */
enum class AppleExampleFeature(val title: String, val scenarios: List<String>) {
    UNLOCK("Unlock", UnlockScenario.entries.map { it.title }),
    AUTH("Auth", AuthScenario.entries.map { it.title }),
    GENERATOR("PasswordGenerator", PasswordGeneratorScenario.entries.map { it.title }),
    VAULT_LIST("VaultList", VaultListScenario.entries.map { it.title }),
    SETTINGS("Settings", SettingsScenario.entries.map { it.title }),
    MAIN("Main", MainScenario.entries.map { it.title }),
}

/** Main Example이 자식 RIB에 쓸 Swift 뷰 팩토리 */
class AppleExampleChildViews(
    val vaultList: VaultListViewFactory,
    val generator: PasswordGeneratorViewFactory,
    val settings: SettingsViewFactory,
)

object AppleExamples {
    fun launchUnlock(index: Int, view: UnlockViewContract, onEvent: (String) -> Unit): RibMount {
        val scenario = UnlockScenario.entries[index]
        val dependency = object : UnlockDependency {
            override val unlockViewFactory = UnlockViewFactory { view }
            override val session: SessionPort = scenario.session()
        }
        return UnlockBuilder(dependency).build(FakeUnlockListener(onEvent)).mount()
    }

    fun launchAuth(index: Int, view: AuthViewContract, onEvent: (String) -> Unit): RibMount {
        val scenario = AuthScenario.entries[index]
        val dependency = object : AuthDependency {
            override val authViewFactory = AuthViewFactory { view }
            override val session: SessionPort = scenario.session()
        }
        return AuthBuilder(dependency).build(FakeAuthListener(onEvent), scenario.notice).mount()
    }

    fun launchGenerator(index: Int, view: PasswordGeneratorViewContract, onEvent: (String) -> Unit): RibMount {
        val scenario = PasswordGeneratorScenario.entries[index]
        val dependency = object : PasswordGeneratorDependency {
            override val passwordGeneratorViewFactory = PasswordGeneratorViewFactory { view }
            override val clipboard = recordingClipboard(scenario.clipboard(), onEvent)
            override val randomBytes: RandomBytes = SeededRandomBytes()
        }
        val listener = if (scenario.insideForm) RecordingGeneratorListener(onEvent) else null
        return PasswordGeneratorBuilder(dependency).build(listener, scenario.allowCopy).mount()
    }

    fun launchVaultList(
        index: Int,
        view: VaultListViewContract,
        generatorViews: PasswordGeneratorViewFactory,
        onEvent: (String) -> Unit,
    ): RibMount {
        val scenario = VaultListScenario.entries[index]
        val dependency = object : VaultListDependency {
            override val vaultListViewFactory = VaultListViewFactory { view }
            override val session: SessionPort = scenario.session()
            override val clipboard = recordingClipboard(scenario.clipboard(), onEvent)
            override val clock: Clock = Clock.System
            override val passwordGeneratorViewFactory = generatorViews
            override val randomBytes: RandomBytes = SeededRandomBytes()
            override fun passwordGeneratorBuilder(dependency: PasswordGeneratorDependency): PasswordGeneratorBuildable =
                PasswordGeneratorBuilder(dependency)
        }
        return VaultListBuilder(dependency).build(RecordingVaultListListener(onEvent)).mount()
    }

    fun launchSettings(index: Int, view: SettingsViewContract, onEvent: (String) -> Unit): RibMount {
        val scenario = SettingsScenario.entries[index]
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
        return SettingsBuilder(dependency).build(FakeSettingsListener(onEvent)).mount()
    }

    fun launchMain(index: Int, view: MainViewContract, children: AppleExampleChildViews, onEvent: (String) -> Unit): RibMount {
        val scenario = MainScenario.entries[index]
        val dependency = object : MainDependency {
            override val mainViewFactory = MainViewFactory { view }
            override val vaultListViewFactory = children.vaultList
            override val passwordGeneratorViewFactory = children.generator
            override val settingsViewFactory = children.settings
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
        return MainBuilder(dependency).build(RecordingMainListener(onEvent), scenario.offline).mount()
    }

    /** 복사는 부모에 알리는 일이 아니지만, 무엇이 복사됐는지 볼 수 있게 같은 자리에 보인다 */
    private fun recordingClipboard(fake: FakeGeneratorClipboard, onEvent: (String) -> Unit) = ClipboardPort { text ->
        fake.copy(text)
        onEvent("클립보드에 복사(${text.length}자)")
    }
}
