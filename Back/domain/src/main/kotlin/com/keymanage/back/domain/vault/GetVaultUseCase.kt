package com.keymanage.back.domain.vault

/** 저장된 볼트를 revision과 함께 조회한다 (api-spec 2.3) */
class GetVaultUseCase(private val repository: VaultRepository) {

    /** @return 볼트가 없으면 null */
    fun execute(id: VaultId): StoredVault? = repository.find(id)
}
