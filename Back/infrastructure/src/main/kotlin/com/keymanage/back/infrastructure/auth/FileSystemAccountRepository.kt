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
 * 같은 이메일의 확인·생성은 이메일별 잠금으로 직렬화하므로 단일 인스턴스가 전제다.
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
        synchronized(locks.computeIfAbsent(account.email) { Any() }) {
            val file = fileOf(account.email)
            if (Files.exists(file)) return false
            val stored = AccountFile(
                email = account.email.value,
                kdfSalt = Base64.getEncoder().encodeToString(account.kdf.salt),
                kdfIterations = account.kdf.iterations,
                passwordHash = account.passwordHash,
                vaultId = account.vaultId.value,
            )
            AtomicFiles.write(file, objectMapper.writeValueAsBytes(stored))
            true
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
