package com.keymanage.back.domain.vault

/**
 * 낙관적 잠금으로 볼트를 저장한다 (VLT-001, ADR-0006).
 *
 * 용량 검사 -> 기대 revision이 같을 때만 다음 revision으로 교체 -> 다르면 현재 revision을 담아 충돌로 거절.
 * 병합은 서버가 아니라 충돌을 받은 클라이언트가 한다.
 */
class SaveVaultUseCase(private val repository: VaultRepository) {

    /**
     * @return 저장된 볼트의 새 revision
     * @throws VaultTooLargeException 페이로드가 [MAX_PAYLOAD_BYTES]를 넘을 때 (저장하지 않음)
     * @throws VaultConflictException 기대 revision이 서버의 현재 revision과 다를 때 (저장하지 않음)
     */
    fun execute(id: VaultId, expectedRevision: Revision, vault: EncryptedVault): Revision {
        if (vault.sizeInBytes > MAX_PAYLOAD_BYTES) throw VaultTooLargeException(vault.sizeInBytes)

        val saved = StoredVault(id, expectedRevision.next(), vault)
        if (repository.replaceIfRevisionMatches(expectedRevision, saved)) return saved.revision

        // 교체에 실패한 뒤 읽으므로 그 사이 또 바뀌었을 수 있지만, 클라이언트는 어차피 최신 볼트를 다시 받아 병합한다
        throw VaultConflictException(repository.find(id)?.revision ?: Revision.NONE)
    }

    companion object {
        /** 볼트 최대 허용 용량 5MB (VLT-001) */
        const val MAX_PAYLOAD_BYTES = 5_242_880
    }
}
