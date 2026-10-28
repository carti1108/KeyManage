package com.keymanage.back.infrastructure.auth

import com.keymanage.back.domain.auth.ServerSecret
import com.keymanage.back.infrastructure.storage.AtomicFiles
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64

/**
 * 서버 비밀키를 준비한다 (SEC-002).
 * 환경 변수 값이 있으면 그것을 쓰고, 없으면 [file]을 읽으며, 파일도 없으면 첫 실행으로 보고 새로 만들어 저장한다.
 * 그래서 설치본마다 다른 키를 쓰게 된다. 값이 잘못됐으면 키 내용을 노출하지 않고 기동을 멈춘다.
 */
object ServerSecretLoader {

    const val ENV_NAME = "KEYMANAGE_SERVER_SECRET"

    fun load(envValue: String?, file: Path): ServerSecret {
        if (!envValue.isNullOrBlank()) return ServerSecret(decodeEnv(envValue))
        if (Files.exists(file)) return ServerSecret(readFile(file))

        val bytes = ByteArray(ServerSecret.BYTES).also(SecureRandom()::nextBytes)
        AtomicFiles.write(file, bytes)
        return ServerSecret(bytes)
    }

    private fun decodeEnv(value: String): ByteArray {
        val bytes = runCatching { Base64.getDecoder().decode(value.trim()) }.getOrNull()
        check(bytes != null && bytes.size == ServerSecret.BYTES) { "$ENV_NAME 값은 ${ServerSecret.BYTES}바이트 Base64여야 함" }
        return bytes
    }

    private fun readFile(file: Path): ByteArray {
        val bytes = Files.readAllBytes(file)
        check(bytes.size == ServerSecret.BYTES) { "서버 비밀키 파일이 손상됨: $file" }
        return bytes
    }
}
