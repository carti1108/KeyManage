package com.keymanage.applesession

import com.keymanage.domain.device.DeviceRecordStore
import com.keymanage.domain.device.DeviceStorageException
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSDataWritingFileProtectionComplete
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToURL

/**
 * 기기 기록을 Application Support 아래 파일 하나에 둔다 (VLT-002·VLT-003, FileDeviceRecordStore의 iOS 짝).
 * 원자적으로 교체해 쓰다 끊겨도 이전 기록이 남는다. 기기가 잠겨 있는 동안은 읽지 못하게 보호하고, iCloud·기기 백업에서 뺀다
 */
@OptIn(BetaInteropApi::class)
class FileDeviceRecordStore(private val fileName: String = FILE_NAME) : DeviceRecordStore {
    override suspend fun load(): String? = io {
        val url = fileUrl()
        val path = url.path ?: return@io null
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return@io null
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            NSString.create(contentsOfURL = url, encoding = NSUTF8StringEncoding, error = error.ptr)?.toString()
                ?: throw DeviceStorageException(IllegalStateException(error.value?.localizedDescription))
        }
    }

    override suspend fun save(recordJson: String) = io {
        val url = fileUrl()
        @Suppress("CAST_NEVER_SUCCEEDS")
        val data = (recordJson as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: throw DeviceStorageException()
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val written = data.writeToURL(url, options = NSDataWritingAtomic or NSDataWritingFileProtectionComplete, error = error.ptr)
            if (!written) throw DeviceStorageException(IllegalStateException(error.value?.localizedDescription))
            url.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
        }
        Unit
    }

    override suspend fun clear() = io {
        val url = fileUrl()
        val path = url.path
        if (path != null && NSFileManager.defaultManager.fileExistsAtPath(path)) {
            if (!NSFileManager.defaultManager.removeItemAtURL(url, error = null)) throw DeviceStorageException()
        }
        Unit
    }

    private fun fileUrl(): NSURL {
        val directory = NSFileManager.defaultManager.URLForDirectory(
            NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ) ?: throw DeviceStorageException()
        return directory.URLByAppendingPathComponent(fileName) ?: throw DeviceStorageException()
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        const val FILE_NAME = "device-record.json"
    }
}
