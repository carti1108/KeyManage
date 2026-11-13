package com.keymanage.devicestore

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

// 기기 기록을 파일 하나에 둔다(domain-migration-plan 12.2). 기록은 암호문과 공개 KDF 값뿐이라 키체인에 두지 않는다.
// 쓰기는 임시 파일에 쓴 뒤 바꿔 끼워(atomically) 중간에 끊겨도 이전 기록이나 새 기록 중 하나만 남는다.
// iOS 앱 파일의 기본 데이터 보호(첫 잠금 해제 뒤 열림)를 그대로 쓴다
private const val RECORD_FILE_NAME = "device-record.json"

// 기록이 없을 때 Foundation이 내는 오류(NSFileReadNoSuchFileError)
private const val NO_SUCH_FILE = 260L

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class FileDeviceStore(private val directory: String) : DeviceStore {
    private val recordPath = "$directory/$RECORD_FILE_NAME"
    private val files = NSFileManager.defaultManager

    override suspend fun load(): String? {
        if (!files.fileExistsAtPath(recordPath)) return null
        return read()
    }

    private fun read(): String? = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val text = NSString.stringWithContentsOfFile(recordPath, NSUTF8StringEncoding, error.ptr)
        val failure = error.value
        when {
            text != null -> text
            failure?.code == NO_SUCH_FILE -> null
            else -> throw DeviceStorageException("기기 기록을 읽지 못함: ${failure?.localizedDescription}")
        }
    }

    override suspend fun save(recordJson: String): Unit = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        if (!files.createDirectoryAtPath(directory, true, null, error.ptr)) {
            throw DeviceStorageException("기기 저장소 폴더를 만들지 못함: ${error.value?.localizedDescription}")
        }
        val written = NSString.create(string = recordJson).writeToFile(recordPath, true, NSUTF8StringEncoding, error.ptr)
        if (!written) throw DeviceStorageException("기기 기록을 쓰지 못함: ${error.value?.localizedDescription}")
    }

    override suspend fun clear() {
        if (files.fileExistsAtPath(recordPath)) remove()
    }

    private fun remove(): Unit = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        if (!files.removeItemAtPath(recordPath, error.ptr) && files.fileExistsAtPath(recordPath)) {
            throw DeviceStorageException("기기 기록을 지우지 못함: ${error.value?.localizedDescription}")
        }
    }

    companion object {
        /** 앱의 Application Support/KeyManage 폴더. 폴더는 처음 저장할 때 만든다 */
        fun inApplicationSupport(): FileDeviceStore {
            val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
                .firstOrNull() as? String
                ?: throw DeviceStorageException("Application Support 폴더를 찾지 못함")
            return FileDeviceStore("$base/KeyManage")
        }
    }
}
