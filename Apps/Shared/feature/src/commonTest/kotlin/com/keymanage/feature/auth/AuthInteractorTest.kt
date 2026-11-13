package com.keymanage.feature.auth

import com.carti.ribs.RibCoroutinesConfig
import com.carti.ribs.RibDispatchersProvider
import com.carti.ribs.RibMount
import com.carti.ribs.mount
import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.backup.VaultBackup
import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.session.LockScope
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.SyncChangeListener
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.domain.vault.VaultData
import com.keymanage.feature.SessionPort
import com.keymanage.feature.messages.ErrorMessage
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

// 원본: Apps/Web/src/ui/screens/AuthScreen.test.tsx, SessionRoot의 로그인·가입 흐름, iOS AuthFeatureTests
@OptIn(ExperimentalCoroutinesApi::class)
class AuthInteractorTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var view: FakeAuthView
    private lateinit var session: FakeAuthSession
    private lateinit var listener: FakeAuthListener
    private lateinit var router: AuthRouter
    private val interactor: AuthInteractor get() = router.interactor

    @BeforeTest
    fun setUp() {
        RibCoroutinesConfig.dispatchers = AuthTestDispatchers(dispatcher)
        view = FakeAuthView()
        session = FakeAuthSession()
        listener = FakeAuthListener()
    }

    @AfterTest
    fun tearDown() {
        RibCoroutinesConfig.reset()
    }

    private fun mountAuth(notice: String? = null): RibMount {
        router = AuthBuilder(
            object : AuthDependency {
                override val authViewFactory = AuthViewFactory { view }
                override val session: SessionPort = this@AuthInteractorTest.session
            },
        ).build(listener, notice)
        return router.mount()
    }

    private fun authTest(block: suspend TestScope.(RibMount) -> Unit) = runTest(dispatcher) {
        val mount = mountAuth()
        try {
            block(mount)
        } finally {
            if (mount.isMounted) mount.unmount()
        }
    }

    private fun TestScope.send(action: AuthViewAction) {
        view.listener?.handle(action)
        runCurrent()
    }

    private fun TestScope.fillLogin(email: String = "user@test.com", password: String = "correct horse battery") {
        send(AuthViewAction.UpdateEmail(email))
        send(AuthViewAction.UpdatePassword(password))
    }

    private fun TestScope.fillRegister(password: String, confirmation: String = password) {
        send(AuthViewAction.SwitchMode)
        send(AuthViewAction.UpdateEmail("user@test.com"))
        send(AuthViewAction.UpdatePassword(password))
        send(AuthViewAction.UpdateConfirmation(confirmation))
    }

    @Test
    fun attachRendersLoginModeWithGivenNotice() = runTest(dispatcher) {
        val mount = mountAuth(notice = "로그인이 만료되었습니다. 다시 로그인해 주세요.")

        assertEquals(AuthMode.LOGIN, view.state.mode)
        assertEquals("로그인이 만료되었습니다. 다시 로그인해 주세요.", view.state.notice)
        assertFalse(view.state.isLoading)
        assertNull(view.state.error)
        mount.unmount()
    }

    // --- 안내 갱신 (L4): 화면을 다시 만들지 않고 안내만 바꾼다 ---

    @Test
    fun showNoticeKeepsEmailAndClearsPasswordAndConfirmation() = authTest {
        fillRegister("correct horse battery staple")

        interactor.showNotice("다른 곳에서 비밀번호가 바뀌었습니다.")
        runCurrent()

        assertEquals("다른 곳에서 비밀번호가 바뀌었습니다.", view.state.notice)
        assertEquals("user@test.com", view.state.email)
        assertEquals("", view.state.password)
        assertEquals("", view.state.confirmation)
        // 안내는 다시 로그인하라는 뜻이므로 로그인 모드로 돌린다
        assertEquals(AuthMode.LOGIN, view.state.mode)
    }

    @Test
    fun showNoticeDropsPendingRollbackConfirmationAndItsStoredPassword() = authTest {
        session.loginHandler = { _, _, _ -> throw RevisionRollbackException(5, 3) }
        fillLogin()
        send(AuthViewAction.Submit)
        assertTrue(view.state.isConfirmingRollback)

        interactor.showNotice("로그인이 만료되었습니다. 다시 로그인해 주세요.")
        runCurrent()
        send(AuthViewAction.AcceptRollback)

        assertFalse(view.state.isConfirmingRollback)
        // 안내 뒤에는 보관하던 비밀번호로 다시 보내지 않는다
        assertEquals(1, session.logins.size)
    }

    @Test
    fun viewListenerIsSetWhileActiveAndClearedAfterDetach() = authTest { mount ->
        assertTrue(view.listener != null)

        mount.unmount()

        assertNull(view.listener)
    }

    @Test
    fun loginSendsCredentialsAndNotifiesParentOnSuccess() = authTest {
        fillLogin()

        send(AuthViewAction.Submit)

        assertEquals(listOf(FakeAuthSession.LoginCall("user@test.com", "correct horse battery", false)), session.logins)
        assertEquals(1, listener.completions)
        assertFalse(view.state.isLoading)
        assertEquals("", view.state.password)
    }

    @Test
    fun loginShowsLoadingUntilSessionResponds() = authTest {
        val gate = CompletableDeferred<Unit>()
        session.loginHandler = { _, _, _ -> gate.await() }
        fillLogin()

        send(AuthViewAction.Submit)

        assertTrue(view.state.isLoading)
        assertEquals(0, listener.completions)

        gate.complete(Unit)
        runCurrent()

        assertFalse(view.state.isLoading)
        assertEquals(1, listener.completions)
    }

    @Test
    fun submitWhileLoadingIsIgnored() = authTest {
        val gate = CompletableDeferred<Unit>()
        session.loginHandler = { _, _, _ -> gate.await() }
        fillLogin()

        send(AuthViewAction.Submit)
        send(AuthViewAction.Submit)

        assertEquals(1, session.logins.size)
        gate.complete(Unit)
    }

    @Test
    fun loginFailureShowsPolicyMessageWithTitleAndKeepsInputs() = authTest {
        session.loginHandler = { _, _, _ -> throw ServerError(401, ServerErrorCode.AUTH_FAILED) }
        fillLogin()

        send(AuthViewAction.Submit)

        assertEquals(
            ErrorMessage(title = "로그인 실패", body = "이메일 또는 마스터 비밀번호가 올바르지 않습니다."),
            view.state.error,
        )
        assertFalse(view.state.isLoading)
        assertEquals("correct horse battery", view.state.password)
        assertEquals(0, listener.completions)
    }

    @Test
    fun unexpectedErrorShowsCommonMessage() = authTest {
        session.loginHandler = { _, _, _ -> throw Exception("stack trace with secret") }
        fillLogin()

        send(AuthViewAction.Submit)

        assertEquals("알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", view.state.error?.body)
    }

    @Test
    fun newSubmitClearsPreviousError() = authTest {
        session.loginHandler = { _, _, _ -> throw NetworkError() }
        fillLogin()
        send(AuthViewAction.Submit)
        assertTrue(view.state.error != null)
        val gate = CompletableDeferred<Unit>()
        session.loginHandler = { _, _, _ -> gate.await() }

        send(AuthViewAction.Submit)

        assertNull(view.state.error)
        gate.complete(Unit)
    }

    @Test
    fun submitDismissesNotice() = runTest(dispatcher) {
        val mount = mountAuth(notice = "안내")

        view.listener?.handle(AuthViewAction.Submit)
        runCurrent()

        assertNull(view.state.notice)
        mount.unmount()
    }

    @Test
    fun rollbackAsksForConfirmationInsteadOfShowingError() = authTest {
        session.loginHandler = { _, _, accept -> if (!accept) throw RevisionRollbackException(5, 3) }
        fillLogin()

        send(AuthViewAction.Submit)

        assertTrue(view.state.isConfirmingRollback)
        assertNull(view.state.error)
        assertFalse(view.state.isLoading)
        assertEquals(0, listener.completions)
    }

    @Test
    fun acceptingRollbackRetriesWithAcceptRollbackAndSameCredentials() = authTest {
        session.loginHandler = { _, _, accept -> if (!accept) throw RevisionRollbackException(5, 3) }
        fillLogin()
        send(AuthViewAction.Submit)

        send(AuthViewAction.AcceptRollback)

        assertEquals(
            listOf(
                FakeAuthSession.LoginCall("user@test.com", "correct horse battery", false),
                FakeAuthSession.LoginCall("user@test.com", "correct horse battery", true),
            ),
            session.logins,
        )
        assertFalse(view.state.isConfirmingRollback)
        assertEquals(1, listener.completions)
    }

    @Test
    fun retryUsesCredentialsOfOriginalRequestEvenIfInputsChangedMeanwhile() = authTest {
        session.loginHandler = { _, _, accept -> if (!accept) throw RevisionRollbackException(5, 3) }
        fillLogin()
        send(AuthViewAction.Submit)
        send(AuthViewAction.UpdatePassword("changed after prompt"))

        send(AuthViewAction.AcceptRollback)

        assertEquals("correct horse battery", session.logins.last().password)
    }

    @Test
    fun cancellingRollbackHidesPromptWithoutRetry() = authTest {
        session.loginHandler = { _, _, _ -> throw RevisionRollbackException(5, 3) }
        fillLogin()
        send(AuthViewAction.Submit)

        send(AuthViewAction.CancelRollback)

        assertFalse(view.state.isConfirmingRollback)
        assertEquals(1, session.logins.size)
        // 취소 뒤 수락 요청이 들어와도 보낼 요청이 없다
        send(AuthViewAction.AcceptRollback)
        assertEquals(1, session.logins.size)
    }

    @Test
    fun rollbackAcceptedButFailsAgainShowsError() = authTest {
        session.loginHandler = { _, _, accept ->
            if (accept) throw NetworkError() else throw RevisionRollbackException(5, 3)
        }
        fillLogin()
        send(AuthViewAction.Submit)

        send(AuthViewAction.AcceptRollback)

        assertFalse(view.state.isConfirmingRollback)
        assertEquals("서버에 연결할 수 없습니다. 네트워크 연결을 확인해 주세요.", view.state.error?.body)
    }

    @Test
    fun switchingModeResetsConfirmationNoticeAndFeedback() = runTest(dispatcher) {
        val mount = mountAuth(notice = "안내")
        session.loginHandler = { _, _, _ -> throw NetworkError() }
        view.listener?.handle(AuthViewAction.UpdateEmail("user@test.com"))
        view.listener?.handle(AuthViewAction.UpdatePassword("abcdefghijkl"))
        view.listener?.handle(AuthViewAction.UpdateConfirmation("abc"))
        view.listener?.handle(AuthViewAction.Submit)
        runCurrent()
        assertTrue(view.state.error != null)

        view.listener?.handle(AuthViewAction.SwitchMode)

        assertEquals(AuthMode.REGISTER, view.state.mode)
        assertEquals("", view.state.confirmation)
        assertNull(view.state.notice)
        assertNull(view.state.error)
        // 비밀번호와 이메일은 남긴다
        assertEquals("abcdefghijkl", view.state.password)
        assertEquals("user@test.com", view.state.email)

        view.listener?.handle(AuthViewAction.SwitchMode)
        assertEquals(AuthMode.LOGIN, view.state.mode)
        mount.unmount()
    }

    @Test
    fun switchingModeWhileLoadingIsIgnored() = authTest {
        val gate = CompletableDeferred<Unit>()
        session.loginHandler = { _, _, _ -> gate.await() }
        fillLogin()
        send(AuthViewAction.Submit)

        send(AuthViewAction.SwitchMode)

        assertEquals(AuthMode.LOGIN, view.state.mode)
        gate.complete(Unit)
    }

    @Test
    fun switchingModeClearsRollbackPrompt() = authTest {
        session.loginHandler = { _, _, _ -> throw RevisionRollbackException(5, 3) }
        fillLogin()
        send(AuthViewAction.Submit)

        send(AuthViewAction.SwitchMode)
        send(AuthViewAction.AcceptRollback)

        assertFalse(view.state.isConfirmingRollback)
        assertEquals(1, session.logins.size)
    }

    @Test
    fun registerChecksLengthBeforeCallingSession() = authTest {
        fillRegister(password = "short")

        send(AuthViewAction.Submit)

        assertEquals("마스터 비밀번호는 12자 이상이어야 합니다.", view.state.error?.body)
        assertNull(view.state.error?.title)
        assertTrue(session.registers.isEmpty())
        assertFalse(view.state.isLoading)
    }

    @Test
    fun registerChecksConfirmationBeforeCallingSession() = authTest {
        fillRegister(password = "abcdefghijkl", confirmation = "abcdefghijkm")

        send(AuthViewAction.Submit)

        assertEquals("비밀번호 확인이 일치하지 않습니다.", view.state.error?.body)
        assertTrue(session.registers.isEmpty())
    }

    @Test
    fun registerSendsCredentialsAndNotifiesParentOnSuccess() = authTest {
        fillRegister(password = "correct horse battery")

        send(AuthViewAction.Submit)

        assertEquals(listOf(FakeAuthSession.RegisterCall("user@test.com", "correct horse battery")), session.registers)
        assertTrue(session.logins.isEmpty())
        assertEquals(1, listener.completions)
        assertEquals("", view.state.password)
        assertEquals("", view.state.confirmation)
    }

    @Test
    fun weakPasswordDoesNotBlockRegistration() = authTest {
        fillRegister(password = "aaaaaaaaaaaa")

        assertTrue(view.state.showsWeakPasswordWarning)
        send(AuthViewAction.Submit)

        assertEquals(listOf(FakeAuthSession.RegisterCall("user@test.com", "aaaaaaaaaaaa")), session.registers)
    }

    @Test
    fun registerFailureShowsPolicyMessage() = authTest {
        session.registerHandler = { _, _ -> throw ServerError(409, ServerErrorCode.EMAIL_ALREADY_REGISTERED) }
        fillRegister(password = "correct horse battery")

        send(AuthViewAction.Submit)

        assertEquals("이미 가입된 이메일입니다.", view.state.error?.body)
        assertEquals(0, listener.completions)
        assertEquals("correct horse battery", view.state.password)
    }

    @Test
    fun strengthGaugeIsHiddenInLoginModeAndForEmptyPassword() = authTest {
        send(AuthViewAction.UpdatePassword("Abcdefgh12"))
        assertFalse(view.state.showsStrengthGauge)
        assertFalse(view.state.showsWeakPasswordWarning)

        send(AuthViewAction.UpdatePassword(""))
        send(AuthViewAction.SwitchMode)
        assertFalse(view.state.showsStrengthGauge)
    }

    @Test
    fun score59ShowsGaugeAndWeakWarning() = authTest {
        send(AuthViewAction.SwitchMode)
        send(AuthViewAction.UpdatePassword("Abcdefgh12"))

        assertTrue(view.state.showsStrengthGauge)
        assertEquals(59, view.state.passwordStrengthScore)
        assertTrue(view.state.showsWeakPasswordWarning)
    }

    @Test
    fun score60ShowsGaugeWithoutWarning() = authTest {
        send(AuthViewAction.SwitchMode)
        send(AuthViewAction.UpdatePassword("abcdefghijk한"))

        assertTrue(view.state.showsStrengthGauge)
        assertEquals(60, view.state.passwordStrengthScore)
        assertFalse(view.state.showsWeakPasswordWarning)
    }

    @Test
    fun detachWhileLoadingCancelsRequestAndDoesNotNotifyParent() = authTest { mount ->
        val gate = CompletableDeferred<Unit>()
        session.loginHandler = { _, _, _ -> gate.await() }
        fillLogin()
        send(AuthViewAction.Submit)

        mount.unmount()
        gate.complete(Unit)
        runCurrent()

        assertEquals(0, listener.completions)
    }

    @Test
    fun actionsAfterDetachAreIgnored() = authTest { mount ->
        val handler = view.listener
        mount.unmount()

        handler?.handle(AuthViewAction.UpdateEmail("late@test.com"))

        assertTrue(session.logins.isEmpty())
    }
}

