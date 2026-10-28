package com.keymanage.back.infrastructure.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.keymanage.back.domain.auth.Email
import com.keymanage.back.domain.auth.Session
import com.keymanage.back.domain.auth.SessionRepository
import com.keymanage.back.infrastructure.storage.AtomicFiles
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * 세션 1개를 `{storageDir}/{tokenHash}.json` 파일 1개로 저장한다 (SEC-002).
 * 파일 이름은 토큰의 SHA-256이며 토큰 원문은 어디에도 저장하지 않는다. 16진수 64자가 아닌 키로는 파일에 접근하지 않는다.
 */
class FileSystemSessionRepository(
    val storageDir: Path,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : SessionRepository {

    override fun save(session: Session) {
        require(TOKEN_HASH.matches(session.tokenHash)) { "토큰 해시 형식이 아님" }
        val stored = SessionFile(session.email.value, session.expiresAt.toString())
        AtomicFiles.write(fileOf(session.tokenHash), objectMapper.writeValueAsBytes(stored))
    }

    override fun find(tokenHash: String): Session? {
        if (!TOKEN_HASH.matches(tokenHash)) return null
        val file = fileOf(tokenHash)
        if (Files.notExists(file)) return null
        val stored = Files.newInputStream(file).use { objectMapper.readValue<SessionFile>(it) }
        return Session(tokenHash, Email.of(stored.email), Instant.parse(stored.expiresAt))
    }

    override fun delete(tokenHash: String) {
        if (!TOKEN_HASH.matches(tokenHash)) return
        Files.deleteIfExists(fileOf(tokenHash))
    }

    private fun fileOf(tokenHash: String): Path = storageDir.resolve("$tokenHash.json")

    private data class SessionFile(val email: String, val expiresAt: String)

    private companion object {
        val TOKEN_HASH = Regex("[0-9a-f]{64}")
    }
}
