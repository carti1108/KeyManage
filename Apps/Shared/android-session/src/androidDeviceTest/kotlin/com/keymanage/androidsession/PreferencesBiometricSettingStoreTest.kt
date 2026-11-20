package com.keymanage.androidsession

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.keymanage.domain.lock.BiometricSetting
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class PreferencesBiometricSettingStoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "biometric-setting-test"
    private fun prefs() = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun store() = PreferencesBiometricSettingStore(context, name)

    @After
    fun cleanUp() {
        prefs().edit().clear().commit()
    }

    @Test
    fun read_returnsOffWhenNothingSaved() = assertEquals(BiometricSetting(), store().read())

    @Test
    fun read_returnsSettingWrittenByAnotherInstance() {
        val setting = BiometricSetting(enabled = true, lastPasswordUnlock = Instant.parse("2026-11-20T01:02:03.456Z"))

        store().write(setting)

        assertEquals(setting, store().read())
    }

    @Test
    fun read_returnsPasswordRequiredWrittenByAnotherInstance() {
        val setting = BiometricSetting(enabled = true, lastPasswordUnlock = Instant.parse("2026-11-20T00:00:00Z"), passwordRequired = true)

        store().write(setting)

        assertEquals(setting, store().read())
    }

    @Test
    fun write_clearsPasswordRequired() {
        store().write(BiometricSetting(enabled = true, passwordRequired = true))

        store().write(BiometricSetting(enabled = true, passwordRequired = false))

        assertEquals(BiometricSetting(enabled = true), store().read())
    }

    @Test
    fun write_removesLastPasswordUnlockWhenNull() {
        store().write(BiometricSetting(enabled = true, lastPasswordUnlock = Instant.parse("2026-11-20T00:00:00Z")))

        store().write(BiometricSetting(enabled = false, lastPasswordUnlock = null))

        assertEquals(BiometricSetting(), store().read())
    }

    @Test
    fun read_returnsOffWhenSavedValuesHaveAnotherType() {
        prefs().edit().putString("biometricEnabled", "true").putString("lastPasswordUnlockMs", "1").commit()

        assertEquals(BiometricSetting(), store().read())
    }

    @Test
    fun write_doesNotTouchOtherSettings() {
        prefs().edit().putString("autoLockMs", "60000").commit()

        store().write(BiometricSetting(enabled = true))

        assertEquals("60000", prefs().getString("autoLockMs", null))
    }
}
