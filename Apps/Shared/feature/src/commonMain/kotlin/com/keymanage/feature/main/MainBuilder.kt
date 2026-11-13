package com.keymanage.feature.main

import com.carti.ribs.Builder
import com.carti.ribs.Component
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.generator.PasswordGeneratorBuilder
import com.keymanage.feature.generator.PasswordGeneratorDependency
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.settings.SettingsBuilder
import com.keymanage.feature.settings.SettingsDependency
import com.keymanage.feature.settings.SettingsViewFactory
import com.keymanage.feature.vaultlist.VaultListBuilder
import com.keymanage.feature.vaultlist.VaultListDependency
import com.keymanage.feature.vaultlist.VaultListViewFactory
import kotlin.time.Clock

/** 볼트 목록·생성기·설정이 쓰는 의존성을 이 RIB의 의존성에서 만든다. 세션은 끝내는 작업을 세는 [MainSessionPort]로 감싼다 */
class MainComponent(dependency: MainDependency) :
    Component<MainDependency>(dependency), VaultListDependency, PasswordGeneratorDependency, SettingsDependency {
    val trackedSession: MainSessionPort by shared { MainSessionPort(dependency.session) }

    override val session: SessionPort get() = trackedSession
    override val vaultListViewFactory: VaultListViewFactory get() = dependency.vaultListViewFactory
    override val passwordGeneratorViewFactory: PasswordGeneratorViewFactory get() = dependency.passwordGeneratorViewFactory
    override val settingsViewFactory: SettingsViewFactory get() = dependency.settingsViewFactory
    override val clipboard: ClipboardPort get() = dependency.clipboard
    override val clock: Clock get() = dependency.environment.clock
    override val randomBytes: RandomBytes get() = dependency.randomBytes
    override val backupFiles: BackupFilesPort get() = dependency.backupFiles
    override val environment: AppEnvironment get() = dependency.environment
}

class MainBuilder(dependency: MainDependency) : Builder<MainDependency>(dependency) {
    /**
     * @param offline 잠금 해제를 서버에 닿지 못해 기기에 저장한 볼트로 열었다. 동기화에 성공하면 이 RIB이 걷는다 (VLT-002)
     */
    fun build(listener: MainListener, offline: Boolean = false): MainRouter {
        val component = MainComponent(dependency)
        val view = dependency.mainViewFactory.makeMainView()
        val interactor = MainInteractor(view, component.trackedSession, dependency.environment, listener, offline)
        return MainRouter(
            interactor = interactor,
            view = view,
            vaultListBuilder = VaultListBuilder(component),
            passwordGeneratorBuilder = PasswordGeneratorBuilder(component),
            settingsBuilder = SettingsBuilder(component),
        )
    }
}
