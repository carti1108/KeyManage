package com.keymanage.feature.main

import com.carti.ribs.RibCoroutinesConfig
import com.carti.ribs.RibMount
import com.carti.ribs.mount
import com.keymanage.domain.auth.MasterPasswordChangedException
import com.keymanage.domain.device.DeviceStorageException
import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.session.LockScope
import com.keymanage.domain.session.SessionExpiredException
import com.keymanage.domain.session.WrongMasterPasswordException
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.messages.ACCOUNT_DELETED
import com.keymanage.feature.messages.DEVICE_STORAGE_FAILED
import com.keymanage.feature.messages.LOGOUT_CLEAR_FAILED
import com.keymanage.feature.messages.OFFLINE_NOTICE
import com.keymanage.feature.messages.READ_ONLY_NOTICE
import com.keymanage.feature.messages.RollbackWarning
import com.keymanage.feature.settings.SettingsViewAction
import com.keymanage.feature.settings.SettingsViewFactory
import com.keymanage.feature.testing.FakeBackupFiles
import com.keymanage.feature.testing.FakeClipboard
import com.keymanage.feature.testing.FakeEnvironment
import com.keymanage.feature.testing.FakeRandomBytes
import com.keymanage.feature.testing.FakeSession
import com.keymanage.feature.testing.FakeViewFactory
import com.keymanage.feature.testing.installTestDispatcher
import com.keymanage.feature.vaultlist.VaultListViewFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.keymanage.domain.platform.RandomBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// 열린 볼트의 틀(탭, 경고·배너, 재연결, 잠금·로그아웃). 원본: Apps/Web/src/ui/screens/VaultHomeScreen.tsx, ui/session/ReconnectPrompt.tsx,
// SessionRoot.tsx의 볼트 화면 부분, iOS Features/Main
@OptIn(ExperimentalCoroutinesApi::class)
class MainInteractorTest {
    private val dispatcher = installTestDispatcher()
    private val session = FakeSession().startUnlocked()
    private val environment = FakeEnvironment()
    private val factory = FakeViewFactory()
    private val listener = RecordingMainListener()
    private lateinit var lastRouter: MainRouter

    private fun router(): MainRouter = lastRouter

    @AfterTest
    fun tearDown() {
        RibCoroutinesConfig.reset()
    }

    private fun mountMain(offline: Boolean = false): RibMount {
        val dependency = object : MainDependency {
            override val mainViewFactory = MainViewFactory { factory.makeMainView() }
            override val vaultListViewFactory = VaultListViewFactory { factory.makeVaultListView() }
            override val passwordGeneratorViewFactory = PasswordGeneratorViewFactory { factory.makePasswordGeneratorView() }
            override val settingsViewFactory = SettingsViewFactory { factory.makeSettingsView() }
            override val session: SessionPort = this@MainInteractorTest.session
            override val clipboard: ClipboardPort = FakeClipboard()
            override val backupFiles: BackupFilesPort = FakeBackupFiles()
            override val environment: AppEnvironment = this@MainInteractorTest.environment
            override val randomBytes: RandomBytes = FakeRandomBytes()
        }
        lastRouter = MainBuilder(dependency).build(listener, offline)
        return lastRouter.mount()
    }

    private fun mainTest(offline: Boolean = false, block: suspend TestScope.(RibMount) -> Unit) = runTest(dispatcher) {
        val mount = mountMain(offline)
        try {
            block(mount)
        } finally {
            if (mount.isMounted) mount.unmount()
        }
    }

    private val view get() = factory.mainViews.single()
    private val state get() = view.state

    private fun TestScope.send(action: MainViewAction) {
        assertNotNull(view.listener, "활성 구간이 아니면 뷰 입력을 받지 않는다").handle(action)
        runCurrent()
    }

    // --- 탭 ---

    @Test
    fun 처음에는_볼트_탭이고_볼트_화면만_붙인다() = mainTest {
        assertEquals(MainTab.VAULT, state.selectedTab)
        assertEquals(1, factory.vaultListViews.size)
        assertEquals(0, factory.generatorViews.size)
        assertEquals(0, factory.settingsViews.size)
        assertEquals(1, view.visible.size)
        assertEquals(TEST_EMAIL_FOR_MAIN, state.email)
    }

