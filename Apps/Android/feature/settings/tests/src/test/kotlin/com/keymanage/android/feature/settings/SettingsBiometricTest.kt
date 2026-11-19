package com.keymanage.android.feature.settings

import com.keymanage.android.domain.lock.BiometricError
import com.keymanage.android.feature.settings.impl.SettingsInteractor
import com.keymanage.android.feature.settings.impl.SettingsMessages
import com.keymanage.android.feature.settings.impl.SettingsPresenter
import com.keymanage.android.feature.settings.impl.SettingsViewState
import com.keymanage.android.feature.settings.testing.MemoryAutoLockStore
import com.keymanage.android.feature.settings.testing.RecordingSettingsListener
import com.keymanage.android.feature.settings.testing.StubBiometricPort
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsBiometricTest {
    private val biometric = StubBiometricPort()
    private val store = ViewStateStore(SettingsViewState(email = "dev@example.com"))

    private fun interactor(port: BiometricPort? = biometric) = SettingsInteractor(
        SettingsPresenter(store, ViewActionStream()), store, ViewActionStream(), MemoryAutoLockStore(), { false }, RecordingSettingsListener(),
        workDispatcher = UnconfinedTestDispatcher(), biometric = port,
    )

    @Test
    fun `기기에서 쓸 수 있을 때만 생체 인증 스위치를 보이고 현재 값을 읽는다`() {
        biometric.isEnabled = true
        interactor()
        assertTrue(store.current.biometric.isAvailable)
        assertTrue(store.current.biometric.isEnabled)
        biometric.isAvailable = false
        interactor()
        assertFalse(store.current.biometric.isAvailable)
        interactor(port = null)
        assertFalse(store.current.biometric.isAvailable)
    }

    @Test
    fun `켜면 생체 인증을 거쳐 켜짐으로 바꾼다`() = runTest {
        interactor().toggleBiometric(true)
        assertEquals(1, biometric.enableCalls)
        assertTrue(store.current.biometric.isEnabled)
        assertFalse(store.current.biometric.isWorking)
    }

    @Test
    fun `켜다 취소하면 꺼진 채로 두고 문구를 보이지 않는다`() = runTest {
        biometric.failure = BiometricError.Cancelled()
        interactor().toggleBiometric(true)
        assertFalse(store.current.biometric.isEnabled)
        assertNull(store.current.biometric.message)
    }

    @Test
    fun `켤 수 없으면 이유를 알린다`() = runTest {
        biometric.failure = BiometricError.Unavailable()
        interactor().toggleBiometric(true)
        assertEquals(SettingsMessages.BIOMETRIC_UNAVAILABLE, store.current.biometric.message)
        assertFalse(store.current.biometric.isEnabled)
    }

    @Test
    fun `끄면 감싼 키를 지운다`() = runTest {
        biometric.isEnabled = true
        interactor().toggleBiometric(false)
        assertEquals(1, biometric.disableCalls)
        assertFalse(store.current.biometric.isEnabled)
    }
}
