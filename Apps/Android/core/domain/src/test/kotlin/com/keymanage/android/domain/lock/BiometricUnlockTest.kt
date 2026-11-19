package com.keymanage.android.domain.lock

import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BiometricUnlockTest {
    private val t0 = Instant.parse("2026-11-19T00:00:00Z")
    private var now = t0
    private val keys = FakeKeyStore()
    private val settings = MemorySettings()
    private val unlock = BiometricUnlock(keys, settings) { now }

    private suspend fun enabled() {
        unlock.onPasswordUnlock()
        unlock.enable(byteArrayOf(7, 7))
    }

    @Test
    fun `기본은 꺼져 있고 생체 해제를 제안하지 않는다`() {
        assertFalse(unlock.isEnabled)
        assertFalse(unlock.canUseBiometrics())
    }

    @Test
    fun `켜면 볼트 키를 감싸 두고 생체 해제로 같은 키를 돌려준다`() = runTest {
        enabled()
        assertTrue(unlock.isEnabled)
        assertTrue(unlock.canUseBiometrics())
        assertContentEquals(byteArrayOf(7, 7), unlock.recoverKey())
    }

    @Test
    fun `생체 인증을 취소하면 켜지 않는다`() = runTest {
        keys.failure = BiometricError.Cancelled()
        unlock.onPasswordUnlock()
        assertFailsWith<BiometricError.Cancelled> { unlock.enable(byteArrayOf(1)) }
        assertFalse(unlock.isEnabled)
    }

    @Test
    fun `마지막 마스터 비밀번호 해제 뒤 14일까지는 생체로 풀고 넘으면 비밀번호를 요구한다`() = runTest {
        enabled()
        now = t0.plus(Duration.ofDays(14))
        assertTrue(unlock.canUseBiometrics())
        now = now.plusSeconds(1)
        assertFalse(unlock.canUseBiometrics())
        assertFailsWith<BiometricError.Expired> { unlock.recoverKey() }
        assertTrue(unlock.isEnabled, "키는 남겨 두고 비밀번호만 요구한다")
        unlock.onPasswordUnlock()
        assertTrue(unlock.canUseBiometrics())
    }

    @Test
    fun `생체 정보가 바뀌어 키가 무효가 되면 지우고 끈다`() = runTest {
        enabled()
        keys.failure = BiometricError.Invalidated()
        assertFailsWith<BiometricError.Invalidated> { unlock.recoverKey() }
        assertFalse(unlock.isEnabled)
        assertFalse(keys.hasKey())
    }

    @Test
    fun `기기 암호를 꺼서 키가 사라져도 끄고 비밀번호를 요구한다`() = runTest {
        enabled()
        keys.sealed = null
        assertFalse(unlock.canUseBiometrics())
        assertFailsWith<BiometricError.Invalidated> { unlock.recoverKey() }
        assertFalse(unlock.isEnabled)
    }

    @Test
    fun `취소는 생체 설정을 그대로 둔다`() = runTest {
        enabled()
        keys.failure = BiometricError.Cancelled()
        assertFailsWith<BiometricError.Cancelled> { unlock.recoverKey() }
        assertTrue(unlock.isEnabled)
    }

    @Test
    fun `끄면 감싼 키를 지운다`() = runTest {
        enabled()
        unlock.disable()
        assertFalse(unlock.isEnabled)
        assertNull(keys.sealed)
        assertFailsWith<BiometricError.Disabled> { unlock.recoverKey() }
    }

    @Test
    fun `마스터 비밀번호로 푼 적이 없으면 생체 해제를 쓰지 않는다`() = runTest {
        unlock.enable(byteArrayOf(1))
        assertFalse(unlock.canUseBiometrics())
        assertFailsWith<BiometricError.Expired> { unlock.recoverKey() }
    }

    @Test
    fun `저장한 설정은 다시 만들어도 남는다`() = runTest {
        enabled()
        val again = BiometricUnlock(keys, settings) { now }
        assertTrue(again.canUseBiometrics())
        assertEquals(t0, settings.setting.lastPasswordUnlock)
    }

    private class FakeKeyStore : BiometricKeyStore {
        var sealed: ByteArray? = null
        var failure: BiometricError? = null

        override fun hasKey() = sealed != null

        override suspend fun seal(key: ByteArray) {
            failure?.let { throw it }
            sealed = key.copyOf()
        }

        override suspend fun open(): ByteArray {
            failure?.let { throw it }
            return checkNotNull(sealed).copyOf()
        }

        override fun clear() {
            sealed = null
        }
    }

    private class MemorySettings : BiometricSettingStore {
        var setting = BiometricSetting()
        override fun read() = setting
        override fun write(setting: BiometricSetting) {
            this.setting = setting
        }
    }
}