    @Test
    fun 탭을_바꾸면_고른_화면만_보이고_이전_화면은_숨긴다() = mainTest {
        val vaultList = view.visible.single()

        send(MainViewAction.SelectTab(MainTab.GENERATOR))

        assertEquals(MainTab.GENERATOR, state.selectedTab)
        assertEquals(1, view.visible.size)
        assertTrue(view.visible.single() !== vaultList)
        assertTrue(view.hidden.any { it === vaultList })

        send(MainViewAction.SelectTab(MainTab.SETTINGS))

        assertEquals(MainTab.SETTINGS, state.selectedTab)
        assertEquals(1, view.visible.size)
        assertEquals(1, factory.settingsViews.size)
    }

    @Test
    fun 한_번_붙인_화면은_다시_만들지_않고_유지한다() = mainTest {
        send(MainViewAction.SelectTab(MainTab.GENERATOR))
        val generator = view.visible.single()
        send(MainViewAction.SelectTab(MainTab.VAULT))

        send(MainViewAction.SelectTab(MainTab.GENERATOR))

        assertEquals(1, factory.vaultListViews.size)
        assertEquals(1, factory.generatorViews.size)
        assertSame(generator, view.visible.single())
        // 숨겨진 동안에도 자식은 붙어 있다
        assertNotNull(factory.vaultListViews.single().listener)
    }

    @Test
    fun 이미_고른_탭을_다시_고르면_아무_일도_하지_않는다() = mainTest {
        val shownBefore = view.shown.size

        send(MainViewAction.SelectTab(MainTab.VAULT))

        assertEquals(shownBefore, view.shown.size)
        assertTrue(view.hidden.isEmpty())
    }

    @Test
    fun 생성기_탭은_복사_버튼을_둔다() = mainTest {
        send(MainViewAction.SelectTab(MainTab.GENERATOR))

        val generator = factory.generatorViews.single().states.last()
        assertTrue(generator.showsCopy)
        assertFalse(generator.showsUse)
        assertFalse(generator.showsClose)
    }

    @Test
    fun 설정_탭은_계정_정보를_그린다() = mainTest {
        send(MainViewAction.SelectTab(MainTab.SETTINGS))

        assertEquals(TEST_EMAIL_FOR_MAIN, factory.settingsViews.single().state.email)
    }

    // --- 경고·배너 ---

    @Test
    fun 평상시에는_경고도_배너도_없다() = mainTest {
        assertNull(state.offlineNotice)
        assertNull(state.readOnlyNotice)
        assertNull(state.deviceStorageWarning)
        assertNull(state.syncError)
        assertFalse(state.needsReconnect)
        assertFalse(state.isConfirmingLogout)
    }

    @Test
    fun 잠금_해제를_오프라인으로_열었으면_오프라인_안내를_보인다() = mainTest(offline = true) {
        assertTrue(state.offline)
        assertEquals(OFFLINE_NOTICE, state.offlineNotice)
    }

    @Test
    fun 네트워크가_끊기면_오프라인_안내를_보이고_돌아오면_걷는다() = mainTest {
        environment.changeOffline(true)
        runCurrent()
        assertEquals(OFFLINE_NOTICE, state.offlineNotice)

        environment.changeOffline(false)
        runCurrent()
        assertNull(state.offlineNotice)
    }

    @Test
    fun 동기화_대기_중이면_오프라인_안내를_보인다() = mainTest {
        session.pendingSync = true
        session.refreshUnlocked()

        session.fireRemote()
        runCurrent()

        assertEquals(OFFLINE_NOTICE, state.offlineNotice)
    }

    @Test
    fun 동기화에_성공하면_열_때_정한_오프라인_표시를_거둔다() = mainTest(offline = true) {
        session.fireSync(null)
        runCurrent()

        assertFalse(state.offline)
        assertNull(state.offlineNotice)
    }

