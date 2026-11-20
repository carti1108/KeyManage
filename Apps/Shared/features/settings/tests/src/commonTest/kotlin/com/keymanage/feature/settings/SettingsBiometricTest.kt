package com.keymanage.feature.settings

import com.carti.ribs.RibCoroutinesConfig
import com.carti.ribs.RibMount
import com.carti.ribs.mount
import com.keymanage.domain.lock.BiometricError
import com.keymanage.feature.messages.BiometricUnlockMessages
import com.keymanage.feature.settings.testing.FakeSettingsBackupFiles
import com.keymanage.feature.settings.testing.FakeSettingsEnvironment
import com.keymanage.feature.settings.testing.FakeSettingsListener
import com.keymanage.feature.settings.testing.FakeSettingsSession
import com.keymanage.feature.settings.testing.FakeSettingsView
import com.keymanage.feature.testing.installTestDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 설정의 생체 해제 켜기·끄기 (SEC-003 규칙 4, 값 표: 기본 꺼짐). 원본: Apps/Android feature/settings의 toggleBiometric
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsBiometricTest {
    private lateinit var dispatcher: TestDispatcher
    private lateinit var session: FakeSettingsSession
    private lateinit var view: FakeSettingsView
    private var mount: RibMount? = null

    @BeforeTest
    fun setUp() {
        dispatcher = installTestDispatcher()
        session = FakeSettingsSession().apply { biometricAvailable = true }
        view = FakeSettingsView()
    }

    @AfterTest
    fun tearDown() {
        mount?.unmount()
        RibCoroutinesConfig.reset()
    }

    private fun attach() {
        val dependency = object : SettingsDependency {
            override val settingsViewFactory = SettingsViewFactory { view }
            override val session = this@SettingsBiometricTest.session
            override val backupFiles = FakeSettingsBackupFiles()
            override val environment = FakeSettingsEnvironment()
        }
        mount = SettingsBuilder(dependency).build(FakeSettingsListener()).mount()
    }

    private val state get() = view.states.last()

    private fun send(action: SettingsViewAction) {
        checkNotNull(view.listener).handle(action)
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun showsTheToggleOffByDefaultWhenTheDeviceSupportsBiometrics() {
        attach()

        assertTrue(state.showsBiometricToggle)
        assertFalse(state.biometricEnabled)
    }

    @Test
    fun hidesTheToggleWhenTheDeviceHasNoBiometrics() {
        session.biometricAvailable = false
        attach()

        assertFalse(state.showsBiometricToggle)
    }

    @Test
    fun showsTheToggleOnWhenAlreadyEnabled() {
        session.biometricEnabled = true
        attach()

        assertTrue(state.biometricEnabled)
    }

    @Test
    fun turningItOnWrapsTheKeyAndShowsItEnabled() {
        attach()

        send(SettingsViewAction.SetBiometricUnlock(true))

        assertEquals(1, session.biometricEnableCalls)
        assertTrue(state.biometricEnabled)
        assertNull(state.biometricError)
        assertFalse(state.inProgress)
    }

    @Test
    fun whileThePromptIsShowingOtherActionsAreBlocked() {
        val gate = CompletableDeferred<Unit>()
        session.biometricEnableGate = gate
        attach()

        send(SettingsViewAction.SetBiometricUnlock(true))
        assertTrue(state.inProgress)
        send(SettingsViewAction.SetBiometricUnlock(true))
        send(SettingsViewAction.StartChangePassword)

        assertEquals(1, session.biometricEnableCalls)
        assertEquals(SettingsModal.NONE, state.modal)
        gate.complete(Unit)
        dispatcher.scheduler.runCurrent()
        assertTrue(state.biometricEnabled)
    }

    @Test
    fun cancellingThePromptLeavesItOffWithoutAnError() {
        session.biometricEnableError = BiometricError.Cancelled()
        attach()

        send(SettingsViewAction.SetBiometricUnlock(true))

        assertFalse(state.biometricEnabled)
        assertNull(state.biometricError)
        assertNull(state.error)
    }

    @Test
    fun whenBiometricsCannotBeUsedItExplainsWhy() {
        session.biometricEnableError = BiometricError.Unavailable()
        attach()

        send(SettingsViewAction.SetBiometricUnlock(true))

        assertFalse(state.biometricEnabled)
        assertEquals(BiometricUnlockMessages.SETTING_UNAVAILABLE, state.biometricError)
    }

    @Test
    fun turningItOffWipesTheWrappedKey() {
        session.biometricEnabled = true
        attach()

        send(SettingsViewAction.SetBiometricUnlock(false))

        assertEquals(1, session.biometricDisableCalls)
        assertFalse(state.biometricEnabled)
    }

    @Test
    fun aLaterSuccessClearsTheEarlierMessage() {
        session.biometricEnableError = BiometricError.Unavailable()
        attach()
        send(SettingsViewAction.SetBiometricUnlock(true))
        session.biometricEnableError = null

        send(SettingsViewAction.SetBiometricUnlock(true))

        assertNull(state.biometricError)
        assertTrue(state.biometricEnabled)
    }

    @Test
    fun settingTheSameValueDoesNothing() {
        attach()

        send(SettingsViewAction.SetBiometricUnlock(false))

        assertEquals(0, session.biometricDisableCalls)
        assertEquals(0, session.biometricEnableCalls)
    }
}
