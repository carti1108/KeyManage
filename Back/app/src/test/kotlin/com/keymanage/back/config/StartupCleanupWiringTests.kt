package com.keymanage.back.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.keymanage.back.domain.auth.Email
import com.keymanage.back.domain.auth.Session
import com.keymanage.back.domain.vault.EncryptedVault
import com.keymanage.back.domain.vault.Revision
import com.keymanage.back.domain.vault.StoredVault
import com.keymanage.back.domain.vault.VaultId
import com.keymanage.back.infrastructure.auth.FileSystemPendingVaultDeletions
import com.keymanage.back.infrastructure.auth.FileSystemSessionRepository
import com.keymanage.back.infrastructure.vault.FileSystemVaultRepository
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 계정이 하나도 없어도 서버가 시작할 때 삭제 예정 목록의 볼트와 무효 세션 파일을 지운다 (ADR-BE-002) */
@SpringBootTest
class StartupCleanupWiringTests {

    @Test
    fun `시작할 때 목록의 볼트를 지우고 목록을 비우며 계정 없는 세션을 지운다`() {
        assertFalse(Files.exists(dataDir.resolve("vaults").resolve("${LISTED.value}.json")))
        assertEquals(emptySet(), FileSystemPendingVaultDeletions(dataDir.resolve("auth").resolve("pending-vault-deletions.json")).all())
        assertEquals(emptySet(), FileSystemSessionRepository(dataDir.resolve("auth").resolve("sessions"), jacksonObjectMapper()).allTokenHashes())
    }

    companion object {
        @TempDir
        @JvmStatic
        lateinit var dataDir: Path

        private val LISTED = VaultId.of("550e8400-e29b-41d4-a716-446655440000")

        // 컨텍스트가 뜨기 전에 계정 없이 목록에 적힌 볼트 1개와 계정 없는 세션 1개를 파일로 만들어 둔다
        @DynamicPropertySource
        @JvmStatic
        fun storage(registry: DynamicPropertyRegistry) {
            FileSystemVaultRepository(dataDir.resolve("vaults")).create(StoredVault(LISTED, Revision(1), EncryptedVault("{}")))
            FileSystemPendingVaultDeletions(dataDir.resolve("auth").resolve("pending-vault-deletions.json")).add(LISTED)
            FileSystemSessionRepository(dataDir.resolve("auth").resolve("sessions"))
                .save(Session("a".repeat(64), Email.of("ghost@example.com"), Instant.parse("2999-01-01T00:00:00Z"), LISTED))

            registry.add("keymanage.vault.storage-dir") { dataDir.resolve("vaults").toString() }
            registry.add("keymanage.auth.storage-dir") { dataDir.resolve("auth").toString() }
        }
    }
}
