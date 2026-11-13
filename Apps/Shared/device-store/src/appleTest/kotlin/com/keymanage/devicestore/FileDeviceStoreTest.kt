package com.keymanage.devicestore

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalForeignApi::class)
class FileDeviceStoreTest {
    private val directory = NSTemporaryDirectory() + "keymanage-device-store-" + NSUUID().UUIDString
    private val store = FileDeviceStore(directory)

    @AfterTest
    fun removeDirectory() {
        NSFileManager.defaultManager.removeItemAtPath(directory, null)
    }

    @Test
    fun loadReturnsNullWhenNothingSaved() = runTest {
        assertNull(store.load())
    }

    @Test
    fun saveThenLoadReturnsSameRecordAndCreatesDirectory() = runTest {
        store.save("""{"version":1,"email":"a@b.c"}""")

        assertEquals("""{"version":1,"email":"a@b.c"}""", store.load())
    }

    @Test
    fun saveOverwritesPreviousRecord() = runTest {
        store.save("""{"revision":1}""")
        store.save("""{"revision":2}""")

        assertEquals("""{"revision":2}""", FileDeviceStore(directory).load())
    }

    @Test
    fun keepsNonAsciiTextIntact() = runTest {
        store.save("""{"email":"사용자@예시.한국"}""")

        assertEquals("""{"email":"사용자@예시.한국"}""", store.load())
    }

    @Test
    fun clearRemovesRecord() = runTest {
        store.save("{}")
        store.clear()

        assertNull(store.load())
    }

    @Test
    fun clearWithoutRecordSucceeds() = runTest {
        store.clear()

        assertNull(store.load())
    }

    @Test
    fun saveFailsWithDeviceStorageExceptionWhenDirectoryCannotBeCreated() = runTest {
        // 같은 이름의 파일이 있으면 폴더를 만들 수 없다
        NSFileManager.defaultManager.createFileAtPath(directory, null, null)

        assertFailsWith<DeviceStorageException> { store.save("{}") }
    }
}
