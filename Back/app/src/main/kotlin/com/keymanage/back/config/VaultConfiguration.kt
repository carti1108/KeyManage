package com.keymanage.back.config

import com.keymanage.back.domain.vault.GetVaultUseCase
import com.keymanage.back.domain.vault.SaveVaultUseCase
import com.keymanage.back.domain.vault.VaultRepository
import com.keymanage.back.infrastructure.vault.FileSystemVaultRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

// 볼트 유스케이스에 파일 저장소를 조립한다 (ADR-0002, ADR-0007)
@Configuration
class VaultConfiguration {

    // Spring의 Path 변환은 상대 경로를 리소스로 해석할 수 있어 문자열로 받아 직접 만든다
    @Bean
    fun vaultRepository(@Value("\${keymanage.vault.storage-dir}") storageDir: String): VaultRepository =
        FileSystemVaultRepository(Path.of(storageDir))

    @Bean
    fun saveVaultUseCase(repository: VaultRepository) = SaveVaultUseCase(repository)

    @Bean
    fun getVaultUseCase(repository: VaultRepository) = GetVaultUseCase(repository)
}
