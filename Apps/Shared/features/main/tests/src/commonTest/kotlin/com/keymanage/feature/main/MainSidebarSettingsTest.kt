package com.keymanage.feature.main

import com.carti.ribs.RibCoroutinesConfig
import com.carti.ribs.RibMount
import com.carti.ribs.mount
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.main.testing.FakeMainViews
import com.keymanage.feature.main.testing.RecordingMainListener
import com.keymanage.feature.settings.SettingsViewFactory
import com.keymanage.feature.testing.FakeBackupFiles
import com.keymanage.feature.testing.FakeClipboard
import com.keymanage.feature.testing.FakeEnvironment
import com.keymanage.feature.testing.FakeRandomBytes
import com.keymanage.feature.testing.FakeSession
import com.keymanage.feature.testing.installTestDispatcher
import com.keymanage.feature.vaultlist.VaultListViewFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// 데스크톱 사이드바 아래 상자(자동 잠금·백업·비밀번호 변경·계정 삭제)는 설정 RIB 화면이다.
// 사이드바를 쓰는 화면(macOS)에서만 설정 RIB을 처음부터 붙이고, 설정 탭은 같은 RIB을 다시 쓴다
@OptIn(ExperimentalCoroutinesApi::class)
class MainSidebarSettingsTest {
    private val dispatcher = installTestDispatcher()
    private val session = FakeSession().startUnlocked()
    private val factory = FakeMainViews()

    @AfterTest
    fun tearDown() {
        RibCoroutinesConfig.reset()
    }

    private fun mountMain(): RibMount {
        val dependency = object : MainDependency {
            override val mainViewFactory = MainViewFactory { factory.makeMainView() }
            override val vaultListViewFactory = VaultListViewFactory { factory.makeVaultListView() }
            override val passwordGeneratorViewFactory = PasswordGeneratorViewFactory { factory.makePasswordGeneratorView() }
            override val settingsViewFactory = SettingsViewFactory { factory.makeSettingsView() }
            override val session: SessionPort = this@MainSidebarSettingsTest.session
            override val clipboard: ClipboardPort = FakeClipboard()
            override val backupFiles: BackupFilesPort = FakeBackupFiles()
            override val environment: AppEnvironment = FakeEnvironment()
            override val randomBytes: RandomBytes = FakeRandomBytes()
            override fun vaultListBuilder(dependency: com.keymanage.feature.vaultlist.VaultListDependency) =
                com.keymanage.feature.vaultlist.VaultListBuilder(dependency)
            override fun passwordGeneratorBuilder(dependency: com.keymanage.feature.generator.PasswordGeneratorDependency) =
                com.keymanage.feature.generator.PasswordGeneratorBuilder(dependency)
            override fun settingsBuilder(dependency: com.keymanage.feature.settings.SettingsDependency) =
                com.keymanage.feature.settings.SettingsBuilder(dependency)
        }
        return MainBuilder(dependency).build(RecordingMainListener(), false).mount()
    }

    private fun mainTest(usesSidebar: Boolean, block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        factory.usesSidebarSettings = usesSidebar
        val mount = mountMain()
        try {
            runCurrent()
            block()
        } finally {
            if (mount.isMounted) mount.unmount()
        }
    }

    private val view get() = factory.mainViews.single()

    private fun TestScope.send(action: MainViewAction) {
        assertNotNull(view.listener).handle(action)
        runCurrent()
    }

    @Test
    fun 사이드바를_쓰면_처음부터_설정_화면을_사이드바에_붙인다() = mainTest(usesSidebar = true) {
        assertEquals(1, factory.settingsViews.size)
        assertSame(factory.settingsViews.single(), view.sidebarSettings.single())
        assertEquals(MainTab.VAULT, view.state.selectedTab)
        assertEquals(1, view.visible.size, "본문에는 볼트 화면만 보인다")
    }

    @Test
    fun 사이드바를_쓰면_설정_탭은_사이드바의_설정_RIB을_다시_쓴다() = mainTest(usesSidebar = true) {
        send(MainViewAction.SelectTab(MainTab.SETTINGS))

        assertEquals(1, factory.settingsViews.size, "설정 RIB을 하나 더 만들지 않는다")
        assertSame(factory.settingsViews.single(), view.visible.single())
    }

    @Test
    fun 사이드바를_쓰지_않으면_설정은_탭을_고를_때_붙인다() = mainTest(usesSidebar = false) {
        assertEquals(0, factory.settingsViews.size)
        assertTrue(view.sidebarSettings.isEmpty())
    }
}
