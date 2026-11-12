package com.keymanage.securestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class KeystoreSecureStoreTest {
    private val store = KeystoreSecureStore(InstrumentationRegistry.getInstrumentation().targetContext, "test")
    private val account = "test-account"

    @After
    fun cleanUp() = store.delete(account)

    @Test
    fun load_returnsSavedBytes() {
        val secret = ByteArray(32) { it.toByte() }
        store.save(account, secret)
        assertContentEquals(secret, store.load(account))
    }

    @Test
    fun save_overwritesExistingValue() {
        store.save(account, byteArrayOf(1))
        store.save(account, byteArrayOf(2, 3))
        assertContentEquals(byteArrayOf(2, 3), store.load(account))
    }

    @Test
    fun delete_removesValue() {
        store.save(account, byteArrayOf(1))
        store.delete(account)
        assertNull(store.load(account))
    }

    @Test
    fun load_returnsNullWhenMissing() = assertNull(store.load("missing-account"))

    @Test
    fun delete_ignoresMissingValue() = store.delete("missing-account")
}
