package com.keymanage.feature.root

import com.carti.ribs.Builder
import com.carti.ribs.Component
import kotlin.time.Clock
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.auth.AuthBuilder
import com.keymanage.feature.auth.AuthDependency
import com.keymanage.feature.auth.AuthViewFactory
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.main.MainBuilder
import com.keymanage.feature.main.MainDependency
import com.keymanage.feature.main.MainViewFactory
import com.keymanage.feature.settings.SettingsViewFactory
import com.keymanage.feature.unlock.BiometricUnlock
import com.keymanage.feature.unlock.UnlockKeySession
import com.keymanage.feature.unlock.UnlockBuilder
import com.keymanage.feature.unlock.UnlockDependency
import com.keymanage.feature.unlock.UnlockViewFactory
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
    override val biometricUnlock: BiometricUnlock? get() = dependency.biometricUnlock
    override val unlockKeySession: UnlockKeySession? get() = dependency.unlockKeySession
    override val clock: Clock get() = dependency.environment.clock
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