    @Test
    fun 새_버전_앱이_저장한_볼트는_읽기_전용_안내를_보인다() = mainTest {
        session.readOnly = true
        session.refreshUnlocked()

        session.fireRemote()
        runCurrent()

        assertTrue(state.readOnly)
        assertEquals(READ_ONLY_NOTICE, state.readOnlyNotice)
    }

    @Test
    fun 기기_저장에_실패했으면_경고를_보인다() = mainTest {
        session.storedOnDevice = false
        session.refreshUnlocked()

        session.fireSync(DeviceStorageException(IllegalStateException("저장 실패")))
        runCurrent()

        assertEquals(DEVICE_STORAGE_FAILED, state.deviceStorageWarning)
        // 기기 저장 실패는 동기화 오류가 아니라 이 경고로만 보인다
        assertNull(state.syncError)
    }

    @Test
    fun 동기화_거절은_정책_문구로_보이고_다음_성공에_걷힌다() = mainTest {
        session.fireSync(NetworkError())
        runCurrent()
        assertEquals("서버에 연결할 수 없습니다. 네트워크 연결을 확인해 주세요.", state.syncError?.body)

        session.fireSync(null)
        runCurrent()
        assertNull(state.syncError)
    }

    @Test
    fun 세션_만료는_볼트_화면_경고가_아니라_로그인_화면_안내로_보인다() = mainTest {
        session.fireSync(SessionExpiredException())
        runCurrent()

        assertNull(state.syncError)
    }

    // --- 재연결 ---

    private fun startWithoutSession() {
        session.signedIn = false
        session.refreshUnlocked()
    }

    @Test
    fun 세션_없이_열었으면_재연결_입력을_보인다() = mainTest {
        startWithoutSession()
        session.fireRemote()
        runCurrent()

        assertTrue(state.needsReconnect)
    }

    @Test
    fun 재연결은_비밀번호를_보내고_입력_칸을_비운다() = mainTest {
        startWithoutSession()
        session.fireRemote()
        runCurrent()
        send(MainViewAction.UpdateReconnectPassword("correct horse battery"))
        assertEquals("correct horse battery", state.reconnectPassword)

        send(MainViewAction.Reconnect)

        assertEquals(listOf("correct horse battery" to false), session.reconnects)
        assertEquals("", state.reconnectPassword)
        assertFalse(state.needsReconnect)
        assertFalse(state.isReconnecting)
        assertNull(state.reconnectError)
    }

    @Test
    fun 재연결이_끝나면_열_때_정한_오프라인_표시를_거둔다() = mainTest(offline = true) {
        startWithoutSession()
        session.fireRemote()
        runCurrent()
        send(MainViewAction.UpdateReconnectPassword("pw"))

        send(MainViewAction.Reconnect)

        assertFalse(state.offline)
    }

    @Test
    fun 재연결_중에는_로딩을_보이고_다시_보내지_않는다() = mainTest {
        startWithoutSession()
        session.fireRemote()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        session.reconnectHandler = { _, _ -> gate.await() }
        send(MainViewAction.UpdateReconnectPassword("pw"))
        send(MainViewAction.Reconnect)
        assertTrue(state.isReconnecting)

        send(MainViewAction.Reconnect)

        assertEquals(1, session.reconnects.size)
        gate.complete(Unit)
        runCurrent()
        assertFalse(state.isReconnecting)
    }

    @Test
    fun 재연결_실패는_정책_문구로_보이고_새_시도에서_지운다() = mainTest {
        startWithoutSession()
        session.fireRemote()
        runCurrent()
        session.reconnectHandler = { _, _ -> throw WrongMasterPasswordException() }
        send(MainViewAction.UpdateReconnectPassword("wrong"))

        send(MainViewAction.Reconnect)

        assertEquals("마스터 비밀번호가 올바르지 않습니다.", state.reconnectError?.body)
        assertEquals("", state.reconnectPassword)
        assertTrue(state.needsReconnect)

        val gate = CompletableDeferred<Unit>()
        session.reconnectHandler = { _, _ -> gate.await() }
        send(MainViewAction.UpdateReconnectPassword("again"))
        send(MainViewAction.Reconnect)
        assertNull(state.reconnectError)
        gate.complete(Unit)
    }

