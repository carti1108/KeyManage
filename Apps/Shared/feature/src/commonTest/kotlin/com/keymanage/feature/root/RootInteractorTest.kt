package com.keymanage.feature.root

import com.carti.ribs.RibCoroutinesConfig
import com.carti.ribs.RibMount
import com.carti.ribs.mount
import com.keymanage.domain.auth.MasterPasswordChangedException
import com.keymanage.domain.device.DeviceStorageException
import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.session.SessionExpiredException
import com.keymanage.domain.session.WrongMasterPasswordException
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.auth.AuthViewAction
import com.keymanage.feature.auth.AuthViewFactory
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.main.MainTab
import com.keymanage.feature.main.MainViewAction
import com.keymanage.feature.main.MainViewFactory
import com.keymanage.feature.messages.ACCOUNT_DELETED
import com.keymanage.feature.messages.LOGOUT_CLEAR_FAILED
import com.keymanage.feature.messages.OFFLINE_NOTICE
import com.keymanage.feature.messages.PASSWORD_CHANGED
import com.keymanage.feature.settings.SettingsViewAction
import com.keymanage.feature.settings.SettingsViewFactory
import com.keymanage.feature.testing.FakeBackupFiles
import com.keymanage.feature.testing.FakeClipboard
import com.keymanage.feature.testing.FakeEnvironment
import com.keymanage.feature.testing.FakeRandomBytes
import com.keymanage.feature.testing.FakeSession
import com.keymanage.feature.testing.FakeViewFactory
import com.keymanage.feature.testing.installTestDispatcher
import com.keymanage.feature.unlock.UnlockViewAction
import com.keymanage.feature.unlock.UnlockViewFactory
import com.keymanage.feature.vaultlist.VaultListViewFactory
import com.keymanage.domain.platform.RandomBytes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// 세션 상태에 따라 로그인·잠금·볼트 화면을 고른다. 원본: Apps/Web/src/ui/session/SessionRoot.tsx, iOS Features/Root
@OptIn(ExperimentalCoroutinesApi::class)
class RootInteractorTest {
    private val dispatcher = installTestDispatcher()
    private val session = FakeSession()
    private val environment = FakeEnvironment()
    private val factory = FakeViewFactory()

    @AfterTest
    fun tearDown() {
        RibCoroutinesConfig.reset()
    }

    private fun mountRoot(initialNotice: String? = null): RibMount {
        val dependency = object : RootDependency {
            override val rootViewFactory = RootViewFactory { factory.makeRootView() }
            override val authViewFactory = AuthViewFactory { factory.makeAuthView() }
            override val unlockViewFactory = UnlockViewFactory { factory.makeUnlockView() }
            override val mainViewFactory = MainViewFactory { factory.makeMainView() }
            override val vaultListViewFactory = VaultListViewFactory { factory.makeVaultListView() }
            override val passwordGeneratorViewFactory = PasswordGeneratorViewFactory { factory.makePasswordGeneratorView() }
            override val settingsViewFactory = SettingsViewFactory { factory.makeSettingsView() }
            override val session: SessionPort = this@RootInteractorTest.session
            override val clipboard: ClipboardPort = FakeClipboard()
            override val backupFiles: BackupFilesPort = FakeBackupFiles()
            override val environment: AppEnvironment = this@RootInteractorTest.environment
            override val randomBytes: RandomBytes = FakeRandomBytes()
        }
        return RootBuilder(dependency).build(initialNotice).mount()
    }

    private fun rootTest(block: suspend TestScope.(RibMount) -> Unit) = runTest(dispatcher) {
        val mount = mountRoot()
        try {
            block(mount)
        } finally {
            if (mount.isMounted) mount.unmount()
        }
    }

    private val root get() = factory.rootViews.single()

    private fun TestScope.settle() = runCurrent()

    private fun TestScope.signIn(email: String = "user@test.com", password: String = "correct horse battery") {
        val auth = factory.authViews.last().listener!!
        auth.handle(AuthViewAction.UpdateEmail(email))
        auth.handle(AuthViewAction.UpdatePassword(password))
        auth.handle(AuthViewAction.Submit)
        settle()
    }

    private fun TestScope.unlock(password: String = "correct horse battery") {
        val unlock = factory.unlockViews.last().listener!!
        unlock.handle(UnlockViewAction.UpdatePassword(password))
        unlock.handle(UnlockViewAction.Unlock)
        settle()
    }

