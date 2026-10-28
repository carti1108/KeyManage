package com.keymanage.back.infrastructure.storage

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * 임시 파일에 다 쓰고 디스크에 내린 뒤 ATOMIC_MOVE로 교체한다 (ADR-0007).
 * 쓰기 도중 장애가 나도 대상 파일은 이전 내용이나 새 내용 중 하나로만 남는다. 폴더가 없으면 만든다.
 */
object AtomicFiles {

    fun write(target: Path, bytes: ByteArray) {
        val dir = target.toAbsolutePath().parent
        Files.createDirectories(dir)
        // POSIX에서 createTempFile은 소유자만 읽고 쓸 수 있는 권한으로 만들며, 이동 후에도 그 권한이 유지된다
        val temp = Files.createTempFile(dir, target.fileName.toString(), ".tmp")
        try {
            FileChannel.open(temp, StandardOpenOption.WRITE).use { channel ->
                channel.write(ByteBuffer.wrap(bytes))
                // 이름을 바꾸기 전에 디스크에 내려 둬야 정전 뒤에 빈 파일로 교체되지 않는다
                channel.force(true)
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
