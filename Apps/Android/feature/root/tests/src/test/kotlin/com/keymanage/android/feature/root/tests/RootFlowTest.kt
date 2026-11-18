package com.keymanage.android.feature.root.tests

import com.keymanage.android.domain.auth.AuthResult
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.OpenedVault
import com.keymanage.android.domain.sync.DeviceAccount
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.auth.AuthListener
import com.keymanage.android.feature.root.impl.RootInteractor
import com.keymanage.android.feature.root.impl.RootMessages
import com.keymanage.android.feature.root.impl.RootRouting
import com.keymanage.android.feature.root.impl.RootViewState
import com.keymanage.android.feature.root.testing.FakeVaultSessionGateway
import com.keymanage.android.feature.unlock.UnlockListener
import com.keymanage.android.feature.vaultlist.VaultListListener
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.EmptyPresenter
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class RootFlowTest {
    private val account = DeviceAccount("dev@example.com", 600_000, "AAAAAAAAAAAAAAAAAAAAAA==")
    private val session = Session("tok", Instant.parse("2026-12-18T00:00:00Z"))
    private val emptyVault = VaultData(1, Instant.EPOCH, emptyList())

    /** 붙인 화면을 기록한다. */
    private class RecordingRouting : RootRouting {
        val screens = mutableListOf<String>()
        var unlockSession: Session? = null
        override fun showAuth(listener: AuthListener) {
            screens += "auth"
        }

        override fun showUnlock(email: String, session: Session?, listener: UnlockListener) {
            screens += "unlock:$email"
            unlockSession = session
        }

        override fun showVaultList(vault: VaultData, isOffline: Boolean, listener: VaultListListener) {
            screens += if (isOffline) "vault:offline" else "vault"
        }
    }

    private val routing = RecordingRouting()
    private val gateway = FakeVaultSessionGateway()
    private val store = ViewStateStore(RootViewState())
    private val interactor = RootInteractor(EmptyPresenter(), store, ViewActionStream(), gateway, UnconfinedTestDispatcher(), routing)

    private fun opened(key: ByteArray = ByteArray(32) { 7 }, withSession: Session? = session) =
        OpenedVault(account, emptyVault, withSession, key)

    @Test
    fun `기기 기록이 없으면 로그인, 있으면 그 계정의 잠금 화면으로 시작한다`() = runTest {
        interactor.start()
        gateway.lockedEmail = "dev@example.com"
        interactor.start()
        assertEquals(listOf("auth", "unlock:dev@example.com"), routing.screens)
    }

    @Test
    fun `인증 뒤 볼트를 열어 목록을 보인다`() = runTest {
        // Given
        val vault = opened()
        gateway.open = { vault }

        // When
        interactor.openAfterAuth(AuthResult(session, vault.encryptionKey, account))

        // Then
        assertEquals(listOf("vault"), routing.screens)
    }

    @Test
    fun `볼트를 열지 못하면 키를 지우고 로그인 화면에 문구를 남긴다`() = runTest {
        // Given
        gateway.open = { throw IOException("x") }
        val key = ByteArray(32) { 7 }

        // When
        interactor.openAfterAuth(AuthResult(session, key, account))

        // Then
        assertContentEquals(ByteArray(32), key)
        assertEquals(RootMessages.VAULT_NOT_READY, store.current.notice)
        assertEquals(listOf("auth"), routing.screens)
    }

    @Test
    fun `잠그면 키를 0으로 덮어쓰고 세션은 남긴 채 잠금 화면으로 간다`() {
        // Given
        val key = ByteArray(32) { 7 }
        interactor.onUnlocked(opened(key))

        // When
        interactor.onLockRequested()

        // Then
        assertContentEquals(ByteArray(32), key)
        assertEquals(listOf("vault", "unlock:dev@example.com"), routing.screens)
        assertEquals(session, routing.unlockSession)
    }

    @Test
    fun `세션 없이 열린 볼트는 오프라인 목록으로 보인다`() {
        interactor.onUnlocked(opened(withSession = null))
        assertEquals(listOf("vault:offline"), routing.screens)
    }

    @Test
    fun `비밀번호가 바뀌었으면 문구와 함께 로그인 화면으로 간다`() {
        interactor.onLoginRequired("바뀜")
        assertEquals("바뀜", store.current.notice)
        assertEquals(listOf("auth"), routing.screens)
    }

    @Test
    fun `로그아웃은 세션을 넘겨 기기 기록을 지우고 로그인 화면으로 간다`() = runTest {
        // Given
        interactor.onUnlocked(opened())
        interactor.onLockRequested()

        // When
        interactor.logout()

        // Then
        assertEquals(listOf<Session?>(session), gateway.logouts)
        assertEquals("auth", routing.screens.last())
    }
}
