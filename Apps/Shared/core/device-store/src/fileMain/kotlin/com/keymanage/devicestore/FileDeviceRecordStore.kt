package com.keymanage.devicestore

import com.keymanage.domain.device.DeviceRecordStore
import com.keymanage.domain.device.DeviceStorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 기기 기록을 파일 하나에 둔다(VLT-002·VLT-003). 기록은 공통 코드가 만든 JSON 문자열이고 형식은 해석하지 않는다.
 * 임시 파일에 쓴 뒤 이름을 바꿔 교체하므로 쓰다 끊겨도 이전 기록이 남는다.
 * Android 앱은 백업에서 빠지는 `noBackupFilesDir` 아래 파일을 넘긴다.
 */
class FileDeviceRecordStore(private val file: File) : DeviceRecordStore {
    override suspend fun load(): String? = io {
        if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    override suspend fun save(recordJson: String) = io {
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            temp.writeText(recordJson, Charsets.UTF_8)
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (error: IOException) {
            temp.delete()
            throw error
        }
        Unit
    }

    override suspend fun clear() = io {
        Files.deleteIfExists(file.toPath())
        Unit
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (error: IOException) {
            throw DeviceStorageException(error)
        }
    }
}
