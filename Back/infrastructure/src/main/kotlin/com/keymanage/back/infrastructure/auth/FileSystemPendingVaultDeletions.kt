package com.keymanage.back.infrastructure.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.keymanage.back.domain.vault.PendingVaultDeletions
import com.keymanage.back.domain.vault.VaultId
import com.keymanage.back.infrastructure.storage.AtomicFiles
import java.nio.file.Files
import java.nio.file.Path

/**
 * 삭제 예정 볼트 ID 목록을 인증 저장 폴더의 파일 하나로 둔다 (ADR-BE-002 결정 1).
 * 볼트 ID만 적는다. 읽고 고쳐 쓰는 동안 다른 쓰기가 끼지 않도록 인스턴스 잠금으로 직렬화하고, 쓰기는 임시 파일 -> ATOMIC_MOVE다.
 * 단일 인스턴스가 전제다 (ADR-0007)
 */
class FileSystemPendingVaultDeletions(
    val file: Path,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : PendingVaultDeletions {

    @Synchronized
    override fun add(id: VaultId) {
        val ids = read()
        if (id.value in ids) return
        write(ids + id.value)
    }

    @Synchronized
    override fun all(): Set<VaultId> = read().map(VaultId::of).toSet()

    @Synchronized
    override fun removeAll(removed: Set<VaultId>) {
        val ids = read()
        val kept = ids - removed.map { it.value }.toSet()
        if (kept.size != ids.size) write(kept)
    }

    private fun read(): Set<String> {
        if (Files.notExists(file)) return emptySet()
        return Files.newInputStream(file).use { objectMapper.readValue<PendingFile>(it) }.vaultIds.toSet()
    }

    private fun write(ids: Set<String>) {
        AtomicFiles.write(file, objectMapper.writeValueAsBytes(PendingFile(ids.sorted())))
    }

    private data class PendingFile(val vaultIds: List<String>)
}
