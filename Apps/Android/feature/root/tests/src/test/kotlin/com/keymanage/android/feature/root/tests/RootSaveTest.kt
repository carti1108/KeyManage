package com.keymanage.android.feature.root.tests

import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.OpenedVault
import com.keymanage.android.domain.sync.DeviceAccount
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.auth.AuthListener
import com.keymanage.android.feature.root.impl.RootInteractor
import com.keymanage.android.feature.root.impl.RootRouting
import com.keymanage.android.feature.root.impl.RootViewState
import com.keymanage.android.feature.root.testing.FakeVaultSessionGateway
import com.keymanage.android.feature.unlock.UnlockListener
import com.keymanage.android.feature.main.MainListener
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.EmptyPresenter
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RootSaveTest {
    private val account = DeviceAccount("dev@example.com", 600_000, "AAAAAAAAAAAAAAAAAAAAAA==")
    private val vault = VaultData(1, Instant.EPOCH, emptyList())
    private val gateway = FakeVaultSessionGateway()
    private var burns = 0

    private object NoRouting : RootRouting {
        override fun showAuth(listener: AuthListener) = Unit
        override fun showUnlock(email: String, session: Session?, listener: UnlockListener) = Unit
        override fun showMain(vault: VaultData, isOffline: Boolean, email: String, listener: MainListener) = Unit
    }

    private val interactor = RootInteractor(
        EmptyPresenter(), ViewStateStore(RootViewState()), ViewActionStream(), gateway, UnconfinedTestDispatcher(), NoRouting,
        onLocked = { burns++ },
    )

    @Test
    fun `잠금이 풀린 동안의 저장은 열린 볼트로 게이트웨이에 넘긴다`() {
        interactor.onUnlocked(OpenedVault(account, vault, null, ByteArray(32)))
        interactor.save(vault)
        assertEquals(listOf(vault), gateway.saved)
    }

    @Test
    fun `잠긴 동안에는 저장하지 않는다`() {
        assertFailsWith<IllegalStateException> { interactor.save(vault) }
    }

    @Test
    fun `잠그면 클립보드를 바로 비운다`() {
        interactor.onUnlocked(OpenedVault(account, vault, null, ByteArray(32)))
        interactor.onLockRequested()
        assertEquals(1, burns)
    }
}
