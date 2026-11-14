package com.keymanage.feature

import com.carti.ribs.RibCoroutinesConfig
import com.keymanage.feature.testing.FakeBackupFiles
import com.keymanage.feature.testing.FakeClipboard
import com.keymanage.feature.testing.FakeEnvironment
import com.keymanage.feature.testing.FakeHost
import com.keymanage.feature.testing.FakeRandomBytes
import com.keymanage.feature.testing.FakeSession
import com.keymanage.feature.testing.FakeViewFactory
import com.keymanage.feature.testing.installTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// 앱 진입점: start로 루트 화면을 호스트에 보이고 세션 상태에 맞는 첫 화면을 붙인다. stop으로 모두 거둔다
@OptIn(ExperimentalCoroutinesApi::class)
class KeyManageAppTest {
    init {
        installTestDispatcher()
    }

    private val session = FakeSession()
    private val environment = FakeEnvironment()
    private val factory = FakeViewFactory()
    private val host = FakeHost()

    @AfterTest
    fun tearDown() {
        RibCoroutinesConfig.reset()
    }

    private fun makeApp(initialNotice: String? = null, biometric: com.keymanage.feature.unlock.BiometricUnlock? = null) = KeyManageApp(
        viewFactory = factory,
        session = session,
        clipboard = FakeClipboard(),
        backupFiles = FakeBackupFiles(),
        environment = environment,
        randomBytes = FakeRandomBytes(),
        initialNotice = initialNotice,
        biometricUnlock = biometric,
    )

    @Test
    fun 트리가_떠_있는_동안_세션이_만료되면_생체_해제_키를_지운다() {
        session.startLocked()
        val biometric = ForgetOnlyBiometric()
        val app = makeApp(biometric = biometric)
        app.start(host)

        session.current = com.keymanage.domain.session.SessionState.SignedOut
        session.syncListeners.toList().forEach { it(com.keymanage.domain.session.SessionExpiredException()) }

        assertEquals(listOf(session.email), biometric.forgotten)
        app.stop()
        assertEquals(0, session.listenerCount)
    }

    @Test
    fun start하면_루트_화면을_호스트에_보이고_로그인_화면을_붙인다() {
        val app = makeApp()

        app.start(host)

        assertEquals(1, host.shown.size)
        assertSame(factory.rootViews.single(), host.shown.single())
        assertEquals(1, factory.authViews.size)
        assertEquals(1, factory.rootViews.single().visible.size)
        app.stop()
    }

    @Test
    fun 잠긴_계정이면_첫_화면이_잠금_화면이다() {
        session.startLocked()
        val app = makeApp()

        app.start(host)

        assertEquals(1, factory.unlockViews.size)
        assertEquals(0, factory.authViews.size)
        app.stop()
    }

    @Test
    fun 열린_볼트면_첫_화면이_볼트_틀과_볼트_목록이다() {
        session.startUnlocked()
        val app = makeApp()

        app.start(host)

        assertEquals(1, factory.mainViews.size)
        assertEquals(1, factory.vaultListViews.size)
        app.stop()
    }

    @Test
    fun stop하면_루트_화면을_거두고_세션_구독을_모두_해제한다() {
        session.startUnlocked()
        val app = makeApp()
        app.start(host)
        assertTrue(session.listenerCount > 0)

        app.stop()

        assertSame(factory.rootViews.single(), host.hidden.single())
        assertEquals(0, session.listenerCount)
        assertEquals(0, environment.networkListeners.size)
        assertNull(factory.mainViews.single().listener)
    }

    @Test
    fun 시작_전이나_끝낸_뒤_stop은_아무_일도_하지_않는다() {
        val app = makeApp()

        app.stop()
        app.start(host)
        app.stop()
        app.stop()

        assertEquals(1, host.hidden.size)
    }
    @Test
    fun 끝낸_뒤_다시_시작하면_로그인_화면을_새로_붙이고_이전_화면을_남기지_않는다() {
        val app = makeApp()
        app.start(host)
        val first = factory.authViews.single()
        app.stop()

        app.start(host)

        assertEquals(2, factory.authViews.size)
        assertEquals(2, host.shown.size)
        val root = factory.rootViews.single()
        assertEquals(1, root.visible.size)
        assertSame(factory.authViews.last(), root.visible.single())
        assertTrue(root.visible.single() !== first)
        app.stop()
    }

    @Test
    fun 끝낸_뒤_다시_시작하면_열린_볼트의_화면도_새로_붙인다() {
        session.startUnlocked()
        val app = makeApp()
        app.start(host)
        app.stop()

        app.start(host)

        assertEquals(2, factory.mainViews.size)
        assertEquals(2, factory.vaultListViews.size)
        assertEquals(1, factory.rootViews.single().visible.size)
        assertSame(factory.mainViews.last(), factory.rootViews.single().visible.single())
        app.stop()
    }

    @Test
    fun 시작할_때_받은_안내를_로그인_화면에_보인다() {
        val app = makeApp(initialNotice = "로그인이 만료되었습니다. 다시 로그인해 주세요.")

        app.start(host)

        assertEquals("로그인이 만료되었습니다. 다시 로그인해 주세요.", factory.authViews.single().state.notice)
        app.stop()
    }

    @Test
    fun 안내를_안_넘기면_로그인_화면에_안내가_없다() {
        val app = makeApp()

        app.start(host)

        assertNull(factory.authViews.single().state.notice)
        app.stop()
    }

}

private class ForgetOnlyBiometric : com.keymanage.feature.unlock.BiometricUnlock {
    val forgotten = mutableListOf<String>()
    override fun isAvailable() = false
    override fun isEnrolled(email: String) = false
    override fun passwordUnlockedAt(email: String): kotlin.time.Instant? = null
    override suspend fun remember(email: String, unlockKey: ByteArray, passwordUnlockedAt: kotlin.time.Instant) = Unit
    override suspend fun recall(email: String) = com.keymanage.feature.unlock.BiometricRecall.Cancelled
    override fun forget(email: String) {
        forgotten += email
    }
}