    private fun TestScope.mainListener() = factory.mainViews.last().listener!!

    // --- 시작 ---

    @Test
    fun 로그인_전이면_로그인_화면을_붙인다() = rootTest {
        assertEquals(1, factory.authViews.size)
        assertEquals(0, factory.unlockViews.size)
        assertEquals(0, factory.mainViews.size)
        assertEquals(1, root.visible.size)
    }

    @Test
    fun 잠겨_있으면_잠금_화면을_붙인다() = runTest(dispatcher) {
        session.startLocked()
        val mount = mountRoot()

        assertEquals(1, factory.unlockViews.size)
        assertEquals(0, factory.authViews.size)
        assertEquals(TEST_EMAIL, factory.unlockViews.single().state.email)
        mount.unmount()
    }

    @Test
    fun 열려_있으면_볼트_화면을_붙인다() = runTest(dispatcher) {
        session.startUnlocked()
        val mount = mountRoot()

        assertEquals(1, factory.mainViews.size)
        assertFalse(factory.mainViews.single().state.offline)
        mount.unmount()
    }

    // --- 로그인 · 잠금 해제 ---

    @Test
    fun 로그인하면_로그인_화면을_내리고_볼트_화면을_붙인다() = rootTest {
        val auth = root.visible.single()

        signIn()

        assertEquals(1, factory.mainViews.size)
        assertEquals(listOf(factory.mainViews.single()), root.visible)
        assertTrue(root.hidden.any { it === auth })
        assertNull(factory.authViews.single().listener, "내려간 로그인 화면은 입력을 받지 않는다")
    }

    @Test
    fun 가입해도_볼트_화면으로_간다() = rootTest {
        val auth = factory.authViews.single().listener!!
        auth.handle(AuthViewAction.SwitchMode)
        auth.handle(AuthViewAction.UpdateEmail("new@test.com"))
        auth.handle(AuthViewAction.UpdatePassword("a very long master password"))
        auth.handle(AuthViewAction.UpdateConfirmation("a very long master password"))
        auth.handle(AuthViewAction.Submit)
        settle()

        assertEquals(1, factory.mainViews.size)
    }

    @Test
    fun 로그인하는_동안_세션의_잠금_해제_알림만으로는_화면을_바꾸지_않는다() = rootTest {
        val gate = CompletableDeferred<Unit>()
        session.loginHandler = { gate.await() }
        signIn()
        assertEquals(0, factory.mainViews.size)

        gate.complete(Unit)
        settle()

        // 로그인 요청이 끝나 알린 뒤에야 한 번만 붙는다
        assertEquals(1, factory.mainViews.size)
    }

    @Test
    fun 잠금_해제하면_볼트_화면을_붙이고_잠금_화면을_내린다() = runTest(dispatcher) {
        session.startLocked()
        val mount = mountRoot()
        val lockScreen = root.visible.single()

        unlock()

        assertEquals(1, factory.mainViews.size)
        assertEquals(listOf(factory.mainViews.single()), root.visible)
        assertTrue(root.hidden.any { it === lockScreen })
        assertFalse(factory.mainViews.single().state.offline)
        mount.unmount()
    }

    @Test
    fun 오프라인으로_열었으면_볼트_화면이_오프라인_안내를_보인다() = runTest(dispatcher) {
        session.startLocked()
        session.unlockHandler = { _, _ -> true }
        val mount = mountRoot()

        unlock()

        assertEquals(1, factory.mainViews.size)
        assertEquals(OFFLINE_NOTICE, factory.mainViews.single().state.offlineNotice)
        mount.unmount()
    }

    @Test
    fun 잠금_해제_알림이_먼저_와도_잠금_화면이_결과를_알릴_때까지_기다린다() = runTest(dispatcher) {
        session.startLocked()
        session.unlockHandler = { _, _ -> true }
        val mount = mountRoot()

        unlock()

        // 세션이 알림을 보낸 시점에 붙였다면 offline 값이 사라진다. 한 번만, 오프라인으로 붙어야 한다
        assertEquals(1, factory.mainViews.size)
        assertTrue(factory.mainViews.single().state.offline)
        mount.unmount()
    }

    @Test
    fun 잠금_해제에서_로그아웃하면_로그인_화면으로_간다() = runTest(dispatcher) {
        session.startLocked()
        val mount = mountRoot()

        factory.unlockViews.single().listener!!.handle(UnlockViewAction.RequestLogout)
        settle()

        assertEquals(1, session.logoutCalls)
        assertEquals(1, factory.authViews.size)
        assertNull(factory.authViews.single().state.notice)
        mount.unmount()
    }