    @Test
    fun 되돌림_경고는_받아들이면_처음_비밀번호로_다시_보낸다() = mainTest {
        startWithoutSession()
        session.fireRemote()
        runCurrent()
        session.reconnectHandler = { _, accept -> if (!accept) throw RevisionRollbackException(5, 3) }
        send(MainViewAction.UpdateReconnectPassword("first password"))

        send(MainViewAction.Reconnect)

        assertTrue(state.isConfirmingReconnectRollback)
        assertNull(state.reconnectError)
        assertEquals("", state.reconnectPassword)

        send(MainViewAction.AcceptReconnectRollback)

        assertEquals(listOf("first password" to false, "first password" to true), session.reconnects)
        assertFalse(state.isConfirmingReconnectRollback)
        assertFalse(state.needsReconnect)
    }

    @Test
    fun 되돌림_경고를_취소하면_확인을_닫고_다시_보내지_않는다() = mainTest {
        startWithoutSession()
        session.fireRemote()
        runCurrent()
        session.reconnectHandler = { _, _ -> throw RevisionRollbackException(5, 3) }
        send(MainViewAction.UpdateReconnectPassword("pw"))
        send(MainViewAction.Reconnect)

        send(MainViewAction.CancelReconnectRollback)
        send(MainViewAction.AcceptReconnectRollback)

        assertFalse(state.isConfirmingReconnectRollback)
        assertEquals(1, session.reconnects.size)
    }

    @Test
    fun 받아들인_뒤에도_되돌림이면_경고_문구를_오류로_보인다() = mainTest {
        startWithoutSession()
        session.fireRemote()
        runCurrent()
        session.reconnectHandler = { _, _ -> throw RevisionRollbackException(5, 3) }
        send(MainViewAction.UpdateReconnectPassword("pw"))
        send(MainViewAction.Reconnect)

        send(MainViewAction.AcceptReconnectRollback)

        assertFalse(state.isConfirmingReconnectRollback)
        assertEquals(RollbackWarning.TITLE, state.reconnectError?.title)
    }

    @Test
    fun 재연결에서_세션이_만료되면_로그인_화면으로_보내달라고_알린다() = mainTest {
        startWithoutSession()
        session.fireRemote()
        runCurrent()
        session.reconnectHandler = { _, _ -> throw MasterPasswordChangedException() }
        send(MainViewAction.UpdateReconnectPassword("pw"))

        send(MainViewAction.Reconnect)

        assertEquals(listOf("마스터 비밀번호가 바뀌었습니다. 새 비밀번호로 로그인해 주세요."), listener.signInRequests)
        assertNull(state.reconnectError)
    }

    // --- 잠금 ---

    @Test
    fun 잠그기는_모든_탭을_잠그고_부모에게_알린다() = mainTest {
        send(MainViewAction.RequestLock)

        assertEquals(listOf(LockScope.ALL_TABS), session.lockScopes)
        assertEquals(1, listener.locks)
    }

    // --- 로그아웃 ---

    @Test
    fun 대기_중인_변경이_없으면_바로_로그아웃하고_알린다() = mainTest {
        send(MainViewAction.RequestLogout)

        assertEquals(1, session.pendingOnDeviceCalls)
        assertEquals(1, session.logoutCalls)
        assertEquals(listOf<String?>(null), listener.logouts)
        assertFalse(state.isConfirmingLogout)
    }

    @Test
    fun 이_탭에_동기화_대기가_있으면_확인을_먼저_묻는다() = mainTest {
        session.pendingSync = true
        session.refreshUnlocked()

        send(MainViewAction.RequestLogout)

        assertTrue(state.isConfirmingLogout)
        assertEquals(0, session.logoutCalls)
        assertEquals(0, session.pendingOnDeviceCalls)
    }

    @Test
    fun 다른_탭이_기기에_남긴_대기_내용이_있어도_확인을_묻는다() = mainTest {
        session.pendingOnDeviceHandler = { true }

        send(MainViewAction.RequestLogout)

        assertTrue(state.isConfirmingLogout)
        assertEquals(0, session.logoutCalls)
    }

