package com.keymanage.domain.session

import com.keymanage.domain.auth.ChangePasswordRequest
import com.keymanage.domain.auth.ChangedPassword
import com.keymanage.domain.vault.RemoteVault
import com.keymanage.domain.vault.addItem
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 원본 Promise는 취소되지 않는다. Kotlin에서는 부른 쪽(화면) scope가 취소되면 요청 도중에 멈출 수 있다 (코드 리뷰 지적)

private const val NEW_PASSWORD = "a brand new master password"

class VaultSessionCancellationTest {
    @Test
    fun changePasswordCancelledAfterRequestStillAdoptsNewCredentials() = sessionTest {
        // 서버는 바꿨지만 응답이 아직 오지 않은 동안 화면이 닫힌다
        val gate = Gate()
        val authApi = object : FakeAuthApi() {
            override suspend fun changePassword(accessToken: String, request: ChangePasswordRequest): ChangedPassword {
                val changed = super.changePassword(accessToken, request)
                gate.pass()
                return changed
            }
        }
        val ctx = setup(authApi = authApi)
        ctx.session.login("user@example.com", PASSWORD)
        ctx.session.save(addItem(ctx.unlockedState().vault.data, fields, NOW, "a"))
        val changing = launch(start = CoroutineStart.UNDISPATCHED) {
            ctx.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)
        }
        waitFor { gate.waiting > 0 }

        changing.cancel()
        gate.release()
        changing.join()

        // 서버에서 바뀐 KDF 값이 기기 기록에 반영되고, 세션은 새 토큰으로 이어진다
        waitFor { ctx.deviceStore.record?.kdf?.salt == ctx.authApi.kdf.salt }
        val state = ctx.unlockedState()
        assertTrue(state.signedIn)
        assertEquals(listOf("a"), activeIds(state))
    }

    @Test
    fun reconnectCancelledWhileSyncingRevokesNewToken() = sessionTest {
        val ctx = unlockedWithItem()
        val next = restart(ctx)
        next.authApi.offline = true
        next.vaultApi.offline = true
        next.session.unlock(PASSWORD)
        next.authApi.offline = false
        next.vaultApi.offline = false
        val gate = Gate()
        next.vaultApi.getHook = {
            gate.pass()
            next.vaultApi.payload?.let { RemoteVault(next.vaultApi.revision, it) }
        }
        val reconnecting = launch(start = CoroutineStart.UNDISPATCHED) { next.session.reconnect(PASSWORD) }
        waitFor { gate.waiting > 0 }

        reconnecting.cancel()
        reconnecting.join()

        // 세션에 넣지 않은 새 토큰은 남겨 두지 않고 폐기한다
        waitFor { next.authApi.loggedOut.isNotEmpty() }
        assertEquals(1, next.authApi.loggedOut.size)
        assertTrue(!next.unlockedState().signedIn)
        gate.release()
    }
}
