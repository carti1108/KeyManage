package com.keymanage.feature.unlock

import com.carti.ribs.RibCoroutinesConfig
import com.carti.ribs.RibDispatchersProvider
import com.carti.ribs.RibMount
import com.carti.ribs.mount
import com.keymanage.domain.auth.MasterPasswordChangedException
import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.backup.VaultBackup
import com.keymanage.domain.device.DeviceStorageException
import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.session.LockScope
import com.keymanage.domain.session.SessionExpiredException
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.SyncChangeListener
import com.keymanage.domain.session.WrongMasterPasswordException
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.domain.vault.VaultData
import com.keymanage.feature.SessionPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.time.Instant

// 원본: Apps/Web/src/ui/screens/UnlockScreen.tsx, SessionRoot의 unlock·logout 흐름, iOS UnlockTests
@OptIn(ExperimentalCoroutinesApi::class)
class UnlockInteractorTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var view: FakeUnlockView
    private lateinit var session: FakeUnlockSession
    private lateinit var listener: FakeUnlockListener

    @BeforeTest
    fun setUp() {
        RibCoroutinesConfig.dispatchers = UnlockTestDispatchers(dispatcher)
        view = FakeUnlockView()
        session = FakeUnlockSession()
        listener = FakeUnlockListener()
    }

    @AfterTest
    fun tearDown() {
        RibCoroutinesConfig.reset()
    }

    private fun mountUnlock(): RibMount {
        val router = UnlockBuilder(
            object : UnlockDependency {
                override val unlockViewFactory = UnlockViewFactory { view }
                override val session: SessionPort = this@UnlockInteractorTest.session
            },
        ).build(listener)
        return router.mount()
    }

    private fun unlockTest(block: suspend TestScope.(RibMount) -> Unit) = runTest(dispatcher) {
        val mount = mountUnlock()
        try {
            block(mount)
        } finally {
            if (mount.isMounted) mount.unmount()
        }
    }

    private fun TestScope.send(action: UnlockViewAction) {
        view.listener?.handle(action)
        runCurrent()
    }

    private fun TestScope.typeAndUnlock(password: String = "correct horse battery") {
        send(UnlockViewAction.UpdatePassword(password))
        send(UnlockViewAction.Unlock)
    }

    @Test
    fun attachRendersEmailOfLockedAccountWithoutBiometrics() = unlockTest {
        assertEquals("user@test.com", view.state.email)
        assertFalse(view.state.isBiometricsAvailable)
        assertFalse(view.state.isLoading)
        assertNull(view.state.error)
        assertEquals("", view.state.password)
    }

    @Test
    fun viewListenerIsSetWhileActiveAndClearedAfterDetach() = unlockTest { mount ->
        assertTrue(view.listener != null)

        mount.unmount()

        assertNull(view.listener)
    }

    @Test
    fun unlockSendsPasswordAndReportsOnlineResult() = unlockTest {
        session.unlockResult = false

        typeAndUnlock()

        assertEquals(listOf(FakeUnlockSession.UnlockCall("correct horse battery", false)), session.unlocks)
        assertEquals(listOf(false), listener.unlocked)
        assertEquals("", view.state.password)
        assertFalse(view.state.isLoading)
    }

    @Test
    fun unlockReportsOfflineWhenOpenedFromDeviceRecord() = unlockTest {
        session.unlockResult = true

        typeAndUnlock()

        assertEquals(listOf(true), listener.unlocked)
    }

    @Test
    fun unlockShowsLoadingUntilSessionResponds() = unlockTest {
        val gate = CompletableDeferred<Boolean>()
        session.unlockHandler = { _, _ -> gate.await() }

        typeAndUnlock()

        assertTrue(view.state.isLoading)
        assertTrue(listener.unlocked.isEmpty())

        gate.complete(false)
        runCurrent()

        assertFalse(view.state.isLoading)
        assertEquals(listOf(false), listener.unlocked)
    }

    @Test
    fun unlockWhileLoadingIsIgnored() = unlockTest {
        val gate = CompletableDeferred<Boolean>()
        session.unlockHandler = { _, _ -> gate.await() }
        typeAndUnlock()

        send(UnlockViewAction.Unlock)

        assertEquals(1, session.unlocks.size)
        gate.complete(false)
    }

    @Test
    fun wrongPasswordShowsMessageAndClearsInput() = unlockTest {
        session.unlockHandler = { _, _ -> throw WrongMasterPasswordException() }

        typeAndUnlock("wrong password!!")

        assertEquals("마스터 비밀번호가 올바르지 않습니다.", view.state.error?.body)
        assertEquals("", view.state.password)
        assertFalse(view.state.isLoading)
        assertTrue(listener.unlocked.isEmpty())
    }

    @Test
    fun otherErrorsShowPolicyMessage() = unlockTest {
        session.unlockHandler = { _, _ -> throw NetworkError() }

        typeAndUnlock()

        assertEquals("서버에 연결할 수 없습니다. 네트워크 연결을 확인해 주세요.", view.state.error?.body)
        assertTrue(listener.signInRequests.isEmpty())
    }

    @Test
    fun newAttemptClearsPreviousError() = unlockTest {
        session.unlockHandler = { _, _ -> throw WrongMasterPasswordException() }
        typeAndUnlock()
        assertTrue(view.state.error != null)
        val gate = CompletableDeferred<Boolean>()
        session.unlockHandler = { _, _ -> gate.await() }

        typeAndUnlock("second try password")

        assertNull(view.state.error)
        gate.complete(false)
    }

    @Test
    fun sessionExpiryAsksParentToShowSignInWithNotice() = unlockTest {
        session.unlockHandler = { _, _ -> throw SessionExpiredException() }

        typeAndUnlock()

        assertEquals(listOf("로그인이 만료되었습니다. 다시 로그인해 주세요."), listener.signInRequests)
        assertNull(view.state.error)
        assertTrue(listener.unlocked.isEmpty())
        assertEquals("", view.state.password)
    }

    @Test
    fun passwordChangedOnAnotherDeviceAsksParentToShowSignInWithNotice() = unlockTest {
        session.unlockHandler = { _, _ -> throw MasterPasswordChangedException() }

        typeAndUnlock()

        assertEquals(listOf("마스터 비밀번호가 바뀌었습니다. 새 비밀번호로 로그인해 주세요."), listener.signInRequests)
    }

    @Test
    fun rollbackAsksForConfirmationAndClearsInput() = unlockTest {
        session.unlockHandler = { _, accept -> if (!accept) throw RevisionRollbackException(5, 3) else false }

        typeAndUnlock()

        assertTrue(view.state.isConfirmingRollback)
        assertNull(view.state.error)
        assertEquals("", view.state.password)
        assertFalse(view.state.isLoading)
        assertTrue(listener.unlocked.isEmpty())
    }

    @Test
    fun acceptingRollbackRetriesWithOriginalPasswordAndAcceptRollback() = unlockTest {
        session.unlockHandler = { _, accept -> if (!accept) throw RevisionRollbackException(5, 3) else true }
        typeAndUnlock()

        send(UnlockViewAction.AcceptRollback)

        assertEquals(
            listOf(
                FakeUnlockSession.UnlockCall("correct horse battery", false),
                FakeUnlockSession.UnlockCall("correct horse battery", true),
            ),
            session.unlocks,
        )
        assertFalse(view.state.isConfirmingRollback)
        assertEquals(listOf(true), listener.unlocked)
    }

    @Test
    fun cancellingRollbackHidesPromptWithoutRetry() = unlockTest {
        session.unlockHandler = { _, _ -> throw RevisionRollbackException(5, 3) }
        typeAndUnlock()

        send(UnlockViewAction.CancelRollback)
        send(UnlockViewAction.AcceptRollback)

        assertFalse(view.state.isConfirmingRollback)
        assertEquals(1, session.unlocks.size)
    }

    @Test
    fun logoutWithoutPendingChangesLogsOutImmediately() = unlockTest {
        session.pendingOnDevice = false

        send(UnlockViewAction.RequestLogout)

        assertEquals(1, session.logoutCalls)
        assertFalse(view.state.isConfirmingLogout)
        assertEquals(listOf<String?>(null), listener.loggedOut)
    }

    @Test
    fun logoutWithPendingSyncInSessionAsksConfirmationFirst() = unlockTest {
        session.pendingSync = true

        send(UnlockViewAction.RequestLogout)

        assertTrue(view.state.isConfirmingLogout)
        assertEquals(0, session.logoutCalls)
        assertEquals(0, session.pendingOnDeviceCalls)
        assertTrue(listener.loggedOut.isEmpty())
    }

    @Test
    fun logoutWithPendingChangesOnDeviceAsksConfirmationFirst() = unlockTest {
        session.pendingOnDevice = true

        send(UnlockViewAction.RequestLogout)

        assertTrue(view.state.isConfirmingLogout)
        assertEquals(0, session.logoutCalls)
    }

    @Test
    fun unreadableDeviceRecordIsTreatedAsPending() = unlockTest {
        session.pendingOnDeviceHandler = { throw IllegalStateException("읽기 실패") }

        send(UnlockViewAction.RequestLogout)

        assertTrue(view.state.isConfirmingLogout)
        assertEquals(0, session.logoutCalls)
    }

    @Test
    fun confirmingLogoutLogsOut() = unlockTest {
        session.pendingSync = true
        send(UnlockViewAction.RequestLogout)

        send(UnlockViewAction.ConfirmLogout)

        assertEquals(1, session.logoutCalls)
        assertFalse(view.state.isConfirmingLogout)
        assertEquals(listOf<String?>(null), listener.loggedOut)
    }

    @Test
    fun cancellingLogoutKeepsLockedScreen() = unlockTest {
        session.pendingSync = true
        send(UnlockViewAction.RequestLogout)

        send(UnlockViewAction.CancelLogout)
        send(UnlockViewAction.ConfirmLogout)

        assertFalse(view.state.isConfirmingLogout)
        assertEquals(0, session.logoutCalls)
        assertTrue(listener.loggedOut.isEmpty())
    }

    @Test
    fun logoutRequestWhileCheckingDeviceRecordIsIgnored() = unlockTest {
        val gate = CompletableDeferred<Boolean>()
        session.pendingOnDeviceHandler = { gate.await() }

        send(UnlockViewAction.RequestLogout)
        send(UnlockViewAction.RequestLogout)

        assertEquals(1, session.pendingOnDeviceCalls)
        gate.complete(false)
        runCurrent()
        assertEquals(1, session.logoutCalls)
    }

    @Test
    fun deviceClearFailureIsReportedWithLogoutNotice() = unlockTest {
        session.logoutHandler = { throw DeviceStorageException() }

        send(UnlockViewAction.RequestLogout)

        assertEquals(
            listOf<String?>("로그아웃했지만 이 기기에 남은 볼트 기록을 지우지 못했습니다. 브라우저의 사이트 데이터를 직접 지워 주세요."),
            listener.loggedOut,
        )
    }

    @Test
    fun serverRevocationFailureIsIgnoredBecauseDeviceIsAlreadyLoggedOut() = unlockTest {
        session.logoutHandler = { throw NetworkError() }

        send(UnlockViewAction.RequestLogout)

        assertEquals(listOf<String?>(null), listener.loggedOut)
        assertNull(view.state.error)
    }

    @Test
    fun logoutShowsLoadingAndBlocksUnlock() = unlockTest {
        val gate = CompletableDeferred<Unit>()
        session.logoutHandler = { gate.await() }
        send(UnlockViewAction.RequestLogout)

        assertTrue(view.state.isLoading)
        typeAndUnlock()
        assertTrue(session.unlocks.isEmpty())

        gate.complete(Unit)
    }

    @Test
    fun logoutRequestWhileUnlockingIsIgnored() = unlockTest {
        val gate = CompletableDeferred<Boolean>()
        session.unlockHandler = { _, _ -> gate.await() }
        typeAndUnlock()

        send(UnlockViewAction.RequestLogout)

        assertEquals(0, session.pendingOnDeviceCalls)
        assertEquals(0, session.logoutCalls)
        gate.complete(false)
    }

    @Test
    fun biometricsActionIsIgnoredBecauseNothingIsWiredYet() = unlockTest {
        val before = view.state

        send(UnlockViewAction.UnlockWithBiometrics)

        assertEquals(before, view.state)
        assertTrue(session.unlocks.isEmpty())
        assertTrue(listener.unlocked.isEmpty())
    }

    @Test
    fun detachWhileUnlockingCancelsRequestAndDoesNotNotifyParent() = unlockTest { mount ->
        val gate = CompletableDeferred<Boolean>()
        session.unlockHandler = { _, _ -> gate.await() }
        typeAndUnlock()

        mount.unmount()
        gate.complete(false)
        runCurrent()

        assertTrue(listener.unlocked.isEmpty())
    }

    @Test
    fun actionsAfterDetachAreIgnored() = unlockTest { mount ->
        val handler = view.listener
        mount.unmount()

        handler?.handle(UnlockViewAction.Unlock)
        runCurrent()

        assertTrue(session.unlocks.isEmpty())
    }
}

