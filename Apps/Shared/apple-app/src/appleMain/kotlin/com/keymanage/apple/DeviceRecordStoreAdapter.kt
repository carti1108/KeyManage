package com.keymanage.apple

import com.keymanage.devicestore.DeviceStore
import com.keymanage.domain.device.DeviceRecordStore
import com.keymanage.domain.device.DeviceStorageException
import kotlin.coroutines.cancellation.CancellationException

// device-store의 파일 저장소를 도메인 포트로 감싼다. 세션은 도메인의 DeviceStorageException만 잡으므로 실패를 모두 그것으로 바꾼다
class DeviceRecordStoreAdapter(private val store: DeviceStore) : DeviceRecordStore {
    override suspend fun load(): String? = deviceCall { store.load() }

    override suspend fun save(recordJson: String) = deviceCall { store.save(recordJson) }

    override suspend fun clear() = deviceCall { store.clear() }
}

private suspend fun <T> deviceCall(call: suspend () -> T): T = try {
    call()
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    throw DeviceStorageException(error)
}
