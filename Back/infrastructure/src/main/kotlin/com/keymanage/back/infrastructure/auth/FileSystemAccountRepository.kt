package com.keymanage.back.infrastructure.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.keymanage.back.domain.auth.Account
import com.keymanage.back.domain.auth.AccountRepository
import com.keymanage.back.domain.auth.Email
import com.keymanage.back.domain.auth.KdfParams
import com.keymanage.back.domain.vault.VaultId
import com.keymanage.back.infrastructure.storage.AtomicFiles
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * 계정 1개를 파일 1개로 저장한다 (ADR-0007, SEC-002).
 * 파일 이름은 이메일의 SHA-256이라 경로 조작이 불가능하고 폴더 목록에 이메일 원문이 드러나지 않는다.
 * 같은 이메일의 확인·생성·교체는 이메일별 잠금으로 직렬화하므로 단일 인스턴스가 전제다.
 * 교체는 임시 파일 -> ATOMIC_MOVE라서 읽는 쪽에는 옛 계정이나 새 계정 중 하나로만 보인다 (ADR-BE-001 결정 2).
 */
class FileSystemAccountRepository(
    val storageDir: Path,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : AccountRepository {

    private val locks = ConcurrentHashMap<Email, Any>()

    override fun find(email: Email): Account? {
        val file = fileOf(email)
        if (Files.notExists(file)) return null
        val stored = Files.newInputStream(file).use { objectMapper.readValue<AccountFile>(it) }
        return Account(
            email = Email.of(stored.email),
            kdf = KdfParams(Base64.getDecoder().decode(stored.kdfSalt), stored.kdfIterations),
            passwordHash = stored.passwordHash,
            vaultId = VaultId.of(stored.vaultId),
        )
    }

    override fun createIfAbsent(account: Account): Boolean =
        withLock(account.email) {
            if (Files.exists(fileOf(account.email))) return@withLock false
            write(account)
            true
        }

    // synchronized는 같은 스레드에서 다시 잡을 수 있어, 잠금 안에서 createIfAbsent·replaceIfMatches를 불러도 된다
    override fun <T> withLock(email: Email, action: () -> T): T =
        synchronized(locks.computeIfAbsent(email) { Any() }) { action() }

    override fun replaceIfMatches(expected: Account, replacement: Account): Boolean {
        require(expected.email == replacement.email) { "이메일이 다른 계정으로 교체할 수 없음" }
        return withLock(expected.email) {
            if (find(expected.email) != expected) return@withLock false
            write(replacement)
            true
        }
    }

    override fun allVaultIds(): Set<VaultId> {
        if (Files.notExists(storageDir)) return emptySet()
        // 쓰다 남은 임시 파일(*.tmp)은 계정이 아니다. 계정 파일을 하나라도 읽지 못하면 예외가 그대로 나간다
        return Files.newDirectoryStream(storageDir, "*.json").use { files ->
            files.map { file -> VaultId.of(Files.newInputStream(file).use { objectMapper.readValue<AccountFile>(it) }.vaultId) }.toSet()
        }
    }

    private fun write(account: Account) {
        val stored = AccountFile(
            email = account.email.value,
            kdfSalt = Base64.getEncoder().encodeToString(account.kdf.salt),
            kdfIterations = account.kdf.iterations,
            passwordHash = account.passwordHash,
            vaultId = account.vaultId.value,
        )
        AtomicFiles.write(fileOf(account.email), objectMapper.writeValueAsBytes(stored))
    }

    private fun fileOf(email: Email): Path {
        val digest = MessageDigest.getInstance("SHA-256").digest(email.value.toByteArray(Charsets.UTF_8))
        return storageDir.resolve(digest.joinToString("") { "%02x".format(it) } + ".json")
    }

    private data class AccountFile(
        val email: String,
        val kdfSalt: String,
        val kdfIterations: Int,
        val passwordHash: String,
        val vaultId: String,
    )
}
