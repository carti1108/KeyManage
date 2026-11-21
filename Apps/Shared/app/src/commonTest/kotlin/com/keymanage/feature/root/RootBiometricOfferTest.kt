package com.keymanage.feature.root

import com.carti.ribs.RibCoroutinesConfig
import com.carti.ribs.mount
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.auth.AuthViewAction
import com.keymanage.feature.auth.AuthViewFactory
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.main.MainViewFactory
import com.keymanage.feature.settings.SettingsViewFactory
import com.keymanage.feature.testing.FakeBackupFiles
import com.keymanage.feature.testing.FakeClipboard
import com.keymanage.feature.testing.FakeEnvironment
import com.keymanage.feature.testing.FakeRandomBytes
import com.keymanage.feature.testing.FakeSession
import com.keymanage.feature.testing.FakeViewFactory
import com.keymanage.feature.testing.installTestDispatcher
import com.keymanage.feature.unlock.UnlockViewFactory
import com.keymanage.feature.vaultlist.VaultListViewFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 가입 직후에만 생체 해제를 켤지 묻는다 (SEC-003 값 표: "가입 직후 켤지 묻는다")
@OptIn(ExperimentalCoroutinesApi::class)
class RootBiometricOfferTest {
    private val dispatcher = installTestDispatcher()
    private val session = FakeSession().apply { biometricAvailable = true }
    private val factory = FakeViewFactory()

    @AfterTest
    fun tearDown() {
        RibCoroutinesConfig.reset()
    }

    private fun rootTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        val dependency = object : RootDependency {
            override val rootViewFactory = RootViewFactory { factory.makeRootView() }
            override val authViewFactory = AuthViewFactory { factory.makeAuthView() }
            override val unlockViewFactory = UnlockViewFactory { factory.makeUnlockView() }
            override val mainViewFactory = MainViewFactory { factory.makeMainView() }
            override val vaultListViewFactory = VaultListViewFactory { factory.makeVaultListView() }
            override val passwordGeneratorViewFactory = PasswordGeneratorViewFactory { factory.makePasswordGeneratorView() }
            override val settingsViewFactory = SettingsViewFactory { factory.makeSettingsView() }
            override val session: SessionPort = this@RootBiometricOfferTest.session
            override val clipboard: ClipboardPort = FakeClipboard()
            override val backupFiles: BackupFilesPort = FakeBackupFiles()
            override val environment: AppEnvironment = FakeEnvironment()
            override val randomBytes: RandomBytes = FakeRandomBytes()
        }
        val mount = RootBuilder(dependency).build().mount()
        try {
            block()
        } finally {
            if (mount.isMounted) mount.unmount()
        }
    }

    private fun TestScope.submit(register: Boolean) {
        val auth = factory.authViews.last().listener!!
        if (register) auth.handle(AuthViewAction.SwitchMode)
        auth.handle(AuthViewAction.UpdateEmail("user@test.com"))
        auth.handle(AuthViewAction.UpdatePassword("correct horse battery"))
        if (register) auth.handle(AuthViewAction.UpdateConfirmation("correct horse battery"))
        auth.handle(AuthViewAction.Submit)
        runCurrent()
    }

    @Test
    fun signingUpOpensTheVaultWithTheBiometricOffer() = rootTest {
        submit(register = true)

        assertTrue(factory.mainViews.single().state.isOfferingBiometricUnlock)
    }

    @Test
    fun signingInOpensTheVaultWithoutTheOffer() = rootTest {
        submit(register = false)

        assertFalse(factory.mainViews.single().state.isOfferingBiometricUnlock)
    }
}
