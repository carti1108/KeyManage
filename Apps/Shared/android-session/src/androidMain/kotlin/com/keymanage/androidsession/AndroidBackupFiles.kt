package com.keymanage.androidsession

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.BackupFileSource
import com.keymanage.feature.BackupFilesPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume

/**
 * 백업 파일을 저장 위치 선택 창(SAF)으로 저장하고 고른다 (VLT-004). 파일 접근 권한은 요청하지 않는다.
 *
 * @param activity 선택 창을 띄울 화면. 화면이 없으면 null을 돌려준다
 */
class AndroidBackupFiles(private val activity: () -> ComponentActivity?) : BackupFilesPort {
    /** @throws IOException 화면이 없거나 고른 위치에 쓰지 못함. 사용자가 취소하면 아무것도 하지 않는다 */
    override suspend fun save(file: BackupFile) {
        val host = activity() ?: throw IOException("백업 파일을 저장할 화면이 없음")
        val target = host.launchForResult(ActivityResultContracts.CreateDocument(MIME_JSON), file.name) ?: return
        withContext(Dispatchers.IO) {
            val stream = host.contentResolver.openOutputStream(target, "wt") ?: throw IOException("백업 파일을 열지 못함")
            stream.use { it.write(file.text.toByteArray(Charsets.UTF_8)) }
        }
    }

    override suspend fun pick(): BackupFileSource? {
        val host = activity() ?: return null
        val source = host.launchForResult(ActivityResultContracts.OpenDocument(), arrayOf(MIME_JSON, "text/*", "application/octet-stream")) ?: return null
        val resolver = host.contentResolver
        val size = withContext(Dispatchers.IO) { sizeOf(host, source) }
        return BackupFileSource(size) {
            withContext(Dispatchers.IO) {
                val stream = resolver.openInputStream(source) ?: throw IOException("백업 파일을 열지 못함")
                stream.use { it.readBytes().toString(Charsets.UTF_8) }
            }
        }
    }

    /** 크기를 알 수 없으면 한도 검사에 걸리지 않게 0으로 두고, 내용은 읽을 때 확인한다 */
    private fun sizeOf(host: ComponentActivity, uri: Uri): Int =
        host.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0
        } ?: 0

    private companion object {
        const val MIME_JSON = "application/json"
    }
}

/** 선택 창을 띄우고 결과를 기다린다. 취소되면 창 등록을 푼다 */
private suspend fun <I, O> ComponentActivity.launchForResult(contract: ActivityResultContract<I, O>, input: I): O =
    suspendCancellableCoroutine { continuation ->
        val launcher = activityResultRegistry.register("keymanage-backup-" + UUID.randomUUID(), contract) { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        continuation.invokeOnCancellation { launcher.unregister() }
        launcher.launch(input)
    }
