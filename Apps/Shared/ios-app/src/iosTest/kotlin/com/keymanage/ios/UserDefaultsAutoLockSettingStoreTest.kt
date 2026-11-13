package com.keymanage.ios

import com.keymanage.domain.lock.DEFAULT_AUTO_LOCK
import com.keymanage.domain.lock.InvalidAutoLockTimeoutException
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class UserDefaultsAutoLockSettingStoreTest {
    private val suiteName = "keymanage-test-" + NSUUID().UUIDString
    private val defaults = NSUserDefaults(suiteName = suiteName)

    @AfterTest
    fun removeSuite() {
        defaults.removePersistentDomainForName(suiteName)
    }

    @Test
    fun readsDefaultWhenNothingSaved() {
        assertEquals(DEFAULT_AUTO_LOCK, UserDefaultsAutoLockSettingStore(defaults).read())
    }

    @Test
    fun keepsWrittenValueForNextLaunch() {
        UserDefaultsAutoLockSettingStore(defaults).write(1.minutes)

        assertEquals(1.minutes, UserDefaultsAutoLockSettingStore(defaults).read())
    }

    @Test
    fun storesMillisecondsAsDecimalText() {
        UserDefaultsAutoLockSettingStore(defaults).write(1.minutes)

        assertEquals("60000", defaults.stringForKey(AUTO_LOCK_DEFAULTS_KEY))
    }

    @Test
    fun readsDefaultWhenSavedValueIsNotAnOption() {
        defaults.setObject("12345", AUTO_LOCK_DEFAULTS_KEY)

        assertEquals(DEFAULT_AUTO_LOCK, UserDefaultsAutoLockSettingStore(defaults).read())
    }

    @Test
    fun rejectsTimeoutOutsideOptions() {
        assertFailsWith<InvalidAutoLockTimeoutException> {
            UserDefaultsAutoLockSettingStore(defaults).write(7.seconds)
        }
    }
}
