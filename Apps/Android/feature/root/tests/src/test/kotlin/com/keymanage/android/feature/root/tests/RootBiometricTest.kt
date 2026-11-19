package com.keymanage.android.feature.root.tests

import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.OpenedVault
import com.keymanage.android.domain.sync.DeviceAccount
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.auth.AuthListener
import com.keymanage.android.feature.main.MainListener
import com.keymanage.android.feature.root.BiometricGateway
import com.keymanage.android.feature.root.impl.RootInteractor
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

class RootBiometricTest {
    private val account = DeviceAccount("dev@example.com", 600_000, "AAAAAAAAAAAAAAAAAAAAAA==")
    private val vault = VaultData(1, Instant.EPOCH, emptyList())
    private val biometrics = RecordingBiometrics()

    private val routing = object : RootRouting {
        override fun showAuth(listener: AuthListener) = Unit
        override fun showUnlock(email: String, session: Session?, listener: UnlockListener) = Unit
        override fun showMain(vault: VaultData, isOffline: Boolean, email: String, listener: MainListener) = Unit
    }

    private fun interactor(gateway: BiometricGateway? = biometrics) = RootInteractor(
        EmptyPresenter(), ViewStateStore(RootViewState()), ViewActionStream(), FakeVaultSessionGateway(), UnconfinedTestDispatcher(), routing,
        biometrics = gateway,
    )

    @Test
    fun `생체 해제를 켜면 지금 든 볼트 키를 감싼다`() = runTest {
        val interactor = interactor()
        interactor.onUnlocked(OpenedVault(account, vault, null, ByteArray(32) { 5 }))
        interactor.enable()
        assertContentEquals(ByteArray(32) { 5 }, biometrics.sealed.single())
        assertTrue(interactor.isEnabled)
    }

    @Test
    fun `끄면 감싼 키를 지운다`() {
        interactor().disable()
        assertEquals(1, biometrics.disableCalls)
    }

    @Test
    fun `생체 기능이 없으면 쓸 수 없다고 알린다`() {
        val interactor = interactor(gateway = null)
        assertFalse(interactor.isAvailable)
        assertFalse(interactor.isEnabled)
    }

    private class RecordingBiometrics : BiometricGateway {
        val sealed = mutableListOf<ByteArray>()
        var disableCalls = 0
        override val isAvailable = true
        override val isEnabled get() = sealed.isNotEmpty() && disableCalls == 0

        override suspend fun enable(encryptionKey: ByteArray) {
            sealed += encryptionKey.copyOf()
        }

        override fun disable() {
            disableCalls++
        }
    }
}
