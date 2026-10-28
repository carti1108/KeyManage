package com.keymanage.back.domain.vault

/** 볼트 저장소 포트. 구현체는 infrastructure 모듈에 둔다 (ADR-0003) */
interface VaultRepository {

    fun find(id: VaultId): StoredVault?

    /**
     * 저장된 revision이 [expected]와 같을 때만 [vault]로 교체한다. 볼트가 없으면 [Revision.NONE]으로 본다.
     * 비교와 교체는 원자적이어야 한다: 같은 revision을 기대한 동시 요청 중 하나만 성공한다 (VLT-001)
     *
     * @return 교체했으면 true, revision이 달라 교체하지 않았으면 false
     */
    fun replaceIfRevisionMatches(expected: Revision, vault: StoredVault): Boolean
}
