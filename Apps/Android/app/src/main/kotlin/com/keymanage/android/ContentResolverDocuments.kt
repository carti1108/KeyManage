package com.keymanage.android

import android.content.ContentResolver
import android.net.Uri
import com.keymanage.android.feature.settings.DocumentIo
import java.io.IOException

/** 저장소 접근 프레임워크로 고른 문서 읽기·쓰기. 백업 파일 크기 상한까지만 읽어 큰 파일로 메모리를 채우지 않는다. */
class ContentResolverDocuments(private val resolver: ContentResolver) : DocumentIo {
    override fun read(uri: String, maxBytes: Int): ByteArray {
        val input = resolver.openInputStream(Uri.parse(uri)) ?: throw IOException("문서를 열지 못함")
        return input.use { stream ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(CHUNK_BYTES)
            while (buffer.size() < maxBytes) {
                val read = stream.read(chunk, 0, minOf(chunk.size, maxBytes - buffer.size()))
                if (read < 0) break
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        }
    }

    override fun write(uri: String, bytes: ByteArray) {
        val output = resolver.openOutputStream(Uri.parse(uri), "wt") ?: throw IOException("문서를 쓰지 못함")
        output.use { it.write(bytes) }
    }

    private companion object {
        const val CHUNK_BYTES = 64 * 1024
    }
}
