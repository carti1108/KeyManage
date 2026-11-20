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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// 볼트 화면 틀이 보이는 두 안내: 복사 뒤 클립보드 자동 삭제(COM-001), 가입 직후 생체 해제 제안(SEC-003 값 표)
@OptIn(ExperimentalCoroutinesApi::class)
class MainNoticesTest {
    private val dispatcher = installTestDispatcher()
    private val session = FakeSession().startUnlocked()
    private val clipboard = FakeBurningClipboard()
    private val views = FakeMainViews()

    @AfterTest
    fun tearDown() {
        RibCoroutinesConfig.reset()
    }

    private fun mountMain(offerBiometricUnlock: Boolean = false): RibMount {
        val dependency = object : MainDependency {
            override val mainViewFactory = MainViewFactory { views.makeMainView() }
            override val vaultListViewFactory = VaultListViewFactory { views.makeVaultListView() }
            override val passwordGeneratorViewFactory = PasswordGeneratorViewFactory { views.makePasswordGeneratorView() }
            override val settingsViewFactory = SettingsViewFactory { views.makeSettingsView() }
            override val session: SessionPort = this@MainNoticesTest.session
            override val clipboard: ClipboardPort = this@MainNoticesTest.clipboard
            override val backupFiles: BackupFilesPort = FakeBackupFiles()
            override val environment: AppEnvironment = FakeEnvironment()
            override val randomBytes: RandomBytes = FakeRandomBytes()
            override fun vaultListBuilder(dependency: VaultListDependency) = VaultListBuilder(dependency)
            override fun passwordGeneratorBuilder(dependency: PasswordGeneratorDependency) = PasswordGeneratorBuilder(dependency)
            override fun settingsBuilder(dependency: SettingsDependency) = SettingsBuilder(dependency)
        }
        return MainBuilder(dependency).build(RecordingMainListener(), offline = false, offerBiometricUnlock = offerBiometricUnlock).mount()
    }

    private fun mainTest(offerBiometricUnlock: Boolean = false, block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        val mount = mountMain(offerBiometricUnlock)
        try {
            block()
        } finally {
            if (mount.isMounted) mount.unmount()
        }
    }

    private val state get() = views.mainViews.single().state

    private fun TestScope.send(action: MainViewAction) {
        checkNotNull(views.mainViews.single().listener).handle(action)
        runCurrent()
    }

    // --- 클립보드 자동 삭제 안내 (COM-001) ---

    @Test
    fun showsNoClipboardNoticeUntilSomethingIsCopied() = mainTest {
        assertNull(state.clipboardBurnAt)
        assertNull(state.clipboardNotice)
    }

    @Test
    fun afterACopyItShowsTheStandardNoticeAndWhenTheClipboardWillBeWiped() = mainTest {
        clipboard.emit(TEST_NOW + 30.seconds)
        runCurrent()

        assertEquals(TEST_NOW + 30.seconds, state.clipboardBurnAt)
        assertEquals("클립보드 보안 소각", state.clipboardNotice?.title)
        assertEquals("보안을 위해 복사된 내용이 30초 후 클립보드에서 자동 삭제됩니다.", state.clipboardNotice?.body)
        assertEquals(ClipboardBurnNotice.TITLE, state.clipboardNotice?.title)
    }

    @Test
    fun theNoticeGoesAwayWhenTheClipboardIsWipedOrSomethingElseIsCopied() = mainTest {
        clipboard.emit(TEST_NOW + 30.seconds)
        runCurrent()

        clipboard.emit(null)
        runCurrent()

        assertNull(state.clipboardBurnAt)
        assertNull(state.clipboardNotice)
    }

    @Test
    fun aCopyMadeBeforeTheScreenAppearedIsShownRightAway() {
        clipboard.burnAt = TEST_NOW + 12.seconds
        mainTest {
            assertEquals(TEST_NOW + 12.seconds, state.clipboardBurnAt)
        }
    }

    @Test
    fun stopsListeningToTheClipboardWhenTakenDown() = runTest(dispatcher) {
        val mount = mountMain()
        assertEquals(1, clipboard.listenerCount)

        mount.unmount()

        assertEquals(0, clipboard.listenerCount)
    }

    // --- 가입 직후 생체 해제 제안 (SEC-003 값 표) ---

    @Test
    fun offersBiometricUnlockRightAfterSignUpOnADeviceThatSupportsIt() {
        session.biometricAvailable = true
        mainTest(offerBiometricUnlock = true) {
            assertTrue(state.isOfferingBiometricUnlock)
        }
    }

    @Test
    fun theOfferUsesTheStandardWording() {
        assertEquals("생체 인증으로 잠금을 풀까요?", BiometricUnlockMessages.OFFER_TITLE)
    }

    @Test
    fun doesNotOfferAfterAnOrdinarySignIn() {
        session.biometricAvailable = true
        mainTest(offerBiometricUnlock = false) {
            assertFalse(state.isOfferingBiometricUnlock)
        }
    }

    @Test
    fun doesNotOfferWhenTheDeviceHasNoBiometricsOrItIsAlreadyOn() {
        session.biometricAvailable = false
        mainTest(offerBiometricUnlock = true) {
            assertFalse(state.isOfferingBiometricUnlock)
        }
    }

    @Test
    fun doesNotOfferWhenItIsAlreadyOn() {
        session.biometricAvailable = true
        session.biometricEnabled = true
        mainTest(offerBiometricUnlock = true) {
            assertFalse(state.isOfferingBiometricUnlock)
        }
    }

    @Test
    fun acceptingTurnsItOnAndClosesTheOffer() {
        session.biometricAvailable = true
        mainTest(offerBiometricUnlock = true) {
            send(MainViewAction.AcceptBiometricOffer)

            assertEquals(1, session.biometricEnableCalls)
            assertTrue(session.biometricEnabled)
            assertFalse(state.isOfferingBiometricUnlock)
            assertNull(state.biometricOfferError)
        }
    }

    @Test
    fun decliningClosesTheOfferWithoutTurningItOn() {
        session.biometricAvailable = true
        mainTest(offerBiometricUnlock = true) {
            send(MainViewAction.DeclineBiometricOffer)

            assertEquals(0, session.biometricEnableCalls)
            assertFalse(state.isOfferingBiometricUnlock)
        }
    }

    @Test
    fun cancellingThePromptLeavesItOffWithoutAMessage() {
        session.biometricAvailable = true
        session.biometricEnableHandler = { throw BiometricError.Cancelled() }
        mainTest(offerBiometricUnlock = true) {
            send(MainViewAction.AcceptBiometricOffer)

            assertFalse(session.biometricEnabled)
            assertFalse(state.isOfferingBiometricUnlock)
            assertNull(state.biometricOfferError)
        }
    }

    @Test
    fun whenItCannotBeTurnedOnItPointsToSettings() {
        session.biometricAvailable = true
        session.biometricEnableHandler = { throw BiometricError.Unavailable() }
        mainTest(offerBiometricUnlock = true) {
            send(MainViewAction.AcceptBiometricOffer)

            assertEquals(BiometricUnlockMessages.OFFER_FAILED, state.biometricOfferError)

            send(MainViewAction.SelectTab(MainTab.SETTINGS))
            assertNull(state.biometricOfferError, "다른 탭으로 가면 지운다")
        }
    }

    @Test
    fun answeringTwiceDoesNothingTheSecondTime() {
        session.biometricAvailable = true
        mainTest(offerBiometricUnlock = true) {
            send(MainViewAction.AcceptBiometricOffer)
            send(MainViewAction.AcceptBiometricOffer)

            assertEquals(1, session.biometricEnableCalls)
        }
    }
}