private class UnlockTestDispatchers(dispatcher: CoroutineDispatcher) : RibDispatchersProvider {
    override val Main: CoroutineDispatcher = dispatcher
    override val MainImmediate: CoroutineDispatcher = dispatcher
    override val Default: CoroutineDispatcher = dispatcher
    override val Unconfined: CoroutineDispatcher = dispatcher
}

private class FakeUnlockView : UnlockPresentable {
    override var listener: UnlockPresentableListener? = null
    val states = mutableListOf<UnlockViewState>()
    val state: UnlockViewState get() = states.last()

    override fun render(state: UnlockViewState) {
        states += state
    }
}

private class FakeUnlockListener : UnlockListener {
    val unlocked = mutableListOf<Boolean>()
    val loggedOut = mutableListOf<String?>()
    val signInRequests = mutableListOf<String>()

    override fun didUnlock(offline: Boolean) {
        unlocked += offline
    }

    override fun didLogout(notice: String?) {
        loggedOut += notice
    }

    override fun didRequireSignIn(notice: String) {
        signInRequests += notice
    }
}

/** 잠금 해제·로그아웃에 쓰는 기능만 구현한다. 나머지는 이 RIB이 부르지 않으므로 호출되면 실패한다. */
private class FakeUnlockSession : SessionPort {
    data class UnlockCall(val password: String, val acceptRollback: Boolean)

