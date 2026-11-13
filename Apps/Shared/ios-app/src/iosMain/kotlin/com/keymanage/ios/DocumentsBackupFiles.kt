package com.keymanage.ios

import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.BackupFileSource
import com.keymanage.feature.BackupFilesPort
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile

/** 백업 파일을 쓰지 못함 */
class BackupFileWriteException(reason: String?) : Exception("백업 파일을 쓰지 못함: $reason")

// 백업 파일(VLT-004)을 앱 문서 폴더에 저장한다. 파일 고르기는 문서 선택기를 띄울 Settings 화면(12.3의 3단계)이 맡으므로
// 이 구현은 고른 파일이 없다고 답한다(사용자가 취소한 것과 같다)
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class DocumentsBackupFiles(
    private val directory: String = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String,
) : BackupFilesPort {
    override suspend fun save(file: BackupFile): Unit = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val text = NSString.create(string = file.text)
        if (!text.writeToFile("$directory/${file.name}", true, NSUTF8StringEncoding, error.ptr)) {
            throw BackupFileWriteException(error.value?.localizedDescription)
        }
    }

    override suspend fun pick(): BackupFileSource? = null
}