    // --- 잠금 ---

    @Test
    fun 볼트_화면에서_잠그면_잠금_화면으로_간다() = runTest(dispatcher) {
        session.startUnlocked()
        val mount = mountRoot()
        val mainScreen = root.visible.single()

        mainListener().handle(MainViewAction.RequestLock)
        settle()

        assertEquals(1, factory.unlockViews.size)
        assertEquals(listOf(factory.unlockViews.single()), root.visible)
        assertTrue(root.hidden.any { it === mainScreen })
        mount.unmount()
    }

    @Test
    fun 잠갔다_다시_열면_새_볼트_화면으로_시작한다() = runTest(dispatcher) {
        session.startUnlocked()
        val mount = mountRoot()
        mainListener().handle(MainViewAction.SelectTab(MainTab.SETTINGS))
        settle()
        mainListener().handle(MainViewAction.RequestLock)
        settle()

        unlock()

        assertEquals(2, factory.mainViews.size)
        assertEquals(MainTab.VAULT, factory.mainViews.last().state.selectedTab)
        assertNull(factory.mainViews.first().listener)
        mount.unmount()
    }

    @Test
    fun 자동_잠금처럼_화면_밖에서_잠가도_잠금_화면으로_간다() = runTest(dispatcher) {
        session.startUnlocked()
        val mount = mountRoot()

        session.lock()
        settle()

        assertEquals(1, factory.unlockViews.size)
        assertEquals(listOf(factory.unlockViews.single()), root.visible)
        mount.unmount()
    }

    @Test
    fun 다른_탭이_잠그면_이_탭도_잠금_화면으로_간다() = runTest(dispatcher) {
        session.startUnlocked()
        val mount = mountRoot()

        session.remoteLock()
        settle()

        assertEquals(1, factory.unlockViews.size)
        assertEquals(listOf(factory.unlockViews.single()), root.visible)
        mount.unmount()
    }

    @Test
    fun 다른_탭이_로그아웃하면_열려_있던_볼트_화면이_로그인_화면으로_바뀐다() = runTest(dispatcher) {
        session.startUnlocked()
        val mount = mountRoot()

        session.remoteLogout()
        settle()

        assertEquals(1, factory.authViews.size)
        assertEquals(listOf(factory.authViews.single()), root.visible)
        assertEquals(0, factory.unlockViews.size)
        mount.unmount()
    }

    @Test
    fun 다른_탭이_로그아웃하면_잠금_화면도_로그인_화면으로_바뀐다() = runTest(dispatcher) {
        session.startLocked()
        val mount = mountRoot()

        session.remoteLogout()
        settle()

        assertEquals(1, factory.authViews.size)
        assertEquals(listOf(factory.authViews.single()), root.visible)
        mount.unmount()
    }

    @Test
    fun 이미_맞는_화면이면_세션_알림에도_다시_붙이지_않는다() = runTest(dispatcher) {
        session.startUnlocked()
        val mount = mountRoot()

        session.fireRemote()
        session.fireLock()
        session.fireSync(null)
        settle()

        assertEquals(1, factory.mainViews.size)
        mount.unmount()
    }

    // --- 로그아웃 ---

    @Test
    fun 볼트_화면에서_로그아웃하면_로그인_화면으로_가고_안내는_없다() = runTest(dispatcher) {
        session.startUnlocked()
        val mount = mountRoot()

        mainListener().handle(MainViewAction.RequestLogout)
        settle()

        assertEquals(1, session.logoutCalls)
        assertEquals(1, factory.authViews.size)
        assertNull(factory.authViews.single().state.notice)
        assertEquals(listOf(factory.authViews.single()), root.visible)
        mount.unmount()
    }

    @Test
    fun 로그아웃_때_기기_기록을_지우지_못하면_로그인_화면이_그_안내를_보인다() = runTest(dispatcher) {
        session.startUnlocked()
        // 세션은 기록을 지우는 도중에 이 탭을 로그인 전 상태로 바꿔 알린다. 그 뒤에도 결과 안내가 로그인 화면에 닿아야 한다
        val gate = CompletableDeferred<Unit>()
        session.logoutHandler = {
            gate.await()
            throw DeviceStorageException(IllegalStateException("삭제 실패"))
        }
        val mount = mountRoot()

        mainListener().handle(MainViewAction.RequestLogout)
        settle()
        gate.complete(Unit)
        settle()

        assertEquals(LOGOUT_CLEAR_FAILED, factory.authViews.last().state.notice)
        assertEquals(listOf(factory.authViews.last()), root.visible)
        mount.unmount()
    }

