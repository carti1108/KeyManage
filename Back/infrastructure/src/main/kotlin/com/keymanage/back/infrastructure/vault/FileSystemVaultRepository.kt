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
 *
 * 비밀번호 변경으로 지운 볼트 ID는 프로세스가 도는 동안 기억해, 변경 전에 인증을 통과한 늦은 저장이 그 ID에 다시 쓰지 못하게 한다
 * (ADR-BE-001). 재시작하면 잊지만, 그때는 옛 볼트를 가리키던 세션이 모두 폐기된 뒤다.
 */
class FileSystemVaultRepository(
    val storageDir: Path,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : VaultRepository {

    private val locks = ConcurrentHashMap<VaultId, Any>()
    private val deleted: MutableSet<VaultId> = ConcurrentHashMap.newKeySet()

    // 교체가 ATOMIC_MOVE라서 잠금 없이 읽어도 이전 파일이나 새 파일 중 하나를 온전히 읽는다
    override fun find(id: VaultId): StoredVault? {
        if (id in deleted) return null
        val file = fileOf(id)
        if (Files.notExists(file)) return null
        // Windows에서 읽는 중에도 교체(삭제)가 막히지 않도록 NIO 스트림으로 연다
        val stored = Files.newInputStream(file).use { objectMapper.readValue<VaultFile>(it) }
        return StoredVault(id, Revision(stored.revision), EncryptedVault(stored.payload))
    }

    override fun replaceIfRevisionMatches(expected: Revision, vault: StoredVault): Boolean =
        withLock(vault.id) {
            if (vault.id in deleted) return@withLock false
            val current = find(vault.id)?.revision ?: Revision.NONE
            if (current != expected) return@withLock false
            write(vault)
            true
        }

    // synchronized는 같은 스레드에서 다시 잡을 수 있어, 잠금 안에서 다른 메서드를 불러도 된다
    override fun <T> withLock(id: VaultId, action: () -> T): T =
        synchronized(locks.computeIfAbsent(id) { Any() }) { action() }

    override fun create(vault: StoredVault) =
        withLock(vault.id) {
            check(vault.id !in deleted && Files.notExists(fileOf(vault.id))) { "이미 있거나 지운 볼트 ID" }
            write(vault)
        }

    // 파일을 지우지 못해도 그 ID로는 더 저장하지 않도록 먼저 기억한다
    override fun delete(id: VaultId) =
        withLock(id) {
            deleted += id
            Files.deleteIfExists(fileOf(id))
            Unit
        }

    override fun allIds(): Set<VaultId> {
        if (Files.notExists(storageDir)) return emptySet()
        // 쓰다 남은 임시 파일과 볼트 ID 형식이 아닌 파일은 볼트가 아니다
        return Files.newDirectoryStream(storageDir, "*.json").use { files ->
            files.mapNotNull { runCatching { VaultId.of(it.fileName.toString().removeSuffix(".json")) }.getOrNull() }.toSet()
        }
    }

    private fun write(vault: StoredVault) {
        AtomicFiles.write(fileOf(vault.id), objectMapper.writeValueAsBytes(VaultFile(vault.revision.value, vault.vault.payload)))
    }

    private fun fileOf(id: VaultId): Path = storageDir.resolve("${id.value}.json")

    private data class VaultFile(val revision: Long, val payload: String)
}
