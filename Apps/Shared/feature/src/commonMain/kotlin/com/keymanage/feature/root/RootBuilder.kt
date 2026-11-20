package com.keymanage.feature.root

import com.carti.ribs.Builder
import com.carti.ribs.Component
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.auth.AuthBuilder
import com.keymanage.feature.auth.AuthDependency
import com.keymanage.feature.auth.AuthViewFactory
import com.keymanage.feature.generator.PasswordGeneratorBuildable
import com.keymanage.feature.generator.PasswordGeneratorBuilder
import com.keymanage.feature.generator.PasswordGeneratorDependency
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.main.MainBuilder
import com.keymanage.feature.main.MainDependency
import com.keymanage.feature.main.MainViewFactory
import com.keymanage.feature.settings.SettingsBuildable
import com.keymanage.feature.settings.SettingsBuilder
import com.keymanage.feature.settings.SettingsDependency
import com.keymanage.feature.settings.SettingsViewFactory
import com.keymanage.feature.unlock.UnlockBuilder
import com.keymanage.feature.unlock.UnlockDependency
import com.keymanage.feature.unlock.UnlockViewFactory
import com.keymanage.feature.vaultlist.VaultListBuildable
import com.keymanage.feature.vaultlist.VaultListBuilder
import com.keymanage.feature.vaultlist.VaultListDependency
import com.keymanage.feature.vaultlist.VaultListViewFactory

class RootComponent(dependency: RootDependency) :
    Component<RootDependency>(dependency), AuthDependency, UnlockDependency, MainDependency {
    override val authViewFactory: AuthViewFactory get() = dependency.authViewFactory
    override val unlockViewFactory: UnlockViewFactory get() = dependency.unlockViewFactory
    override val mainViewFactory: MainViewFactory get() = dependency.mainViewFactory
    override val vaultListViewFactory: VaultListViewFactory get() = dependency.vaultListViewFactory
    override val passwordGeneratorViewFactory: PasswordGeneratorViewFactory get() = dependency.passwordGeneratorViewFactory
    override val settingsViewFactory: SettingsViewFactory get() = dependency.settingsViewFactory
    override val session: SessionPort get() = dependency.session
    override val environment: AppEnvironment get() = dependency.environment
    override val clipboard: ClipboardPort get() = dependency.clipboard
    override val backupFiles: BackupFilesPort get() = dependency.backupFiles
    override val randomBytes: RandomBytes get() = dependency.randomBytes

    // Main과 그 자식이 쓸 구현 Builder. 피처는 서로의 interface만 보므로 구현은 여기서 넣는다 (modularization-plan 3장 1번)
    override fun vaultListBuilder(dependency: VaultListDependency): VaultListBuildable = VaultListBuilder(dependency)
    override fun passwordGeneratorBuilder(dependency: PasswordGeneratorDependency): PasswordGeneratorBuildable =
        PasswordGeneratorBuilder(dependency)
    override fun settingsBuilder(dependency: SettingsDependency): SettingsBuildable = SettingsBuilder(dependency)
}

class RootBuilder(dependency: RootDependency) : Builder<RootDependency>(dependency) {
    fun build(initialNotice: String? = null): RootRouter {
        val component = RootComponent(dependency)
        return RootRouter(
            interactor = RootInteractor(dependency.session, initialNotice),
            view = dependency.rootViewFactory.makeRootView(),
            authBuilder = AuthBuilder(component),
            unlockBuilder = UnlockBuilder(component),
            mainBuilder = MainBuilder(component),
        )
    }
}
