package com.keymanage.back.infrastructure.vault

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.keymanage.back.domain.vault.EncryptedVault
import com.keymanage.back.domain.vault.Revision
import com.keymanage.back.domain.vault.StoredVault
import com.keymanage.back.domain.vault.VaultId
import com.keymanage.back.domain.vault.VaultRepository
import com.keymanage.back.infrastructure.storage.AtomicFiles
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * 볼트 1개를 `{storageDir}/{vaultId}.json` 파일 1개로 저장한다 (ADR-0003, ADR-0007).
 *
 * 같은 볼트의 비교·교체는 볼트별 잠금으로 직렬화하고, 임시 파일에 다 쓴 뒤 ATOMIC_MOVE로 교체해
 * 쓰기 도중 장애가 나도 반쯤 쓰인 볼트가 남지 않는다. 잠금은 프로세스 안에서만 유효하므로 단일 인스턴스가 전제다.
 */
class FileSystemVaultRepository(
    val storageDir: Path,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : VaultRepository {

    private val locks = ConcurrentHashMap<VaultId, Any>()

    // 교체가 ATOMIC_MOVE라서 잠금 없이 읽어도 이전 파일이나 새 파일 중 하나를 온전히 읽는다
    override fun find(id: VaultId): StoredVault? {
        val file = fileOf(id)
        if (Files.notExists(file)) return null
        // Windows에서 읽는 중에도 교체(삭제)가 막히지 않도록 NIO 스트림으로 연다
        val stored = Files.newInputStream(file).use { objectMapper.readValue<VaultFile>(it) }
        return StoredVault(id, Revision(stored.revision), EncryptedVault(stored.payload))
    }

    override fun replaceIfRevisionMatches(expected: Revision, vault: StoredVault): Boolean =
        synchronized(locks.computeIfAbsent(vault.id) { Any() }) {
            val current = find(vault.id)?.revision ?: Revision.NONE
            if (current != expected) return false
            write(vault)
            true
        }

    private fun write(vault: StoredVault) {
        AtomicFiles.write(fileOf(vault.id), objectMapper.writeValueAsBytes(VaultFile(vault.revision.value, vault.vault.payload)))
    }

    private fun fileOf(id: VaultId): Path = storageDir.resolve("${id.value}.json")

    private data class VaultFile(val revision: Long, val payload: String)
}
