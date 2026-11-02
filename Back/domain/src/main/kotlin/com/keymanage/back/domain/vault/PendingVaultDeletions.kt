package com.keymanage.back.domain.vault

/**
 * 확정 뒤 지우지 못한 볼트 ID의 삭제 예정 목록 포트 (ADR-BE-002 결정 1). 볼트 ID 외의 정보는 두지 않는다.
 * 구현은 추가·삭제를 원자적으로 해, 쓰다 멈춰도 목록이 옛 내용이나 새 내용 중 하나로만 남아야 한다
 */
interface PendingVaultDeletions {

    /** @throws Exception 목록을 쓰지 못할 때 */
    fun add(id: VaultId)

    /** @throws Exception 목록을 읽지 못할 때 */
    fun all(): Set<VaultId>

    /** 목록에서 뺀다. 없는 ID는 무시한다 */
    fun removeAll(removed: Set<VaultId>)

    companion object {
        /** 목록을 남기지 않는 구현. 목록 없이 조립하는 테스트가 쓴다 */
        val NONE: PendingVaultDeletions = object : PendingVaultDeletions {
            override fun add(id: VaultId) = Unit
            override fun all(): Set<VaultId> = emptySet()
            override fun removeAll(removed: Set<VaultId>) = Unit
        }
    }
}