private class AuthTestDispatchers(dispatcher: CoroutineDispatcher) : RibDispatchersProvider {
    override val Main: CoroutineDispatcher = dispatcher
    override val MainImmediate: CoroutineDispatcher = dispatcher
    override val Default: CoroutineDispatcher = dispatcher
    override val Unconfined: CoroutineDispatcher = dispatcher
}

private class FakeAuthView : AuthViewContract {
    override var listener: AuthPresentableListener? = null
    val states = mutableListOf<AuthViewState>()
    val state: AuthViewState get() = states.last()

    override fun render(state: AuthViewState) {
        states += state
    }
}

private class FakeAuthListener : AuthListener {
    var completions = 0

    override fun authDidComplete() {
        completions += 1
    }
}

/** 로그인·가입만 쓴다. 나머지는 이 RIB이 부르지 않으므로 호출되면 실패한다. */
private class FakeAuthSession : SessionPort {
    data class LoginCall(val email: String, val password: String, val acceptRollback: Boolean)
    data class RegisterCall(val email: String, val password: String)

    val logins = mutableListOf<LoginCall>()
    val registers = mutableListOf<RegisterCall>()
    var loginHandler: suspend (String, String, Boolean) -> Unit = { _, _, _ -> }
    var registerHandler: suspend (String, String) -> Unit = { _, _ -> }

