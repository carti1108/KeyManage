package com.keymanage.applesession

import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.BackupFileSource
import com.keymanage.feature.BackupFilesPort
import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * 백업 파일을 문서 선택 창으로 저장하고 고른다 (VLT-004, AndroidBackupFiles의 짝). 저장할 때는 임시 파일을 만들어 고른 위치로 복사하고 바로 지운다.
 *
 * @param presenter 선택 창을 띄울 화면. 화면이 없으면 null을 돌려준다
 */
class DocumentPickerBackupFiles(private val presenter: () -> UIViewController?) : BackupFilesPort {
    /** @throws IllegalStateException 화면이 없거나 임시 파일을 쓰지 못함. 사용자가 취소하면 아무것도 하지 않는다 */
    override suspend fun save(file: BackupFile) {
        val host = presenter() ?: throw IllegalStateException("백업 파일을 저장할 화면이 없음")
        val temp = NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByAppendingPathComponent(file.name)
            ?: throw IllegalStateException("임시 파일 경로를 만들지 못함")
        @Suppress("CAST_NEVER_SUCCEEDS")
        val written = (file.text as NSString).writeToURL(temp, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        if (!written) throw IllegalStateException("임시 백업 파일을 쓰지 못함")
        try {
            present(host, UIDocumentPickerViewController(forExportingURLs = listOf(temp), asCopy = true))
        } finally {
            NSFileManager.defaultManager.removeItemAtURL(temp, error = null)
        }
    }

    override suspend fun pick(): BackupFileSource? {
        val host = presenter() ?: return null
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON, UTTypePlainText), asCopy = true)
        val url = present(host, picker) ?: return null
        val attributes = url.path?.let { NSFileManager.defaultManager.attributesOfItemAtPath(it, error = null) }
        val size = (attributes?.get(NSFileSize) as? NSNumber)?.longValue?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
        return BackupFileSource(size) { readText(url) }
    }

    @OptIn(BetaInteropApi::class)
    private fun readText(url: NSURL): String =
        NSString.create(contentsOfURL = url, encoding = NSUTF8StringEncoding, error = null)?.toString()
            ?: throw IllegalStateException("백업 파일을 열지 못함")

    /** 선택 창을 띄우고 고른 첫 파일을 기다린다. 취소하면 null */
    private suspend fun present(host: UIViewController, picker: UIDocumentPickerViewController): NSURL? =
        suspendCancellableCoroutine { continuation ->
            val delegate = PickerDelegate { url -> if (continuation.isActive) continuation.resume(url) }
            picker.delegate = delegate
            continuation.invokeOnCancellation { picker.dismissViewControllerAnimated(true, completion = null) }
            host.presentViewController(picker, animated = true) {
                // 다른 화면이 닫히는 중이면 띄우지 못하고 조용히 끝난다. 그때는 기다리지 않고 실패로 알린다
                if (picker.presentingViewController == null && continuation.isActive) {
                    continuation.resumeWith(Result.failure(IllegalStateException("백업 파일 선택 창을 띄우지 못함")))
                }
            }
            retained = delegate
        }

    private var retained: PickerDelegate? = null

    private class PickerDelegate(private val onResult: (NSURL?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            onResult(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onResult(null)
    }
}