    @Test
    fun 로그아웃_뒤_로그인하고_다시_로그아웃하면_옛_안내가_남지_않는다() = runTest(dispatcher) {
        session.startUnlocked()
        session.logoutHandler = { throw DeviceStorageException(IllegalStateException("삭제 실패")) }
        val mount = mountRoot()
        mainListener().handle(MainViewAction.RequestLogout)
        settle()
        assertEquals(LOGOUT_CLEAR_FAILED, factory.authViews.last().state.notice)
        signIn()
        session.logoutHandler = {}

        mainListener().handle(MainViewAction.RequestLogout)
        settle()

        assertNull(factory.authViews.last().state.notice)
        mount.unmount()
    }

    // --- 로그인 안내 ---

    @Test
    fun 잠금_해제에서_세션이_만료되면_로그인_화면이_만료_안내를_보인다() = runTest(dispatcher) {
        session.startLocked()
        session.unlockHandler = { _, _ ->
            session.current = com.keymanage.domain.session.SessionState.SignedOut
            session.fireSync(SessionExpiredException())
            throw SessionExpiredException()
        }
        val mount = mountRoot()

        unlock()

        assertEquals("로그인이 만료되었습니다. 다시 로그인해 주세요.", factory.authViews.last().state.notice)
        assertEquals(listOf(factory.authViews.last()), root.visible)
        // 같은 안내가 두 경로로 와도 로그인 화면은 한 번만 만든다
        assertEquals(1, factory.authViews.size)
        mount.unmount()
    }

    @Test
    fun 다른_곳에서_비밀번호를_바꾸면_이_탭의_로그인_화면이_그_안내를_보인다() = runTest(dispatcher) {
        session.startLocked()
        val mount = mountRoot()

        // 실제 세션은 로그인 전 상태로 바꾸고 알린 뒤에 사유를 알린다
        session.current = com.keymanage.domain.session.SessionState.SignedOut
        session.fireRemote()
        session.fireSync(MasterPasswordChangedException())
        settle()

        assertEquals(PASSWORD_CHANGED, factory.authViews.last().state.notice)
        assertEquals(listOf(factory.authViews.last()), root.visible)
        mount.unmount()
    }

    @Test
    fun 볼트_화면에서_다른_탭의_비밀번호_변경을_알면_로그인_화면이_안내를_보인다() = runTest(dispatcher) {
        session.startUnlocked()
        val mount = mountRoot()

        session.signOutLocally()
        session.fireSync(MasterPasswordChangedException())
        settle()

        assertEquals(PASSWORD_CHANGED, factory.authViews.last().state.notice)
        assertEquals(listOf(factory.authViews.last()), root.visible)
        mount.unmount()
    }

    @Test
    fun 재연결에서_만료되면_로그인_화면이_안내를_보인다() = runTest(dispatcher) {
        session.startUnlocked()
        session.signedIn = false
        session.refreshUnlocked()
        val mount = mountRoot()
        session.reconnectHandler = { _, _ ->
            session.signOutLocally()
            throw MasterPasswordChangedException()
        }

        mainListener().handle(MainViewAction.UpdateReconnectPassword("pw"))
        mainListener().handle(MainViewAction.Reconnect)
        settle()

        assertEquals(PASSWORD_CHANGED, factory.authViews.last().state.notice)
        mount.unmount()
    }

    @Test
    fun 안내는_로그인_화면에_넘긴_뒤_비워_다음_로그인_화면에는_없다() = runTest(dispatcher) {
        session.startLocked()
        session.unlockHandler = { _, _ ->
            session.current = com.keymanage.domain.session.SessionState.SignedOut
            throw SessionExpiredException()
        }
        val mount = mountRoot()
        unlock()
        assertNotNull(factory.authViews.last().state.notice)
        signIn()
        mainListener().handle(MainViewAction.RequestLock)
        settle()

        session.remoteLogout()
        settle()

        assertNull(factory.authViews.last().state.notice)
        mount.unmount()
    }