    @Test
    fun 기기_기록을_읽지_못하면_대기_내용이_있다고_보고_확인을_묻는다() = mainTest {
        session.pendingOnDeviceHandler = { throw IllegalStateException("읽기 실패") }

        send(MainViewAction.RequestLogout)

        assertTrue(state.isConfirmingLogout)
        assertEquals(0, session.logoutCalls)
    }

    @Test
    fun 확인하면_로그아웃하고_취소하면_닫는다() = mainTest {
        session.pendingOnDeviceHandler = { true }
        send(MainViewAction.RequestLogout)

        send(MainViewAction.CancelLogout)
        assertFalse(state.isConfirmingLogout)
        assertEquals(0, session.logoutCalls)

        send(MainViewAction.RequestLogout)
        send(MainViewAction.ConfirmLogout)

        assertEquals(1, session.logoutCalls)
        assertEquals(listOf<String?>(null), listener.logouts)
    }

    @Test
    fun 확인_중이_아닐_때의_로그아웃_확정은_무시한다() = mainTest {
        send(MainViewAction.ConfirmLogout)

        assertEquals(0, session.logoutCalls)
    }

    @Test
    fun 기기_기록을_지우지_못하면_로그인_화면에_보일_안내를_담아_알린다() = mainTest {
        session.logoutHandler = { throw DeviceStorageException(IllegalStateException("삭제 실패")) }

        send(MainViewAction.RequestLogout)

        assertEquals(listOf<String?>(LOGOUT_CLEAR_FAILED), listener.logouts)
    }

    @Test
    fun 서버_토큰_폐기만_실패하면_안내_없이_알린다() = mainTest {
        session.logoutHandler = { throw NetworkError() }

        send(MainViewAction.RequestLogout)

        assertEquals(listOf<String?>(null), listener.logouts)
    }

    @Test
    fun 로그아웃은_이_화면이_내려가도_끝까지_하고_결과를_알린다() = mainTest { mount ->
        // 세션은 기기 기록을 지우는 도중에 잠금 알림을 보내 부모가 이 화면을 내린다. 그래도 정리는 취소되면 안 된다
        val gate = CompletableDeferred<Unit>()
        var cleaned = false
        session.logoutHandler = {
            gate.await()
            cleaned = true
            throw DeviceStorageException(IllegalStateException("삭제 실패"))
        }
        send(MainViewAction.RequestLogout)
        assertTrue(listener.logouts.isEmpty())

        mount.unmount()
        gate.complete(Unit)
        runCurrent()

        assertTrue(cleaned)
        assertEquals(listOf<String?>(LOGOUT_CLEAR_FAILED), listener.logouts)
    }

    @Test
    fun 로그아웃이_진행_중인_동안_세션_종료를_마무리하는_중이라고_알린다() = mainTest {
        val gate = CompletableDeferred<Unit>()
        session.logoutHandler = { gate.await() }
        assertFalse(router().interactor.isFinishingSession)

        send(MainViewAction.RequestLogout)

        assertTrue(router().interactor.isFinishingSession)
        assertTrue(state.isLoggingOut)
        gate.complete(Unit)
        runCurrent()
        assertFalse(router().interactor.isFinishingSession)
    }

    @Test
    fun 로그아웃_중에_다시_요청해도_한_번만_한다() = mainTest {
        val gate = CompletableDeferred<Unit>()
        session.logoutHandler = { gate.await() }
        send(MainViewAction.RequestLogout)

        send(MainViewAction.RequestLogout)

        assertEquals(1, session.logoutCalls)
        gate.complete(Unit)
    }

    // --- 설정 이벤트 ---

    @Test
    fun 설정의_잠그기는_잠그고_부모에게_알린다() = mainTest {
        send(MainViewAction.SelectTab(MainTab.SETTINGS))

        factory.settingsViews.single().listener!!.handle(SettingsViewAction.RequestLock)
        runCurrent()

        assertEquals(listOf(LockScope.ALL_TABS), session.lockScopes)
        assertEquals(1, listener.locks)
    }