    override suspend fun login(email: String, password: String, acceptRollback: Boolean) {
        logins += LoginCall(email, password, acceptRollback)
        loginHandler(email, password, acceptRollback)
    }

    override suspend fun register(email: String, password: String) {
        registers += RegisterCall(email, password)
        registerHandler(email, password)
    }

    override fun state(): SessionState = SessionState.SignedOut
    override fun lock(scope: LockScope) = unused()
    override suspend fun unlock(password: String, acceptRollback: Boolean): Boolean = unused()
    override suspend fun reconnect(password: String, acceptRollback: Boolean) = unused()
    override suspend fun save(data: VaultData) = unused()
    override suspend fun exportBackup(now: Instant, calendar: LocalCalendar): BackupFile = unused()
    override suspend fun restoreBackup(backup: VaultBackup, password: String, now: Instant): Int = unused()
    override suspend fun changeMasterPassword(currentPassword: String, newPassword: String) = unused()
    override suspend fun deleteAccount(password: String) = unused()
    override suspend fun logout() = unused()
    override suspend fun pendingOnDevice(): Boolean = unused()
    override fun onRemoteChange(listener: () -> Unit): () -> Unit = unused()
    override fun onLock(listener: () -> Unit): () -> Unit = unused()
    override fun onUnlock(listener: () -> Unit): () -> Unit = unused()
    override fun onSyncChange(listener: SyncChangeListener): () -> Unit = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("Auth RIB이 부르지 않는 세션 기능")
}
