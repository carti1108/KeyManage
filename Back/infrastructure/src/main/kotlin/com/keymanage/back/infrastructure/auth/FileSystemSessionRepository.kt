package com.keymanage.back.infrastructure.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.keymanage.back.domain.auth.Email
import com.keymanage.back.domain.auth.Session
import com.keymanage.back.domain.auth.SessionRepository
import com.keymanage.back.domain.vault.VaultId
import com.keymanage.back.infrastructure.storage.AtomicFiles
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * 세션 1개를 `{storageDir}/{tokenHash}.json` 파일 1개로 저장한다 (SEC-002).
 * 파일 이름은 토큰의 SHA-256이며 토큰 원문은 어디에도 저장하지 않는다. 16진수 64자가 아닌 키로는 파일에 접근하지 않는다.
 * 이메일 색인이 없으므로 계정의 세션을 모두 지울 때는 폴더를 훑는다. 단일 인스턴스의 데모 규모라 비용이 작다 (ADR-BE-001 결정 6).
 * 세션 파일에는 발급 때의 볼트 ID가 있다. 볼트 ID가 없는 예전 형식의 파일은 무효로 보고 다시 로그인하게 한다.
 */
class FileSystemSessionRepository internal constructor(
    val storageDir: Path,
    private val objectMapper: ObjectMapper,
    // 테스트가 파일 삭제 실패를 주입할 수 있게 둔다
    private val deleteFile: (Path) -> Unit,
) : SessionRepository {

    constructor(storageDir: Path, objectMapper: ObjectMapper = jacksonObjectMapper()) :
        this(storageDir, objectMapper, { Files.deleteIfExists(it) })

    override fun save(session: Session) {
        require(TOKEN_HASH.matches(session.tokenHash)) { "토큰 해시 형식이 아님" }
        val stored = SessionFile(session.email.value, session.expiresAt.toString(), session.vaultId.value)
        AtomicFiles.write(fileOf(session.tokenHash), objectMapper.writeValueAsBytes(stored))
    }

    override fun find(tokenHash: String): Session? {
        if (!TOKEN_HASH.matches(tokenHash)) return null
        val file = fileOf(tokenHash)
        if (Files.notExists(file)) return null
        val stored = Files.newInputStream(file).use { objectMapper.readValue<SessionFile>(it) }
        val vaultId = stored.vaultId ?: return null
        return Session(tokenHash, Email.of(stored.email), Instant.parse(stored.expiresAt), VaultId.of(vaultId))
    }

    override fun delete(tokenHash: String) {
        if (!TOKEN_HASH.matches(tokenHash)) return
        Files.deleteIfExists(fileOf(tokenHash))
    }

    override fun deleteAllOf(email: Email) {
        if (Files.notExists(storageDir)) return
        val files = Files.newDirectoryStream(storageDir, "*.json").use { it.toList() }
        var failures = 0
        files.filter { TOKEN_HASH.matches(it.fileName.toString().removeSuffix(".json")) }
            .filter { file -> readEmail(file) == email.value }
            .forEach { file ->
                // 하나를 지우지 못해도 나머지는 지운다. 남은 세션은 볼트 ID가 달라 인증에서 거절된다
                try {
                    deleteFile(file)
                } catch (e: Exception) {
                    failures += 1
                }
            }
        // 파일 이름이 토큰 해시이므로 경로·이메일은 남기지 않고 개수만 남긴다
        if (failures > 0) log.warn("계정 세션 파일 {}개를 지우지 못함", failures)
    }

    // 읽지 못하는 파일은 find에서도 쓸 수 없는 세션이므로 건너뛴다. 그 사이 지워진 파일도 같다
    private fun readEmail(file: Path): String? =
        runCatching { Files.newInputStream(file).use { objectMapper.readValue<SessionFile>(it) }.email }.getOrNull()

    private fun fileOf(tokenHash: String): Path = storageDir.resolve("$tokenHash.json")

    private data class SessionFile(val email: String, val expiresAt: String, val vaultId: String? = null)

    private companion object {
        val TOKEN_HASH = Regex("[0-9a-f]{64}")
        val log = LoggerFactory.getLogger(FileSystemSessionRepository::class.java)
    }
}
