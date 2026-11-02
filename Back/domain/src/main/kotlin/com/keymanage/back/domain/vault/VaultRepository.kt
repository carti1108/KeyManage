package com.keymanage.back.domain.vault

/** 볼트 저장소 포트. 구현체는 infrastructure 모듈에 둔다 (ADR-0003) */
interface VaultRepository {

    /** @return 볼트가 없거나 [delete]로 지운 ID면 null */
    fun find(id: VaultId): StoredVault?

    /**
     * 저장된 revision이 [expected]와 같을 때만 [vault]로 교체한다. 볼트가 없으면 [Revision.NONE]으로 본다.
     * 비교와 교체는 원자적이어야 한다: 같은 revision을 기대한 동시 요청 중 하나만 성공한다 (VLT-001).
     * [delete]로 지운 ID에는 저장하지 않는다
     *
     * @return 교체했으면 true, revision이 다르거나 지운 ID라서 교체하지 않았으면 false
     */
    fun replaceIfRevisionMatches(expected: Revision, vault: StoredVault): Boolean

    /**
     * 이 볼트의 잠금을 잡고 [action]을 실행한다. [replaceIfRevisionMatches]와 같은 잠금이며 같은 스레드에서 다시 잡을 수 있다.
     * 비밀번호 변경이 옛 볼트의 저장을 막은 채 교체할 때 쓴다 (ADR-BE-001 결정 3)
     */
    fun <T> withLock(id: VaultId, action: () -> T): T

    /**
     * 아직 쓰지 않은 볼트 ID로 볼트를 새로 쓴다. 반쯤 쓰인 파일이 남지 않아야 한다 (ADR-BE-001 결정 1)
     *
     * @throws IllegalStateException 그 ID의 볼트가 이미 있거나 지운 ID일 때
     */
    fun create(vault: StoredVault)

    /** 볼트를 지운다. 없어도 실패하지 않으며, 지운 ID로는 다시 저장하지 않는다 (ADR-BE-001 결정 5) */
    fun delete(id: VaultId)

    /** 저장된 모든 볼트의 ID */
    fun allIds(): Set<VaultId>
}
