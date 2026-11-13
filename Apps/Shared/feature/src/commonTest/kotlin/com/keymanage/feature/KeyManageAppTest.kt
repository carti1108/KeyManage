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

    private fun makeApp() = KeyManageApp(
        viewFactory = factory,
        session = session,
        clipboard = FakeClipboard(),
        backupFiles = FakeBackupFiles(),
        environment = environment,
        randomBytes = FakeRandomBytes(),
    )

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
}
