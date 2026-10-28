package com.keymanage.back.api.vault

import com.keymanage.back.domain.vault.Revision
import com.keymanage.back.domain.vault.StoredVault
import com.keymanage.back.domain.vault.VaultId
import com.keymanage.back.domain.vault.VaultRepository

/** 테스트용 저장소. revision 비교와 교체를 한 번에 처리한다 */
class InMemoryVaultRepository : VaultRepository {

    private val vaults = mutableMapOf<VaultId, StoredVault>()

    @Synchronized
    override fun find(id: VaultId): StoredVault? = vaults[id]

    @Synchronized
    override fun replaceIfRevisionMatches(expected: Revision, vault: StoredVault): Boolean {
        val current = vaults[vault.id]?.revision ?: Revision.NONE
        if (current != expected) return false
        vaults[vault.id] = vault
        return true
    }
}
