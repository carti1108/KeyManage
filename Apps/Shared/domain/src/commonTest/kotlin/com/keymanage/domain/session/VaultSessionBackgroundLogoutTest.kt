package com.keymanage.domain.session

import com.keymanage.domain.device.DeviceStorageException
import com.keymanage.domain.server.NetworkError
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 서버 토큰 폐기를 기다리지 않는 로그아웃 (SEC-002, ADR-0019). 로컬 정리는 끝내고 돌아오며, 폐기는 세션 범위(deps.scope)에서 끝까지 한다

class VaultSessionBackgroundLogoutTest {
    @Test
    fun returnsAfterLocalCleanupWithoutWaitingForTokenRevocation() = sessionTest {
        val ctx = unlockedWithItem()
        val gate = Gate()
        val revoked = mutableListOf<String>()
        ctx.authApi.logoutHook = { token ->
            gate.pass()
            revoked += token
        }

        ctx.session.logoutAndRevokeInBackground()

        // 서버가 아직 응답하지 않아도 이 기기는 로그아웃이 끝나 있다
        assertEquals(SessionState.SignedOut, ctx.session.state())
        assertNull(ctx.deviceStore.stored)
        assertEquals(1, ctx.locks())
        assertTrue(revoked.isEmpty())

        gate.release()
        waitFor { revoked.isNotEmpty() }
        assertEquals(listOf("token-1"), revoked)
    }

    @Test
    fun revokeRequestIsSentEvenIfCallerDoesNotWait() = sessionTest {
        val ctx = unlockedWithItem()

        ctx.session.logoutAndRevokeInBackground()

        waitFor { ctx.authApi.loggedOut.isNotEmpty() }
        assertEquals(listOf("token-1"), ctx.authApi.loggedOut)
    }

    @Test
    fun serverFailureOnRevokeIsNotReported() = sessionTest {
        val ctx = unlockedWithItem()
        var attempted = false
        ctx.authApi.logoutHook = {
            attempted = true
            throw NetworkError()
        }

        ctx.session.logoutAndRevokeInBackground()
        waitFor { attempted }

        assertEquals(SessionState.SignedOut, ctx.session.state())
        assertNull(ctx.deviceStore.stored)
    }

    @Test
    fun reportsRecordClearFailureAndStillRevokesToken() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.deviceStore.failing = true

        assertFailsWith<DeviceStorageException> { ctx.session.logoutAndRevokeInBackground() }

        assertEquals(SessionState.SignedOut, ctx.session.state())
        waitFor { ctx.authApi.loggedOut.isNotEmpty() }
        assertEquals(listOf("token-1"), ctx.authApi.loggedOut)
    }

    @Test
    fun logoutFromLockScreenWithoutTokenOnlyClearsRecord() = sessionTest {
        val ctx = unlockedWithItem()
        // 다시 시작하면 기기 기록으로 잠금 화면만 열려 세션 토큰이 없다
        val next = restart(ctx)

        next.session.logoutAndRevokeInBackground()

        assertNull(next.deviceStore.stored)
        assertEquals(SessionState.SignedOut, next.session.state())
        // 폐기할 토큰이 없으니 서버 요청을 하지 않는다
        repeat(20) { yield() }
        assertTrue(next.authApi.loggedOut.isEmpty())
    }
}
