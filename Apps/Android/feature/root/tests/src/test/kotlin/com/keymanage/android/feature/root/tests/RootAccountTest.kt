package com.keymanage.android.feature.root.tests

import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.OpenedVault
import com.keymanage.android.domain.sync.DeviceAccount
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
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class RootAccountTest {
    private val account = DeviceAccount("dev@example.com", 600_000, "AAAAAAAAAAAAAAAAAAAAAA==")
    private val vault = VaultData(1, Instant.EPOCH, emptyList())
    private val oldSession = Session("old", Instant.parse("2026-12-18T00:00:00Z"))
    private val newSession = Session("new", Instant.parse("2026-12-18T00:00:00Z"))
    private val gateway = FakeVaultSessionGateway()
    private val screens = mutableListOf<String>()
    private var unlockSession: Session? = null

    private val routing = object : RootRouting {
        override fun showAuth(listener: AuthListener) {
            screens += "auth"
        }

        override fun showUnlock(email: String, session: Session?, listener: UnlockListener) {
            screens += "unlock"
            unlockSession = session
        }

        override fun showMain(vault: VaultData, isOffline: Boolean, email: String, listener: MainListener) {
            screens += "main"
        }
    }

    private val store = ViewStateStore(RootViewState())
    private val interactor = RootInteractor(EmptyPresenter(), store, ViewActionStream(), gateway, UnconfinedTestDispatcher(), routing)

    @Test
    fun `비밀번호를 바꾸면 옛 키를 지우고 새 세션을 든다`() {
        // Given
        val oldKey = ByteArray(32) { 7 }
        interactor.onUnlocked(OpenedVault(account, vault, oldSession, oldKey))
        gateway.changed = { OpenedVault(account, vault, newSession, ByteArray(32) { 9 }) }

        // When
        interactor.changePassword("current", "new-password!", "new-password!")
        interactor.onLockRequested()

        // Then
        assertContentEquals(ByteArray(32), oldKey)
        assertEquals(newSession, unlockSession)
    }

    @Test
    fun `계정을 지우면 완료 안내와 함께 로그인 화면으로 간다`() {
        interactor.onUnlocked(OpenedVault(account, vault, oldSession, ByteArray(32)))
        interactor.deleteAccount("pw")
        interactor.onAccountDeleted()
        assertEquals(listOf("pw"), gateway.deletePasswords)
        assertEquals(RootMessages.ACCOUNT_DELETED, store.current.notice)
        assertEquals("auth", screens.last())
    }

    @Test
    fun `세션이 만료되면 문구와 함께 로그인 화면으로 간다`() {
        interactor.onSessionExpired()
        assertEquals(RootMessages.SESSION_EXPIRED, store.current.notice)
        assertEquals(listOf("auth"), screens)
    }
}
