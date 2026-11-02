package com.keymanage.back.config

import com.keymanage.back.domain.auth.Account
import com.keymanage.back.domain.auth.Email
import com.keymanage.back.domain.auth.KdfParams
import com.keymanage.back.domain.vault.EncryptedVault
import com.keymanage.back.domain.vault.Revision
import com.keymanage.back.domain.vault.StoredVault
import com.keymanage.back.domain.vault.VaultId
import com.keymanage.back.infrastructure.auth.FileSystemAccountRepository
import com.keymanage.back.infrastructure.vault.FileSystemVaultRepository
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 서버가 시작할 때 어느 계정도 가리키지 않는 볼트 파일을 지운다 (ADR-BE-001 결정 5) */
@SpringBootTest
class OrphanVaultCleanupWiringTests {

    @Test
    fun `시작할 때 고아 볼트 파일은 지우고 계정이 가리키는 볼트는 남긴다`() {
        assertTrue(Files.exists(vaultFile(OWNED)))
        assertFalse(Files.exists(vaultFile(ORPHAN)))
    }

    companion object {
        @TempDir
        @JvmStatic
        lateinit var dataDir: Path

        private val OWNED = VaultId.of("550e8400-e29b-41d4-a716-446655440000")
        private val ORPHAN = VaultId.of("650e8400-e29b-41d4-a716-446655440000")

        private fun vaultFile(id: VaultId): Path = dataDir.resolve("vaults").resolve("${id.value}.json")

        // 컨텍스트가 뜨기 전에 계정 1개와 볼트 2개(하나는 고아)를 파일로 만들어 둔다
        @DynamicPropertySource
        @JvmStatic
        fun storage(registry: DynamicPropertyRegistry) {
            val accounts = FileSystemAccountRepository(dataDir.resolve("auth").resolve("accounts"))
            accounts.createIfAbsent(Account(Email.of("owner@example.com"), KdfParams(ByteArray(16) { 1 }, KdfParams.MIN_ITERATIONS), "hash", OWNED))
            val vaults = FileSystemVaultRepository(dataDir.resolve("vaults"))
            vaults.create(StoredVault(OWNED, Revision(1), EncryptedVault("{}")))
            vaults.create(StoredVault(ORPHAN, Revision(1), EncryptedVault("{}")))

            registry.add("keymanage.vault.storage-dir") { dataDir.resolve("vaults").toString() }
            registry.add("keymanage.auth.storage-dir") { dataDir.resolve("auth").toString() }
        }
    }
}
