package com.keymanage.feature.unlock

import com.carti.ribs.RibCoroutinesConfig
import com.carti.ribs.RibMount
import com.carti.ribs.mount
import com.keymanage.domain.auth.MasterPasswordChangedException
import com.keymanage.domain.lock.BiometricError
import com.keymanage.domain.session.SessionExpiredException
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.feature.SessionPort
import com.keymanage.feature.messages.BiometricUnlockMessages
import com.keymanage.feature.messages.PASSWORD_CHANGED
import com.keymanage.feature.testing.installTestDispatcher
import com.keymanage.feature.unlock.testing.FakeUnlockListener
import com.keymanage.feature.unlock.testing.FakeUnlockSession
import com.keymanage.feature.unlock.testing.FakeUnlockView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 잠금 화면의 생체 해제 (SEC-003 규칙 4·8). 원본: Apps/Android feature/unlock의 UnlockInteractor 생체 해제 흐름
@OptIn(ExperimentalCoroutinesApi::class)
class UnlockBiometricTest {
    private lateinit var dispatcher: TestDispatcher
    private lateinit var view: FakeUnlockView
    private lateinit var session: FakeUnlockSession
    private lateinit var listener: FakeUnlockListener

    @BeforeTest
    fun setUp() {
        dispatcher = installTestDispatcher()
        view = FakeUnlockView()
        session = FakeUnlockSession().apply { biometricsUsable = true }
        listener = FakeUnlockListener()
    }

    @AfterTest
    fun tearDown() {
        RibCoroutinesConfig.reset()
    }

    private fun unlockTest(block: suspend TestScope.(RibMount) -> Unit) = runTest(dispatcher) {
        val mount = UnlockBuilder(
            object : UnlockDependency {
                override val unlockViewFactory = UnlockViewFactory { view }
                override val session: SessionPort = this@UnlockBiometricTest.session
            },
        ).build(listener).mount()
        try {
            block(mount)
        } finally {
            mount.unmount()
        }
    }

    private fun TestScope.send(action: UnlockViewAction) {
        checkNotNull(view.listener).handle(action)
        runCurrent()
    }

    @Test
    fun offersBiometricsWhenTheSessionAllowsIt() = unlockTest {
        assertTrue(view.state.isBiometricsAvailable)
    }

    @Test
    fun hidesBiometricsWhenTheSessionDoesNotAllowIt() {
        session.biometricsUsable = false
        unlockTest {
            assertFalse(view.state.isBiometricsAvailable)

            send(UnlockViewAction.UnlockWithBiometrics)

            assertTrue(session.biometricUnlocks.isEmpty())
        }
    }

    @Test
    fun biometricUnlockOpensTheVaultAndTellsTheParent() = unlockTest {
        send(UnlockViewAction.UnlockWithBiometrics)

        assertEquals(listOf(false), session.biometricUnlocks)
        assertEquals(listOf(false), listener.unlocked)
        assertFalse(view.state.isLoading)
    }

    @Test
    fun biometricUnlockReportsOfflineOpen() = unlockTest {
        session.biometricUnlockHandler = { true }

        send(UnlockViewAction.UnlockWithBiometrics)

        assertEquals(listOf(true), listener.unlocked)
    }

    @Test
    fun showsLoadingWhileThePromptIsShowingAndIgnoresASecondTap() = unlockTest {
        val gate = CompletableDeferred<Boolean>()
        session.biometricUnlockHandler = { gate.await() }

        send(UnlockViewAction.UnlockWithBiometrics)
        assertTrue(view.state.isLoading)
        send(UnlockViewAction.UnlockWithBiometrics)
        send(UnlockViewAction.Unlock)

        assertEquals(1, session.biometricUnlocks.size)
        assertTrue(session.unlocks.isEmpty())
        gate.complete(false)
        runCurrent()
        assertEquals(listOf(false), listener.unlocked)
    }

