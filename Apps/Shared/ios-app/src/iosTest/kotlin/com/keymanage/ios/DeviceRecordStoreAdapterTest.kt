package com.keymanage.ios

import com.keymanage.devicestore.DeviceStore
import com.keymanage.domain.device.DeviceStorageException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import com.keymanage.devicestore.DeviceStorageException as PlatformStorageException

class DeviceRecordStoreAdapterTest {
    private class MemoryDeviceStore(var failure: Throwable? = null) : DeviceStore {
        var record: String? = null

        override suspend fun load(): String? {
            failure?.let { throw it }
            return record
        }

        override suspend fun save(recordJson: String) {
            failure?.let { throw it }
            record = recordJson
        }

        override suspend fun clear() {
            failure?.let { throw it }
            record = null
        }
    }

    @Test
    fun passesRecordsThrough() = runTest {
        val adapter = DeviceRecordStoreAdapter(MemoryDeviceStore())

        adapter.save("{}")
        assertEquals("{}", adapter.load())
        adapter.clear()

        assertNull(adapter.load())
    }

    @Test
    fun platformStorageFailureBecomesDomainStorageException() = runTest {
        val adapter = DeviceRecordStoreAdapter(MemoryDeviceStore(PlatformStorageException("디스크 가득 참")))

        assertFailsWith<DeviceStorageException> { adapter.load() }
        assertFailsWith<DeviceStorageException> { adapter.save("{}") }
        assertFailsWith<DeviceStorageException> { adapter.clear() }
    }
}
