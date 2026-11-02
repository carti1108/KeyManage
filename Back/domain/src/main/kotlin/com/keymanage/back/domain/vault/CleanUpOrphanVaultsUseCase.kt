package com.keymanage.back.domain.vault

import com.keymanage.back.domain.auth.AccountRepository

/**
 * 어느 계정도 가리키지 않는 볼트를 지운다 (ADR-BE-001 결정 5). 서버가 요청을 받기 전, 시작 때 한 번 실행한다.
 *
 * 비밀번호 변경이 확정 전에 실패해 남은 새 볼트와, 확정 뒤 지우지 못한 옛 볼트가 대상이다.
 * 볼트를 아직 저장하지 않은 계정은 볼트 파일이 없을 뿐이라 영향이 없다.
 * 계정을 하나라도 읽지 못하거나 계정이 하나도 없으면(저장 폴더 설정 오류일 수 있음) 아무것도 지우지 않는다.
 */
class CleanUpOrphanVaultsUseCase(
    private val accounts: AccountRepository,
    private val vaults: VaultRepository,
) {

    /** @return 지운 볼트 수 */
    fun execute(): Int {
        val referenced = try {
            accounts.allVaultIds()
        } catch (e: Exception) {
            log.log(System.Logger.Level.WARNING, "계정을 읽지 못해 고아 볼트 정리를 건너뜀", e)
            return 0
        }
        if (referenced.isEmpty()) return 0

        return (vaults.allIds() - referenced).count { id ->
            runCatching { vaults.delete(id) }
                .onFailure { log.log(System.Logger.Level.WARNING, "고아 볼트를 지우지 못함: ${id.value}", it) }
                .isSuccess
        }
    }

    private companion object {
        val log: System.Logger = System.getLogger(CleanUpOrphanVaultsUseCase::class.java.name)
    }
}
