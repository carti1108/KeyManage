package com.keymanage.websession

import com.keymanage.domain.session.SessionState
import com.keymanage.domain.tabs.SingleTab
import com.keymanage.feature.SessionPortAdapter
import com.keymanage.serverhttp.keyManageHttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

// 화면이 쓰는 세션 포트의 로그아웃은 서버 토큰 폐기를 기다리지 않는다 (SEC-002, ADR-0019)

private const val EMAIL = "user@example.com"
private const val PASSWORD = "correct horse battery staple"

class SessionPortAdapterLogoutTest {
    @Test
    fun logoutReturnsAfterLocalCleanupWithoutWaitingForServerRevocation() = runTest(timeout = 2.minutes) {
        val server = FakeServer()
        val store = MemoryDeviceStore()
        val kotlinSession = buildKotlinSession(TEST_BASE_URL, keyManageHttpClient(server.engine), store, SingleTab(), tabMemory = null)
        kotlinSession.register(EMAIL, PASSWORD).await()
        val gate = CompletableDeferred<Unit>()
        server.logoutGate = gate
        val port = SessionPortAdapter(kotlinSession.session)

        port.logout()

        // 서버가 응답하지 않아도 이 기기는 로그아웃이 끝나 있다
        assertEquals(SessionState.SignedOut, port.state())
        assertNull(store.stored)
        assertEquals(0, server.logoutsCompleted)

        // 토큰 폐기는 화면과 무관한 세션 범위에서 끝까지 한다
        gate.complete(Unit)
        repeat(500) {
            if (server.logoutsCompleted == 1) return@runTest
            withContext(Dispatchers.Default) { delay(10) }
        }
        fail("토큰 폐기 요청이 끝나지 않음")
    }
}
