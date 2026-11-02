package com.keymanage.back.domain.vault

/** 테스트용 저장소. 실제 어댑터처럼 revision 비교와 교체를 한 번에(원자적으로) 처리하고, 지운 ID로는 다시 저장하지 않는다 */
class InMemoryVaultRepository : VaultRepository {

    private val vaults = mutableMapOf<VaultId, StoredVault>()
    private val deleted = mutableSetOf<VaultId>()

    @Synchronized
    override fun find(id: VaultId): StoredVault? = vaults[id]

    @Synchronized
    override fun replaceIfRevisionMatches(expected: Revision, vault: StoredVault): Boolean {
        if (vault.id in deleted) return false
        val current = vaults[vault.id]?.revision ?: Revision.NONE
        if (current != expected) return false
        vaults[vault.id] = vault
        return true
    }

    // 모든 볼트가 하나의 잠금을 쓰므로 볼트별 잠금보다 넓지만, 테스트에서 겹치지 않아야 할 것은 그대로 겹치지 않는다
    @Synchronized
    override fun <T> withLock(id: VaultId, action: () -> T): T = action()

    @Synchronized
    override fun create(vault: StoredVault) {
        check(vault.id !in vaults && vault.id !in deleted) { "이미 있거나 지운 볼트 ID" }
        vaults[vault.id] = vault
    }

    @Synchronized
    override fun delete(id: VaultId) {
        deleted += id
        vaults.remove(id)
    }

    @Synchronized
    override fun allIds(): Set<VaultId> = vaults.keys.toSet()
}
