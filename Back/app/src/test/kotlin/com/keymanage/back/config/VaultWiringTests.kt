package com.keymanage.back.config

import com.keymanage.back.domain.vault.GetVaultUseCase
import com.keymanage.back.domain.vault.SaveVaultUseCase
import com.keymanage.back.domain.vault.VaultRepository
import com.keymanage.back.infrastructure.vault.FileSystemVaultRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs

@SpringBootTest(properties = ["keymanage.vault.storage-dir=build/test-vaults"])
class VaultWiringTests {

    @Autowired
    lateinit var repository: VaultRepository

    @Autowired
    lateinit var saveVault: SaveVaultUseCase

    @Autowired
    lateinit var getVault: GetVaultUseCase

    @Test
    fun `설정한 폴더를 쓰는 파일 저장소가 유스케이스에 조립된다`() {
        val fileRepository = assertIs<FileSystemVaultRepository>(repository)
        assertEquals(Path.of("build/test-vaults"), fileRepository.storageDir)
    }
}
