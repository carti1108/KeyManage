package com.keymanage.back.domain.vault

import com.keymanage.back.domain.auth.AccountRepository

/**
 * 삭제 예정 목록의 볼트를 지운다 (ADR-BE-002 결정 2). 서버가 요청을 받기 전, 시작 때 한 번 실행한다.
 *
 * 계정 수와 관계없이 하므로 마지막 계정을 지울 때 남은 볼트도 정리된다. 목록에 적힌 ID만 지우므로 저장 폴더 설정이 틀려도
 * 다른 볼트는 지우지 않는다. 지금 어느 계정이 가리키는 ID는 지우지 않고 목록에 남긴다. 계정이나 목록을 읽지 못하면 아무것도 지우지 않는다.
 */
class CleanUpPendingVaultDeletionsUseCase(
    private val accounts: AccountRepository,
    private val vaults: VaultRepository,
    private val pending: PendingVaultDeletions,
) {

    /** @return 지운 볼트 수 */
    fun execute(): Int {
        val (listed, referenced) = try {
            pending.all() to accounts.allVaultIds()
        } catch (e: Exception) {
            log.log(System.Logger.Level.WARNING, "삭제 예정 목록이나 계정을 읽지 못해 정리를 건너뜀", e)
            return 0
        }
        val removed = mutableSetOf<VaultId>()
        for (id in listed - referenced) {
            try {
                vaults.delete(id)
                removed += id
            } catch (e: Exception) {
                // 지우지 못한 ID는 목록에 남겨 다음 시작 때 다시 시도한다
                log.log(System.Logger.Level.WARNING, "삭제 예정 볼트를 지우지 못함: ${id.value}", e)
            }
        }
        if (removed.isNotEmpty()) pending.removeAll(removed)
        return removed.size
    }

    private companion object {
        val log: System.Logger = System.getLogger(CleanUpPendingVaultDeletionsUseCase::class.java.name)
    }
}
