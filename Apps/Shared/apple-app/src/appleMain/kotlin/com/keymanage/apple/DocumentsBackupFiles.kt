package com.keymanage.apple

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
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import kotlin.coroutines.resume

/**
 * 내보내기 전에 백업 파일을 두는 폴더. iOS는 파일 앱에서 보이는 앱 문서 폴더(UIFileSharingEnabled),
 * macOS는 사용자 문서 폴더에 몰래 남기지 않도록 임시 폴더다. 저장 패널이 고른 곳으로 복사한다
 */
expect fun backupStagingDirectory(): String

/** 백업 파일을 쓰지 못함 */
class BackupFileWriteException(reason: String?) : Exception("백업 파일을 쓰지 못함: $reason")

/** 고른 백업 파일을 읽지 못함 */
class BackupFileReadException(reason: String?) : Exception("백업 파일을 읽지 못함: $reason")

/**
 * 문서 선택기를 띄우는 화면 쪽(Swift). 메인 스레드에서 부르고, 콜백도 메인 스레드에서 한 번만 부른다
 */
interface BackupDocumentPresenter {
    /** 파일을 고르게 한다. 고른 파일을 앱이 읽을 수 있는 곳에 복사한 경로를 넘기고, 취소면 null을 넘긴다 */
    fun pickBackupDocument(onPicked: (String?) -> Unit)

    /** 임시 저장 폴더에 저장한 백업 파일을 사용자가 고른 곳으로 내보내게 한다 */
    fun exportBackupDocument(path: String)
}

// 백업 파일(VLT-004)을 임시 저장 폴더(backupStagingDirectory)에 저장한 뒤 내보내기 선택기를 띄우고, 복원할 파일은 문서 선택기로 고른다
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class DocumentsBackupFiles(
    private val presenter: BackupDocumentPresenter?,
    private val directory: String = backupStagingDirectory(),
) : BackupFilesPort {
    override suspend fun save(file: BackupFile) {
        val path = "$directory/${file.name}"
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val text = NSString.create(string = file.text)
            if (!text.writeToFile(path, true, NSUTF8StringEncoding, error.ptr)) {
                throw BackupFileWriteException(error.value?.localizedDescription)
            }
        }
        presenter?.exportBackupDocument(path)
    }

    override suspend fun pick(): BackupFileSource? {
        val chooser = presenter ?: return null
        val path = suspendCancellableCoroutine<String?> { continuation ->
            chooser.pickBackupDocument { picked -> if (continuation.isActive) continuation.resume(picked) }
        } ?: return null
        return BackupFileSource(fileSize(path)) { readText(path) }
    }

    private fun fileSize(path: String): Int = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, error.ptr)
            ?: throw BackupFileReadException(error.value?.localizedDescription)
        val size = (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: throw BackupFileReadException("크기 없음")
        // 백업 크기 검사(readBackupFile)가 거절하도록 Int 범위를 넘으면 최댓값으로 둔다
        size.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun readText(path: String): String = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, error.ptr)
            ?: throw BackupFileReadException(error.value?.localizedDescription)
    }
}
