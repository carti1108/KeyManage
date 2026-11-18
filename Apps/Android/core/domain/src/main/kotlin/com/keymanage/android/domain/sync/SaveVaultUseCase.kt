package com.keymanage.android.domain.sync

import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import com.keymanage.android.domain.crypto.VaultCipher
import com.keymanage.android.domain.remote.ApiError
import com.keymanage.android.domain.remote.ServerErrorCode
import com.keymanage.android.domain.remote.VaultApi
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultMerger
import com.keymanage.android.domain.vault.VaultSerializer

/** 저장에 성공한 볼트와 서버가 발급한 revision. */
data class SaveResult(val vault: VaultData, val revision: Int)

/** 동기화를 멈춰야 하는 경우(VLT-001). */
sealed class SyncError(message: String) : Exception(message) {
    /** 409 자동 병합을 [retries]번 다시 해도 서버가 먼저 바뀌었다. */
    class ConflictRetriesExhausted(val retries: Int) : SyncError("자동 병합 재시도 ${retries}회 초과")

    /** 서버 revision이 이 기기가 마지막으로 본 값보다 낮다. 사용자 확인 전까지 서버 볼트를 받아들이지 않는다(규칙 9). */
    class RevisionRollback(val serverRevision: Int, val lastSeenRevision: Int) :
        SyncError("서버 revision $serverRevision < 마지막으로 본 revision $lastSeenRevision")

    /** 앱이 아는 것보다 높은 버전의 볼트. 저장하지 않는다(규칙 8). */
    class ReadOnlyVault(val version: Int) : SyncError("읽기 전용 볼트(버전 $version)")

    /** 페이로드 JSON이 5MB를 넘는다. */
    class VaultTooLarge(val bytes: Int) : SyncError("볼트 페이로드 ${bytes}바이트가 상한을 넘는다")

    /** 저장 응답 revision이 expectedRevision + 1이 아니다. 서버 규약 위반이라 대기로 숨기지 않는다(VLT-002 규칙 4). */
    class UnexpectedRevision(val expected: Int, val actual: Int) : SyncError("저장 응답 revision $actual, 기대값 $expected")
}

/**
 * 볼트를 암호화해 서버에 저장한다(VLT-001 규칙 3~5·8·9, 값 표).
 * 409를 받으면 서버 볼트를 받아 항목별로 병합하고 서버 revision으로 다시 저장한다. 재시도는 최대 3회다.
 */
class SaveVaultUseCase(
    private val api: VaultApi,
    private val cipher: VaultCipher,
    private val serializer: VaultSerializer,
) {
    /**
     * @param baseRevision 이 볼트를 만든 기준 revision. 볼트가 없던 계정은 0.
     * @param lastSeenRevision 이 기기가 서버에서 마지막으로 본 revision. 되돌림 감지에 쓴다.
     */
    fun save(vault: VaultData, baseRevision: Int, lastSeenRevision: Int, key: ByteArray, accessToken: String): SaveResult {
        var current = vault
        var expected = baseRevision
        var seen = lastSeenRevision
        for (attempt in 0..MAX_MERGE_RETRIES) {
            if (current.isReadOnly) throw SyncError.ReadOnlyVault(current.version)
            val payload = encrypt(current, key)
            try {
                val revision = api.saveVault(accessToken, expected, payload)
                if (revision != expected + 1) throw SyncError.UnexpectedRevision(expected + 1, revision)
                return SaveResult(current, revision)
            } catch (error: ApiError.Server) {
                if (error.code != ServerErrorCode.VAULT_CONFLICT) throw error
                if (attempt == MAX_MERGE_RETRIES) break
            }
            val remote = api.fetchVault(accessToken)
            val remoteRevision = remote?.revision ?: 0
            if (remoteRevision < seen) throw SyncError.RevisionRollback(remoteRevision, seen)
            seen = remoteRevision
            expected = remoteRevision
            if (remote != null) {
                val serverVault = serializer.decode(cipher.decrypt(remote.payload, key))
                if (serverVault.isReadOnly) throw SyncError.ReadOnlyVault(serverVault.version)
                current = VaultMerger.merge(current, serverVault)
            }
        }
        throw SyncError.ConflictRetriesExhausted(MAX_MERGE_RETRIES)
    }

    private fun encrypt(vault: VaultData, key: ByteArray): EncryptedVaultPayload {
        val plaintext = serializer.encode(vault)
        try {
            val payload = cipher.encrypt(plaintext, key)
            val size = payload.canonicalJson().toByteArray(Charsets.UTF_8).size
            if (size > MAX_PAYLOAD_BYTES) throw SyncError.VaultTooLarge(size)
            return payload
        } finally {
            plaintext.fill(0)
        }
    }

    companion object {
        /** 자동 병합 재시도 횟수. 첫 저장을 포함해 최대 4번 요청한다(VLT-001 값 표). */
        const val MAX_MERGE_RETRIES = 3

        /** 볼트 페이로드 JSON 상한. 이 값과 같으면 허용한다(VLT-001 값 표). */
        const val MAX_PAYLOAD_BYTES = 5_242_880
    }
}
