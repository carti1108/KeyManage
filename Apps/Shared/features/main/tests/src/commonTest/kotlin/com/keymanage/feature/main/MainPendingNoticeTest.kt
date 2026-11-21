package com.keymanage.feature.main

import com.carti.ribs.RibCoroutinesConfig
import com.carti.ribs.RibMount
import com.carti.ribs.mount
import com.keymanage.domain.lock.BiometricError
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.generator.PasswordGeneratorBuilder
import com.keymanage.feature.generator.PasswordGeneratorDependency
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.main.testing.FakeBurningClipboard
import com.keymanage.feature.main.testing.FakeMainViews
import com.keymanage.feature.main.testing.RecordingMainListener
import com.keymanage.feature.messages.BiometricUnlockMessages
import com.keymanage.feature.messages.ClipboardBurnNotice
import com.keymanage.feature.settings.SettingsBuilder
import com.keymanage.feature.settings.SettingsDependency
import com.keymanage.feature.settings.SettingsViewFactory
import com.keymanage.feature.testing.FakeBackupFiles
import com.keymanage.feature.testing.FakeEnvironment
import com.keymanage.feature.testing.FakeRandomBytes
import com.keymanage.feature.testing.FakeSession
import com.keymanage.feature.testing.TEST_NOW
import com.keymanage.feature.testing.installTestDispatcher
import com.keymanage.feature.vaultlist.VaultListBuilder
import com.keymanage.feature.vaultlist.VaultListDependency
import com.keymanage.feature.vaultlist.VaultListViewFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import com.keymanage.feature.messages.OFFLINE_NOTICE


// 네트워크는 연결돼 있고 서버만 닿지 않아 저장이 동기화 대기가 됐을 때도 오프라인 안내를 보인다 (VLT-002)
@OptIn(ExperimentalCoroutinesApi::class)
class MainPendingNoticeTest {
    private val dispatcher = installTestDispatcher()
    private val session = FakeSession().startUnlocked()
    private val views = FakeMainViews()

    @AfterTest
    fun tearDown() {
        RibCoroutinesConfig.reset()
    }

    private fun mountMain(): RibMount {
        val dependency = object : MainDependency {
            override val mainViewFactory = MainViewFactory { views.makeMainView() }
            override val vaultListViewFactory = VaultListViewFactory { views.makeVaultListView() }
            override val passwordGeneratorViewFactory = PasswordGeneratorViewFactory { views.makePasswordGeneratorView() }
            override val settingsViewFactory = SettingsViewFactory { views.makeSettingsView() }
            override val session: SessionPort = this@MainPendingNoticeTest.session
            override val clipboard: ClipboardPort = FakeBurningClipboard()
            override val backupFiles: BackupFilesPort = FakeBackupFiles()
            override val environment: AppEnvironment = FakeEnvironment()
            override val randomBytes: RandomBytes = FakeRandomBytes()
            override fun vaultListBuilder(dependency: VaultListDependency) = VaultListBuilder(dependency)
            override fun passwordGeneratorBuilder(dependency: PasswordGeneratorDependency) = PasswordGeneratorBuilder(dependency)
            override fun settingsBuilder(dependency: SettingsDependency) = SettingsBuilder(dependency)
        }
        return MainBuilder(dependency).build(RecordingMainListener(), offline = false).mount()
    }

    private fun mainTest(block: suspend TestScope.(RibMount) -> Unit) = runTest(dispatcher) {
        val mount = mountMain()
        try {
            block(mount)
        } finally {
            if (mount.isMounted) mount.unmount()
        }
    }

    private val state get() = views.mainViews.single().state

    @Test
    fun 저장이_대기가_되면_오프라인_안내를_보인다() = mainTest {
        session.pendingSync = true
        session.refreshUnlocked()

        session.firePendingChange()
        runCurrent()

        assertEquals(OFFLINE_NOTICE, state.offlineNotice)
    }

    @Test
    fun 재전송으로_대기가_풀리면_안내를_걷는다() = mainTest {
        session.pendingSync = true
        session.refreshUnlocked()
        session.firePendingChange()
        runCurrent()

        session.pendingSync = false
        session.refreshUnlocked()
        session.firePendingChange()
        runCurrent()

        assertNull(state.offlineNotice)
    }

    @Test
    fun 내려가면_대기_알림_구독을_거둔다() = mainTest { mount ->
        mount.unmount()

        assertEquals(0, session.pendingListeners.size)
    }
}
