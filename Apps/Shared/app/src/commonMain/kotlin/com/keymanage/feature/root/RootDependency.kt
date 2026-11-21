package com.keymanage.feature.root

import com.carti.ribs.Dependency
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.auth.AuthViewFactory
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.main.MainViewFactory
import com.keymanage.feature.settings.SettingsViewFactory
import com.keymanage.feature.unlock.UnlockViewFactory
import com.keymanage.feature.vaultlist.VaultListViewFactory

interface RootDependency : Dependency {
    val rootViewFactory: RootViewFactory
    val session: SessionPort
    val environment: AppEnvironment
    val clipboard: ClipboardPort
    val backupFiles: BackupFilesPort
    val randomBytes: RandomBytes

    // 자식 RIB과 그 자식이 쓸 뷰 팩토리
    val authViewFactory: AuthViewFactory
    val unlockViewFactory: UnlockViewFactory
    val mainViewFactory: MainViewFactory
    val vaultListViewFactory: VaultListViewFactory
    val passwordGeneratorViewFactory: PasswordGeneratorViewFactory
    val settingsViewFactory: SettingsViewFactory
}
