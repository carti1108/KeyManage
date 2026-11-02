package com.keymanage.back.domain.vault

/** 테스트용 삭제 예정 목록. [failAdd]면 추가가 실패한다 */
class InMemoryPendingVaultDeletions : PendingVaultDeletions {

    private val ids = linkedSetOf<VaultId>()
    var failAdd = false

    @Synchronized
    override fun add(id: VaultId) {
        if (failAdd) throw java.io.IOException("삭제 예정 목록 쓰기 실패")
        ids += id
    }

    @Synchronized
    override fun all(): Set<VaultId> = ids.toSet()

    @Synchronized
    override fun removeAll(removed: Set<VaultId>) {
        ids -= removed
    }
}
