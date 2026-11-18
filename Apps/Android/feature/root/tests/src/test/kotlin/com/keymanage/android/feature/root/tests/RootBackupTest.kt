package com.keymanage.android.feature.root.tests

import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.OpenedVault
import com.keymanage.android.domain.sync.DeviceAccount
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultItem
import com.keymanage.android.feature.auth.AuthListener
import com.keymanage.android.feature.main.MainListener
import com.keymanage.android.feature.root.impl.RootInteractor
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RootBackupTest {
    private val t0 = Instant.EPOCH
    private val account = DeviceAccount("dev@example.com", 600_000, "AAAAAAAAAAAAAAAAAAAAAA==")
    private val gateway = FakeVaultSessionGateway()

    private object NoRouting : RootRouting {
        override fun showAuth(listener: AuthListener) = Unit
        override fun showUnlock(email: String, session: Session?, listener: UnlockListener) = Unit
        override fun showMain(vault: VaultData, isOffline: Boolean, email: String, listener: MainListener) = Unit
    }

    private val interactor = RootInteractor(
        EmptyPresenter(), ViewStateStore(RootViewState()), ViewActionStream(), gateway, UnconfinedTestDispatcher(), NoRouting,
    )

    private fun vault(vararg ids: String) =
        VaultData(1, t0, ids.map { VaultItem(id = it, serviceName = it, username = null, password = null, createdAt = t0, updatedAt = t0) })

    @Test
    fun `내보내기는 마지막으로 저장한 볼트를 쓴다`() {
        interactor.onUnlocked(OpenedVault(account, vault("a"), null, ByteArray(32)))
        assertEquals("backup:1", String(interactor.export(t0)))
        interactor.save(vault("a", "b"))
        assertEquals("backup:2", String(interactor.export(t0)))
    }

    @Test
    fun `복원은 게이트웨이에 넘기고 되돌린 볼트를 돌려준다`() {
        interactor.onUnlocked(OpenedVault(account, vault("a"), null, ByteArray(32)))
        val outcome = interactor.restore(ByteArray(0), "pw", t0)
        assertEquals(listOf("pw"), gateway.restoreCalls)
        assertEquals(1, outcome.restored)
        assertEquals(listOf("a"), outcome.vault?.items?.map { it.id })
    }

    @Test
    fun `잠긴 동안에는 내보내지 않는다`() {
        assertFailsWith<IllegalStateException> { interactor.export(t0) }
    }
}
