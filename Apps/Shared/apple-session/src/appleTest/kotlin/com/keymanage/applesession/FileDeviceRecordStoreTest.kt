package com.keymanage.applesession

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileDeviceRecordStoreTest {
    private val store = FileDeviceRecordStore("device-record-test.json", directoryName = "com.keymanage.applesession.test")

    @Test
    fun saveThenLoad_returnsSameJson() = runTest {
        store.save("""{"v":1}""")

        assertEquals("""{"v":1}""", store.load())
        store.clear()
    }

    @Test
    fun clear_thenLoad_returnsNull() = runTest {
        store.save("x")

        store.clear()

        assertNull(store.load())
    }

    @Test
    fun clear_withoutFile_doesNothing() = runTest {
        store.clear()
        store.clear()

        assertNull(store.load())
    }
}
