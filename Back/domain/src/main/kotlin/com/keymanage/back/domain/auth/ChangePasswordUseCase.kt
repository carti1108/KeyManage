package com.keymanage.back.domain.auth

import com.keymanage.back.domain.vault.EncryptedVault
import com.keymanage.back.domain.vault.Revision
import com.keymanage.back.domain.vault.SaveVaultUseCase
import com.keymanage.back.domain.vault.StoredVault
import com.keymanage.back.domain.vault.VaultConflictException
import com.keymanage.back.domain.vault.VaultId
import com.keymanage.back.domain.vault.VaultRepository
import com.keymanage.back.domain.vault.VaultTooLargeException
import java.util.UUID

/** 비밀번호 변경 요청. [vault]는 클라이언트가 새 키로 다시 암호화한 볼트다 (api-spec 2.5.5) */
data class PasswordChange(
    val currentAuthHash: AuthHash,
    val newAuthHash: AuthHash,
    val newKdf: KdfParams,
    val expectedRevision: Revision,
    val vault: EncryptedVault,
)

/** 비밀번호 변경 결과: 바꾼 기기에만 주는 새 세션과 교체한 볼트의 revision */
data class PasswordChanged(val session: IssuedSession, val revision: Revision)

/**
 * 마스터 비밀번호 변경: KDF 값·인증 해시·볼트를 전부 바꾸거나 전부 그대로 둔다 (SEC-004, ADR-0023, ADR-BE-001).
 *
 * 입력 검사 -> 시도 제한 확인 -> 계정 잠금 -> 현재 인증 해시 검증 -> 옛 볼트 잠금 -> revision 비교
 * -> 새 볼트 ID로 볼트 쓰기 -> 계정 원자 교체(확정) -> 옛 볼트 삭제 -> 잠금 해제 -> 계정 세션 모두 폐기 -> 새 토큰 발급.
 * 확정 전에 실패하면 새로 쓴 볼트를 지우고 옛 계정·볼트는 그대로 둔다. 확정 뒤 옛 볼트를 지우지 못해도 성공이다(시작 때 정리).
 */
class ChangePasswordUseCase(
    private val accounts: AccountRepository,
    private val vaults: VaultRepository,
    private val sessions: SessionRepository,
    private val hasher: PasswordHasher,
    private val issuer: SessionIssuer,
    private val limiter: LoginAttemptLimiter,
    private val newVaultId: () -> VaultId = { VaultId.of(UUID.randomUUID().toString()) },
) {

    /**
     * @param email 요청 토큰의 계정
     * @throws InvalidAuthInputException 반복 횟수가 상한을 넘을 때
     * @throws VaultTooLargeException 볼트가 5MB를 넘을 때
     * @throws TooManyAttemptsException 계정이 잠겨 있을 때 (해시 검증을 하지 않음)
     * @throws UnauthorizedException 토큰의 계정이 없을 때
     * @throws AuthFailedException 현재 인증 해시가 틀릴 때 (실패로 기록)
     * @throws VaultConflictException 기대 revision이 서버 revision과 다를 때
     */
    fun execute(email: Email, change: PasswordChange): PasswordChanged {
        validate(change)
        limiter.checkAllowed(email)

        val revision = accounts.withLock(email) {
            val account = accounts.find(email) ?: throw UnauthorizedException()
            verifyCurrent(account, change.currentAuthHash)
            vaults.withLock(account.vaultId) { swap(account, change) }
        }

        sessions.deleteAllOf(email)
        return PasswordChanged(issuer.issue(email), revision)
    }

    private fun validate(change: PasswordChange) {
        if (change.newKdf.iterations > KdfParams.MAX_ITERATIONS) throw InvalidAuthInputException("kdfIterations")
        if (change.vault.sizeInBytes > SaveVaultUseCase.MAX_PAYLOAD_BYTES) throw VaultTooLargeException(change.vault.sizeInBytes)
    }

    private fun verifyCurrent(account: Account, currentAuthHash: AuthHash) {
        if (!hasher.verify(currentAuthHash, account.passwordHash)) {
            limiter.recordFailure(account.email)
            throw AuthFailedException()
        }
        limiter.recordSuccess(account.email)
    }

    // 계정 잠금과 옛 볼트 잠금을 모두 잡은 상태에서 호출된다
    private fun swap(account: Account, change: PasswordChange): Revision {
        val current = vaults.find(account.vaultId)?.revision ?: Revision.NONE
        if (current != change.expectedRevision) throw VaultConflictException(current)

        val newVault = StoredVault(newVaultId(), current.next(), change.vault)
        val replacement = account.copy(kdf = change.newKdf, passwordHash = hasher.hash(change.newAuthHash), vaultId = newVault.id)
        vaults.create(newVault)
        commit(account, replacement)

        try {
            vaults.delete(account.vaultId)
        } catch (e: Exception) {
            // 이미 확정됐으므로 성공으로 처리한다. 남은 파일은 어느 계정도 가리키지 않아 다음 시작 때 지운다
            log.log(System.Logger.Level.WARNING, "비밀번호 변경 뒤 옛 볼트를 지우지 못함", e)
        }
        return newVault.revision
    }

    private fun commit(account: Account, replacement: Account) {
        val replaced = try {
            accounts.replaceIfMatches(account, replacement)
        } catch (e: Exception) {
            discard(replacement.vaultId, e)
            throw e
        }
        if (!replaced) {
            val error = IllegalStateException("계정 잠금 안에서 계정이 바뀜")
            discard(replacement.vaultId, error)
            throw error
        }
    }

    // 확정 전 실패로 남은 새 볼트를 지운다. 지우지 못해도 원래 오류를 알리고, 남은 파일은 시작 때 정리된다
    private fun discard(id: VaultId, cause: Exception) {
        try {
            vaults.delete(id)
        } catch (e: Exception) {
            cause.addSuppressed(e)
        }
    }

    private companion object {
        val log: System.Logger = System.getLogger(ChangePasswordUseCase::class.java.name)
    }
}