    var email = "user@test.com"
    var pendingSync = false
    var pendingOnDevice = false
    var unlockResult = false
    val unlocks = mutableListOf<UnlockCall>()
    var logoutCalls = 0
    var pendingOnDeviceCalls = 0

    var unlockHandler: suspend (String, Boolean) -> Boolean = { _, _ -> unlockResult }
    var logoutHandler: suspend () -> Unit = {}
    var pendingOnDeviceHandler: suspend () -> Boolean = { pendingOnDevice }

    override fun state(): SessionState = SessionState.Locked(email, pendingSync)

    override suspend fun unlock(password: String, acceptRollback: Boolean): Boolean {
        unlocks += UnlockCall(password, acceptRollback)
        return unlockHandler(password, acceptRollback)
    }

    override suspend fun logout() {
        logoutCalls += 1
        logoutHandler()
    }

    override suspend fun pendingOnDevice(): Boolean {
        pendingOnDeviceCalls += 1
        return pendingOnDeviceHandler()
    }

    override suspend fun register(email: String, password: String) = unused()
    override suspend fun login(email: String, password: String, acceptRollback: Boolean) = unused()
    override fun lock(scope: LockScope) = unused()
    override suspend fun reconnect(password: String, acceptRollback: Boolean) = unused()
    override suspend fun save(data: VaultData) = unused()
    override suspend fun exportBackup(now: Instant, calendar: LocalCalendar): BackupFile = unused()
    override suspend fun restoreBackup(backup: VaultBackup, password: String, now: Instant): Int = unused()
    override suspend fun changeMasterPassword(currentPassword: String, newPassword: String) = unused()
    override suspend fun deleteAccount(password: String) = unused()
    override fun onRemoteChange(listener: () -> Unit): () -> Unit = unused()
    override fun onLock(listener: () -> Unit): () -> Unit = unused()
    override fun onUnlock(listener: () -> Unit): () -> Unit = unused()
    override fun onSyncChange(listener: SyncChangeListener): () -> Unit = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("Unlock RIB이 부르지 않는 세션 기능")
}