    @Test
    fun 설정의_로그아웃은_같은_확인_절차를_탄다() = mainTest {
        session.pendingSync = true
        session.refreshUnlocked()
        send(MainViewAction.SelectTab(MainTab.SETTINGS))

        factory.settingsViews.single().listener!!.handle(SettingsViewAction.RequestLogout)
        runCurrent()

        assertTrue(state.isConfirmingLogout)
        assertEquals(0, session.logoutCalls)
    }

    @Test
    fun 계정을_지우면_로그인_화면으로_보내달라고_안내와_함께_알린다() = mainTest {
        send(MainViewAction.SelectTab(MainTab.SETTINGS))
        val settings = factory.settingsViews.single().listener!!

        settings.handle(SettingsViewAction.StartDeleteAccount)
        settings.handle(SettingsViewAction.EditDeletionPassword("correct horse battery"))
        settings.handle(SettingsViewAction.EditDeletionPhrase("계정 삭제"))
        settings.handle(SettingsViewAction.SubmitDeleteAccount)
        runCurrent()

        assertEquals(listOf("correct horse battery"), session.deletes)
        assertEquals(listOf(ACCOUNT_DELETED), listener.signInRequests)
    }

    @Test
    fun 계정_삭제가_진행_중인_동안_세션_종료를_마무리하는_중이라고_알린다() = mainTest {
        send(MainViewAction.SelectTab(MainTab.SETTINGS))
        val settings = factory.settingsViews.single().listener!!
        val gate = CompletableDeferred<Unit>()
        session.deleteAccountHandler = { gate.await() }
        settings.handle(SettingsViewAction.StartDeleteAccount)
        settings.handle(SettingsViewAction.EditDeletionPassword("pw"))
        settings.handle(SettingsViewAction.EditDeletionPhrase("계정 삭제"))
        settings.handle(SettingsViewAction.SubmitDeleteAccount)
        runCurrent()

        assertTrue(router().interactor.isFinishingSession)

        gate.complete(Unit)
        runCurrent()
        assertFalse(router().interactor.isFinishingSession)
    }

    @Test
    fun 설정에서_세션이_바뀌었다고_알리면_다시_읽는다() = mainTest {
        session.pendingSync = true
        session.refreshUnlocked()

        router().interactor.settingsDidChangeSession()

        assertEquals(OFFLINE_NOTICE, state.offlineNotice)
    }

    @Test
    fun 설정에서_세션이_바뀌었는데_더_열려_있지_않으면_부모가_다시_읽도록_알린다() = mainTest {
        session.signOutLocally()

        router().interactor.settingsDidChangeSession()

        assertEquals(1, listener.locks)
    }

    // --- 생명주기 ---

    @Test
    fun 내려가면_세션_구독과_뷰_입력을_모두_거둔다() = mainTest { mount ->
        assertTrue(session.listenerCount > 0)
        assertTrue(environment.networkListeners.isNotEmpty())
        val handler = view.listener

        mount.unmount()

        assertNull(view.listener)
        assertEquals(0, session.listenerCount)
        assertTrue(environment.networkListeners.isEmpty())
        handler?.handle(MainViewAction.RequestLock)
        runCurrent()
        assertTrue(session.lockScopes.isEmpty())
    }

    @Test
    fun 내려간_뒤_늦게_온_세션_알림은_무시한다() = mainTest { mount ->
        val staleSync = session.syncListeners.toList()
        mount.unmount()

        staleSync.forEach { it(NetworkError()) }
        runCurrent()

        assertNull(view.states.last().syncError)
    }

}

private const val TEST_EMAIL_FOR_MAIN = "user@test.com"

private class RecordingMainListener : MainListener {
    var locks = 0
    val logouts = mutableListOf<String?>()
    val signInRequests = mutableListOf<String>()

    override fun didLock() {
        locks += 1
    }

    override fun didLogout(notice: String?) {
        logouts += notice
    }

    override fun didRequireSignIn(notice: String) {
        signInRequests += notice
    }
}