    @Test
    fun 틀린_비밀번호는_화면에_머물고_알림만_잠금_화면에_보인다() = runTest(dispatcher) {
        session.startLocked()
        session.unlockHandler = { _, _ -> throw WrongMasterPasswordException() }
        val mount = mountRoot()

        unlock()

        assertEquals(1, factory.unlockViews.size)
        assertEquals(0, factory.mainViews.size)
        assertEquals("마스터 비밀번호가 올바르지 않습니다.", factory.unlockViews.single().state.error?.body)
        mount.unmount()
    }

    @Test
    fun 네트워크_오류는_세션_종료가_아니라_잠금_화면_오류로만_보인다() = runTest(dispatcher) {
        session.startLocked()
        val mount = mountRoot()

        session.fireSync(NetworkError())
        settle()

        assertEquals(1, factory.unlockViews.size)
        assertEquals(0, factory.authViews.size)
        mount.unmount()
    }

    // --- 계정 삭제 ---

    @Test
    fun 계정을_지우면_로그인_화면이_완료_안내를_보인다() = runTest(dispatcher) {
        session.startUnlocked()
        session.deleteAccountHandler = { session.signOutLocally() }
        val mount = mountRoot()
        deleteAccountFromSettings()

        assertEquals(ACCOUNT_DELETED, factory.authViews.last().state.notice)
        assertEquals(listOf(factory.authViews.last()), root.visible)
        mount.unmount()
    }

    @Test
    fun 계정_삭제의_기기_정리는_볼트_화면이_내려가도_끝까지_한다() = runTest(dispatcher) {
        session.startUnlocked()
        val gate = CompletableDeferred<Unit>()
        var deviceCleared = false
        // 실제 세션은 이 탭을 로그인 전 상태로 바꿔 알린 뒤에 기기 기록을 지운다
        session.deleteAccountHandler = {
            session.signOutLocally()
            gate.await()
            deviceCleared = true
        }
        val mount = mountRoot()

        deleteAccountFromSettings(gate = gate)
        assertTrue(deviceCleared, "기기 정리가 취소되지 않고 끝나야 한다")
        assertEquals(ACCOUNT_DELETED, factory.authViews.last().state.notice)
        mount.unmount()
    }

    private fun TestScope.deleteAccountFromSettings(gate: CompletableDeferred<Unit>? = null) {
        mainListener().handle(MainViewAction.SelectTab(MainTab.SETTINGS))
        settle()
        val settings = factory.settingsViews.single().listener!!
        settings.handle(SettingsViewAction.StartDeleteAccount)
        settings.handle(SettingsViewAction.EditDeletionPassword("correct horse battery"))
        settings.handle(SettingsViewAction.EditDeletionPhrase("계정 삭제"))
        settings.handle(SettingsViewAction.SubmitDeleteAccount)
        settle()
        if (gate != null) {
            // 세션이 이미 로그인 전 상태로 알렸지만 정리가 끝나기 전에는 볼트 화면이 남아 정리를 끝낼 시간을 준다
            assertEquals(listOf(factory.mainViews.single()), root.visible)
            gate.complete(Unit)
            settle()
        }
    }

    @Test
    fun 잠금_화면의_로그아웃은_세션_알림이_와도_끝날_때까지_화면을_내리지_않는다() = runTest(dispatcher) {
        session.startLocked()
        var finished = false
        val gate = CompletableDeferred<Unit>()
        // 세션은 기기 기록을 정리한 뒤 서버에 토큰 폐기를 요청한다. 정리는 이미 끝났고 이 탭은 로그인 전 상태다
        session.logoutHandler = {
            gate.await()
            finished = true
        }
        val mount = mountRoot()
        val unlockScreen = root.visible.single()

        factory.unlockViews.single().listener!!.handle(UnlockViewAction.RequestLogout)
        settle()
        session.fireRemote()
        settle()

        assertEquals(0, factory.authViews.size)
        assertEquals(1, root.visible.size)
        assertSame(unlockScreen, root.visible.single())

        gate.complete(Unit)
        settle()

        assertTrue(finished)
        assertEquals(1, factory.authViews.size)
        assertEquals(listOf(factory.authViews.single()), root.visible)
        mount.unmount()
    }

