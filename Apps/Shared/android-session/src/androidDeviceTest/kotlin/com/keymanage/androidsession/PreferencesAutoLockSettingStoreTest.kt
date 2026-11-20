package com.keymanage.androidsession

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.keymanage.domain.lock.DEFAULT_AUTO_LOCK
import com.keymanage.domain.lock.InvalidAutoLockTimeoutException
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class PreferencesAutoLockSettingStoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "auto-lock-test"
    private fun prefs() = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun store() = PreferencesAutoLockSettingStore(context, name)

    @After
    fun cleanUp() {
        prefs().edit().clear().commit()
    }

    @Test
    fun read_returnsDefaultWhenNothingSaved() = assertEquals(DEFAULT_AUTO_LOCK, store().read())

    @Test
    fun read_returnsValueWrittenByAnotherInstance() {
        store().write(15.minutes)

        assertEquals(15.minutes, store().read())
    }

    @Test
    fun write_storesImmediateOption() {
        store().write(Duration.ZERO)

        assertEquals(Duration.ZERO, store().read())
    }

    @Test
    fun write_savesCanonicalMillisecondString() {
        store().write(1.minutes)

        assertEquals("60000", prefs().getString("autoLockMs", null))
    }

    @Test
    fun write_rejectsValueOutsideOptions() {
        val store = store()

        assertFailsWith<InvalidAutoLockTimeoutException> { store.write(2.minutes) }
        assertEquals(DEFAULT_AUTO_LOCK, store().read())
    }

    @Test
    fun read_returnsDefaultWhenSavedValueIsNotAnOption() {
        prefs().edit().putString("autoLockMs", "120000").commit()

        assertEquals(DEFAULT_AUTO_LOCK, store().read())
    }

    @Test
    fun read_returnsDefaultWhenSavedValueHasAnotherType() {
        prefs().edit().putLong("autoLockMs", 60_000L).commit()

        assertEquals(DEFAULT_AUTO_LOCK, store().read())
    }
}
