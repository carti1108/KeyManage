package com.keymanage.android.feature.unlock

import com.keymanage.android.domain.lock.BiometricError
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.OpenedVault
import com.keymanage.android.domain.session.UnlockError
import com.keymanage.android.domain.sync.DeviceAccount
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.unlock.impl.UnlockInteractor
import com.keymanage.android.feature.unlock.impl.UnlockMessages
import com.keymanage.android.feature.unlock.impl.UnlockPresenter
import com.keymanage.android.feature.unlock.impl.UnlockViewState
import com.keymanage.android.feature.unlock.testing.RecordingUnlockListener
import com.keymanage.android.feature.unlock.testing.StubBiometricUnlocker
import com.keymanage.android.feature.unlock.testing.StubUnlocker
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnlockBiometricTest {
    private val session = Session("tok", Instant.parse("2026-12-18T00:00:00Z"))
    private val opened = OpenedVault(
        DeviceAccount("dev@example.com", 600_000, "AAAAAAAAAAAAAAAAAAAAAA=="),
        VaultData(1, Instant.EPOCH, emptyList()),
        session,
        ByteArray(32),
    )
    private val biometric = StubBiometricUnlocker { opened }
    private val listener = RecordingUnlockListener()
    private val store = ViewStateStore(UnlockViewState(email = "dev@example.com"))

    private fun interactor(biometric: StubBiometricUnlocker? = this.biometric) = UnlockInteractor(
        UnlockPresenter(store, ViewActionStream()), store, ViewActionStream(), session, StubUnlocker { opened }, listener,
        UnconfinedTestDispatcher(), biometric,
    )

    @Test
    fun `생체 해제를 쓸 수 있을 때만 버튼을 보인다`() {
        interactor()
        assertTrue(store.current.canUseBiometrics)
        biometric.available = false
        interactor()
        assertFalse(store.current.canUseBiometrics)
        interactor(biometric = null)
        assertFalse(store.current.canUseBiometrics)
    }

    @Test
    fun `생체 인증으로 풀면 잠기기 전 세션으로 열고 부모에 넘긴다`() = runTest {
        interactor().unlockWithBiometrics()
        assertEquals(listOf<Session?>(session), biometric.sessions)
        assertEquals(listOf(opened), listener.unlocked)
        assertFalse(store.current.isSubmitting)
    }

    @Test
    fun `취소하면 아무 문구 없이 그대로 둔다`() = runTest {
        biometric.failure = BiometricError.Cancelled()
        interactor().unlockWithBiometrics()
        assertNull(store.current.errorMessage)
        assertTrue(store.current.canUseBiometrics)
        assertFalse(store.current.isSubmitting)
    }

    @Test
    fun `14일이 지났거나 키가 무효면 버튼을 숨기고 비밀번호를 요구한다`() = runTest {
        val cases = mapOf<Exception, String>(
            BiometricError.Expired() to UnlockMessages.BIOMETRIC_EXPIRED,
            BiometricError.Invalidated() to UnlockMessages.BIOMETRIC_INVALIDATED,
            BiometricError.Unavailable() to UnlockMessages.BIOMETRIC_UNAVAILABLE,
        )
        for ((error, message) in cases) {
            biometric.failure = error
            val interactor = interactor()
            interactor.unlockWithBiometrics()
            assertEquals(message, store.current.errorMessage)
            assertFalse(store.current.canUseBiometrics)
        }
        assertTrue(listener.unlocked.isEmpty())
    }

    @Test
    fun `기기 기록이 없으면 로그인 화면으로 보낸다`() = runTest {
        biometric.failure = UnlockError.NoDeviceRecord()
        interactor().unlockWithBiometrics()
        assertEquals(listOf<String?>(null), listener.loginRequired)
    }
}
