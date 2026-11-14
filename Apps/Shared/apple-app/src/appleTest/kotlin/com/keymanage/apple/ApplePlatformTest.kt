package com.keymanage.apple

import com.keymanage.domain.backup.LocalDay
import com.keymanage.securestore.BiometricSecureStore
import platform.Foundation.NSTimeZone
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeZoneForSecondsFromGMT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ApplePlatformTest {
    @Test
    fun calendarUsesGivenTimeZone() {
        // 2026-11-13T15:30Z는 서울(+9)에서 11월 14일이다
        val seoul = NSTimeZone.timeZoneForSecondsFromGMT(9 * 3600)
        val calendar = TimeZoneCalendar { seoul }

        assertEquals(LocalDay(2026, 11, 14), calendar.dateOf(Instant.parse("2026-11-13T15:30:00Z")))
    }

    @Test
    fun forgetAllRemovesEveryEnrolledAccountAndItsKeychainItem() {
        val suite = "com.keymanage.ios.test.${platform.Foundation.NSUUID().UUIDString}"
        val defaults = NSUserDefaults(suiteName = suite)
        val store = DeletionRecordingStore()
        val unlock = KeychainBiometricUnlock(store = store, defaults = defaults)
        defaults.setObject("1", "keymanage.biometric.passwordUnlockedAt.a@test.com")
        defaults.setObject("2", "keymanage.biometric.passwordUnlockedAt.b@test.com")
        defaults.setObject("keep", "other.key")

        unlock.forgetAll()

        assertEquals(setOf("a@test.com", "b@test.com"), store.deleted.toSet())
        assertNull(unlock.passwordUnlockedAt("a@test.com"))
        assertNull(unlock.passwordUnlockedAt("b@test.com"))
        assertEquals("keep", defaults.stringForKey("other.key"))
        NSUserDefaults.standardUserDefaults.removePersistentDomainForName(suite)
    }

    @Test
    fun backupStagingDirectoryExists() {
        val directory = backupStagingDirectory()

        assertEquals(true, platform.Foundation.NSFileManager.defaultManager.fileExistsAtPath(directory))
    }
}

private class DeletionRecordingStore : BiometricSecureStore {
    val deleted = mutableListOf<String>()
    override suspend fun saveProtected(account: String, value: ByteArray) = Unit
    override suspend fun loadProtected(account: String, reason: String): ByteArray? = null
    override fun delete(account: String) {
        deleted += account
    }
}
