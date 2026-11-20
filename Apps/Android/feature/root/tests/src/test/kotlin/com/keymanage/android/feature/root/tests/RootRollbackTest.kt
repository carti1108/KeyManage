package com.keymanage.android.feature.root.tests

import com.keymanage.android.domain.auth.AuthResult
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.OpenedVault
import com.keymanage.android.domain.sync.DeviceAccount
import com.keymanage.android.domain.sync.SyncError
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.auth.AuthListener
import com.keymanage.android.feature.main.MainListener
import com.keymanage.android.feature.root.impl.RootInteractor
import com.keymanage.android.feature.root.impl.RootMessages
import com.keymanage.android.feature.root.impl.RootRouting
import com.keymanage.android.feature.root.impl.RootViewState
import com.keymanage.android.feature.root.testing.FakeVaultSessionGateway
import com.keymanage.android.feature.unlock.UnlockListener
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.EmptyPresenter
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** VLT-001 규칙 9: 로그인 직후 서버 볼트가 되돌아가 있으면 사용자가 확인한 경우에만 받아들인다. */
class RootRollbackTest {
    private val account = DeviceAccount("dev@example.com", 600_000, "AAAAAAAAAAAAAAAAAAAAAA==")
    private val session = Session("tok", Instant.parse("2026-12-18T00:00:00Z"))
    private val key = ByteArray(32) { 7 }
    private val screens = mutableListOf<String>()
    private val routing = object : RootRouting {
        override fun showAuth(listener: AuthListener) {
            screens += "auth"
        }

        override fun showUnlock(email: String, session: Session?, listener: UnlockListener) {
            screens += "unlock"
        }

        override fun showMain(vault: VaultData, isOffline: Boolean, email: String, listener: MainListener) {
            screens += "vault"
        }
    }
    private val gateway = FakeVaultSessionGateway()
    private val store = ViewStateStore(RootViewState())
    private val interactor = RootInteractor(EmptyPresenter(), store, ViewActionStream(), gateway, UnconfinedTestDispatcher(), routing)

    private suspend fun loginIntoRollback() {
        gateway.open = { throw SyncError.RevisionRollback(serverRevision = 2, lastSeenRevision = 5) }
        interactor.openAfterAuth(AuthResult(session, key, account))
    }

    @Test
    fun `되돌림이면 키를 쥔 채 확인을 묻고 받아들이면 볼트를 연다`() = runTest {
        // Given
        loginIntoRollback()
        assertTrue(store.current.isConfirmingRollback)
        assertContentEquals(ByteArray(32) { 7 }, key)
        assertTrue(screens.isEmpty())
        gateway.open = { OpenedVault(account, VaultData(1, Instant.EPOCH, emptyList()), session, key) }

        // When
        interactor.answerRollback(accept = true)

        // Then
        assertEquals(listOf(2), gateway.acceptedRollbacks)
        assertEquals(listOf("vault"), screens)
        assertFalse(store.current.isConfirmingRollback)
    }

    @Test
    fun `취소하면 받아들이지 않고 키를 지운 뒤 로그인 화면으로 간다`() = runTest {
        // Given
        loginIntoRollback()

        // When
        interactor.answerRollback(accept = false)

        // Then
        assertTrue(gateway.acceptedRollbacks.isEmpty())
        assertContentEquals(ByteArray(32), key)
        assertEquals(listOf("auth"), screens)
        assertFalse(store.current.isConfirmingRollback)
    }

    @Test
    fun `되돌림 문구는 정책 원문과 같다`() {
        assertEquals("서버 볼트가 이전 상태로 돌아감", RootMessages.ROLLBACK_TITLE)
        assertEquals(
            "서버의 볼트가 이 기기에서 마지막으로 본 상태보다 이전으로 돌아갔습니다. 서버를 복구한 경우가 아니라면 받아들이지 마세요.",
            RootMessages.ROLLBACK_BODY,
        )
        assertEquals("서버 볼트 받아들이기", RootMessages.ROLLBACK_ACCEPT)
    }
}
