package com.keymanage.android.feature.root.tests

import com.keymanage.android.domain.auth.AuthResult
import com.keymanage.android.domain.lock.BiometricError
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.OpenedVault
import com.keymanage.android.domain.sync.DeviceAccount
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.auth.AuthListener
import com.keymanage.android.feature.main.MainListener
import com.keymanage.android.feature.root.BiometricGateway
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 가입 직후 생체 인증을 켤지 묻는다(SEC-003 값 표). */
class RootSignupBiometricTest {
    private val account = DeviceAccount("dev@example.com", 600_000, "AAAAAAAAAAAAAAAAAAAAAA==")
    private val session = Session("tok", Instant.parse("2026-12-18T00:00:00Z"))
    private val vault = VaultData(1, Instant.EPOCH, emptyList())
    private val biometrics = Biometrics()
    private val gateway = FakeVaultSessionGateway(open = { OpenedVault(account, vault, session, ByteArray(32) { 3 }) })
    private val store = ViewStateStore(RootViewState())

    private val routing = object : RootRouting {
        override fun showAuth(listener: AuthListener) = Unit
        override fun showUnlock(email: String, session: Session?, listener: UnlockListener) = Unit
        override fun showMain(vault: VaultData, isOffline: Boolean, email: String, listener: MainListener) = Unit
    }

    private val interactor = RootInteractor(
        EmptyPresenter(), store, ViewActionStream(), gateway, UnconfinedTestDispatcher(), routing, biometrics = biometrics,
    )

    private fun result(isNewAccount: Boolean) = AuthResult(session, ByteArray(32) { 3 }, account, isNewAccount)

    @Test
    fun `가입 직후 생체 인증을 쓸 수 있으면 켤지 묻는다`() = runTest {
        interactor.openAfterAuth(result(isNewAccount = true))
        assertTrue(store.current.isAskingBiometrics)
    }

    @Test
    fun `로그인이거나 기기에서 쓸 수 없으면 묻지 않는다`() = runTest {
        interactor.openAfterAuth(result(isNewAccount = false))
        assertFalse(store.current.isAskingBiometrics)
        biometrics.isAvailable = false
        interactor.openAfterAuth(result(isNewAccount = true))
        assertFalse(store.current.isAskingBiometrics)
    }

    @Test
    fun `켜기를 고르면 볼트 키를 감싸고 안내를 닫는다`() = runTest {
        interactor.openAfterAuth(result(isNewAccount = true))
        interactor.answerBiometricPrompt(enable = true)
        assertContentEquals(ByteArray(32) { 3 }, biometrics.sealed.single())
        assertFalse(store.current.isAskingBiometrics)
    }

    @Test
    fun `나중에를 고르면 켜지 않는다`() = runTest {
        interactor.openAfterAuth(result(isNewAccount = true))
        interactor.answerBiometricPrompt(enable = false)
        assertTrue(biometrics.sealed.isEmpty())
        assertFalse(store.current.isAskingBiometrics)
    }

    @Test
    fun `켜다 취소하면 조용히 닫고 쓸 수 없으면 안내한다`() = runTest {
        interactor.openAfterAuth(result(isNewAccount = true))
        biometrics.failure = BiometricError.Cancelled()
        interactor.answerBiometricPrompt(enable = true)
        assertNull(store.current.notice)
        interactor.openAfterAuth(result(isNewAccount = true))
        biometrics.failure = BiometricError.Unavailable()
        interactor.answerBiometricPrompt(enable = true)
        assertEquals(RootMessages.BIOMETRIC_UNAVAILABLE, store.current.notice)
        assertFalse(store.current.isAskingBiometrics)
    }

    private class Biometrics : BiometricGateway {
        val sealed = mutableListOf<ByteArray>()
        var failure: Exception? = null
        override var isAvailable = true
        override val isEnabled get() = sealed.isNotEmpty()

        override suspend fun enable(encryptionKey: ByteArray) {
            failure?.let { throw it }
            sealed += encryptionKey.copyOf()
        }

        override fun disable() = sealed.clear()
    }
}