    @Test
    fun cancelledPromptShowsNoErrorAndKeepsTheButton() = unlockTest {
        session.biometricUnlockHandler = { throw BiometricError.Cancelled() }

        send(UnlockViewAction.UnlockWithBiometrics)

        assertNull(view.state.error)
        assertFalse(view.state.isLoading)
        assertTrue(view.state.isBiometricsAvailable)
        assertTrue(listener.unlocked.isEmpty())
    }

    @Test
    fun afterFourteenDaysItAsksForTheMasterPasswordAndHidesTheButton() = unlockTest {
        session.biometricUnlockHandler = {
            session.biometricsUsable = false
            throw BiometricError.Expired()
        }

        send(UnlockViewAction.UnlockWithBiometrics)

        assertEquals(BiometricUnlockMessages.EXPIRED, view.state.error?.body)
        assertFalse(view.state.isBiometricsAvailable)
    }

    @Test
    fun invalidatedKeyAsksForTheMasterPasswordAndHidesTheButton() = unlockTest {
        session.biometricUnlockHandler = {
            session.biometricsUsable = false
            throw BiometricError.Invalidated()
        }

        send(UnlockViewAction.UnlockWithBiometrics)

        assertEquals(BiometricUnlockMessages.INVALIDATED, view.state.error?.body)
        assertFalse(view.state.isBiometricsAvailable)
    }

    @Test
    fun unavailableBiometricsShowsItsMessage() = unlockTest {
        session.biometricUnlockHandler = { throw BiometricError.Unavailable() }

        send(UnlockViewAction.UnlockWithBiometrics)

        assertEquals(BiometricUnlockMessages.UNAVAILABLE, view.state.error?.body)
    }

    @Test
    fun rollbackAsksFirstAndRetriesWithBiometricsWhenAccepted() = unlockTest {
        session.biometricUnlockHandler = { acceptRollback ->
            if (!acceptRollback) throw RevisionRollbackException(lastSeenRevision = 7, serverRevision = 5)
            false
        }

        send(UnlockViewAction.UnlockWithBiometrics)
        assertTrue(view.state.isConfirmingRollback)
        assertTrue(listener.unlocked.isEmpty())

        send(UnlockViewAction.AcceptRollback)

        assertEquals(listOf(false, true), session.biometricUnlocks)
        assertTrue(session.unlocks.isEmpty(), "비밀번호 없이 같은 방법으로 다시 연다")
        assertEquals(listOf(false), listener.unlocked)
    }

    @Test
    fun cancellingTheRollbackWarningStaysLocked() = unlockTest {
        session.biometricUnlockHandler = { throw RevisionRollbackException(lastSeenRevision = 7, serverRevision = 5) }
        send(UnlockViewAction.UnlockWithBiometrics)

        send(UnlockViewAction.CancelRollback)
        send(UnlockViewAction.AcceptRollback)

        assertFalse(view.state.isConfirmingRollback)
        assertEquals(1, session.biometricUnlocks.size)
    }

    @Test
    fun sessionExpiryAndPasswordChangeSendToSignIn() = unlockTest {
        session.biometricUnlockHandler = { throw SessionExpiredException() }
        send(UnlockViewAction.UnlockWithBiometrics)
        session.biometricUnlockHandler = { throw MasterPasswordChangedException() }
        send(UnlockViewAction.UnlockWithBiometrics)

        assertEquals(2, listener.signInRequests.size)
        assertEquals(PASSWORD_CHANGED, listener.signInRequests.last())
    }

    @Test
    fun aFailedPasswordUnlockRefreshesWhetherBiometricsAreOffered() = unlockTest {
        session.unlockHandler = { _, _ ->
            session.biometricsUsable = false
            throw com.keymanage.domain.session.WrongMasterPasswordException()
        }
        send(UnlockViewAction.UpdatePassword("wrong"))

        send(UnlockViewAction.Unlock)

        assertFalse(view.state.isBiometricsAvailable)
    }
}