    @Test
    fun 잠금_화면의_로그아웃_중_기기_기록을_지우지_못해도_안내가_로그인_화면에_닿는다() = runTest(dispatcher) {
        session.startLocked()
        val gate = CompletableDeferred<Unit>()
        session.logoutHandler = {
            gate.await()
            throw DeviceStorageException(IllegalStateException("삭제 실패"))
        }
        val mount = mountRoot()

        factory.unlockViews.single().listener!!.handle(UnlockViewAction.RequestLogout)
        settle()
        session.fireRemote()
        gate.complete(Unit)
        settle()

        assertEquals(LOGOUT_CLEAR_FAILED, factory.authViews.last().state.notice)
        mount.unmount()
    }

    // --- 로그인 화면 유지 (L4) ---

    @Test
    fun 로그인_화면이_떠_있을_때_안내가_와도_화면을_다시_만들지_않고_이메일은_남긴다() = rootTest {
        val auth = factory.authViews.single()
        auth.listener!!.handle(AuthViewAction.UpdateEmail("user@test.com"))
        auth.listener!!.handle(AuthViewAction.UpdatePassword("correct horse battery"))
        settle()

        session.fireSync(MasterPasswordChangedException())
        settle()

        assertEquals(1, factory.authViews.size)
        assertEquals(listOf(auth), root.visible)
        assertEquals(PASSWORD_CHANGED, auth.state.notice)
        assertEquals("user@test.com", auth.state.email)
        // 비밀번호는 안내와 함께 비운다 (보안)
        assertEquals("", auth.state.password)
    }

    @Test
    fun 로그인_화면이_떠_있을_때_세션_알림만_와도_화면을_다시_만들지_않는다() = rootTest {
        val auth = factory.authViews.single()
        auth.listener!!.handle(AuthViewAction.UpdateEmail("user@test.com"))
        settle()

        session.fireRemote()
        session.fireLock()
        settle()

        assertEquals(1, factory.authViews.size)
        assertEquals("user@test.com", auth.state.email)
    }

    @Test
    fun 안내가_두_번_이어_와도_로그인_화면은_하나이고_이메일이_남는다() = rootTest {
        val auth = factory.authViews.single()
        auth.listener!!.handle(AuthViewAction.UpdateEmail("user@test.com"))
        settle()

        session.fireSync(SessionExpiredException())
        settle()
        session.fireSync(MasterPasswordChangedException())
        settle()

        assertEquals(1, factory.authViews.size)
        assertEquals(PASSWORD_CHANGED, auth.state.notice)
        assertEquals("user@test.com", auth.state.email)
    }

    // --- 시작할 때 받은 안내 (웹 restoreError) ---

    @Test
    fun 시작할_때_받은_안내를_첫_로그인_화면에_보이고_다음_로그인_화면에는_없다() = runTest(dispatcher) {
        val mount = mountRoot(initialNotice = "로그인이 만료되었습니다. 다시 로그인해 주세요.")

        assertEquals("로그인이 만료되었습니다. 다시 로그인해 주세요.", factory.authViews.single().state.notice)

        signIn()
        mainListener().handle(MainViewAction.RequestLogout)
        settle()

        assertNull(factory.authViews.last().state.notice)
        mount.unmount()
    }

    @Test
    fun 시작할_때_받은_안내는_로그인_화면이_아닌_첫_화면에서는_쓰지_않고_로그인_화면이_뜰_때까지_들고_있다() = runTest(dispatcher) {
        session.startLocked()
        val mount = mountRoot(initialNotice = "만료 안내")
        assertEquals(0, factory.authViews.size)

        session.remoteLogout()
        settle()

        assertEquals("만료 안내", factory.authViews.single().state.notice)
        mount.unmount()
    }

    // --- 생명주기 ---

    @Test
    fun 끝내면_화면을_내리고_세션_구독을_모두_거둔다() = runTest(dispatcher) {
        session.startUnlocked()
        val mount = mountRoot()
        assertTrue(session.listenerCount > 0)

        mount.unmount()

        assertEquals(0, session.listenerCount)
        assertEquals(0, environment.networkListeners.size)
        assertNull(factory.mainViews.single().listener)
    }

    @Test
    fun 끝낸_뒤_늦게_온_세션_알림은_무시한다() = runTest(dispatcher) {
        session.startUnlocked()
        val mount = mountRoot()
        val locks = session.lockListeners.toList()
        val syncs = session.syncListeners.toList()
        mount.unmount()

        locks.forEach { it() }
        syncs.forEach { it(SessionExpiredException()) }
        settle()

        assertEquals(0, factory.unlockViews.size)
        assertEquals(0, factory.authViews.size)
    }

    private companion object {
        const val TEST_EMAIL = "user@test.com"
    }
}
