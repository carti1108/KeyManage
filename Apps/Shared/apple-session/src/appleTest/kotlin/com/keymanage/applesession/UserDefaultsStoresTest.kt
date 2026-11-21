package com.keymanage.applesession

import com.keymanage.domain.lock.BiometricSetting
import com.keymanage.domain.lock.InvalidAutoLockTimeoutException
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class UserDefaultsStoresTest {
    private val suite = "com.keymanage.applesession.test." + NSUUID().UUIDString
    private val defaults = NSUserDefaults(suiteName = suite)

    @AfterTest
    fun cleanUp() = defaults.removePersistentDomainForName(suite)

    @Test
    fun autoLock_noSavedValue_readsDefaultFiveMinutes() {
        assertEquals(5.minutes, UserDefaultsAutoLockSettingStore(defaults).read())
    }

    @Test
    fun autoLock_written_isReadByNewStore() {
        UserDefaultsAutoLockSettingStore(defaults).write(15.minutes)

        assertEquals(15.minutes, UserDefaultsAutoLockSettingStore(defaults).read())
    }

    @Test
    fun autoLock_notAnOption_throwsAndKeepsValue() {
        val store = UserDefaultsAutoLockSettingStore(defaults)

        assertFailsWith<InvalidAutoLockTimeoutException> { store.write(7.seconds) }
        assertEquals(5.minutes, store.read())
    }

    @Test
    fun biometric_noSavedValue_isOff() {
        assertEquals(BiometricSetting(), UserDefaultsBiometricSettingStore(defaults).read())
    }

    @Test
    fun biometric_written_isReadBack() {
        val setting = BiometricSetting(enabled = true, lastPasswordUnlock = Instant.fromEpochMilliseconds(1_700_000_000_123), passwordRequired = true)

        UserDefaultsBiometricSettingStore(defaults).write(setting)

        assertEquals(setting, UserDefaultsBiometricSettingStore(defaults).read())
    }

    @Test
    fun biometric_lastUnlockCleared_removesTime() {
        val store = UserDefaultsBiometricSettingStore(defaults)
        store.write(BiometricSetting(enabled = true, lastPasswordUnlock = Instant.fromEpochMilliseconds(1)))

        store.write(BiometricSetting(enabled = true, lastPasswordUnlock = null))

        assertEquals(null, store.read().lastPasswordUnlock)
    }
}
