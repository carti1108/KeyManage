package com.keymanage.domain.vault

import com.keymanage.domain.crypto.EncryptedVaultPayload
import com.keymanage.domain.crypto.EncryptionKey
import com.keymanage.domain.crypto.openVault
import com.keymanage.domain.crypto.sealVault
import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.server.UnexpectedResponseError
import kotlin.time.Clock

// 볼트 불러오기·저장과 충돌 시 자동 병합 (VLT-001, ADR-0006). 원본: Apps/Web/src/domain/vault/vaultSync.ts, vaultApi.ts
// 평문 볼트와 암호화 키는 이 모듈 안에서만 쓰고, 서버 포트에는 암호화된 페이로드만 넘긴다

/** 볼트 최대 허용 용량. 페이로드 JSON의 UTF-8 바이트 기준이며 같으면 허용한다 (VLT-001) */
const val MAX_PAYLOAD_BYTES: Int = 5_242_880

/** 첫 저장이 충돌한 뒤 병합해 다시 보내는 횟수 (VLT-001) */
private const val MAX_MERGE_RETRIES = 3

data class RemoteVault(val revision: Int, val payload: EncryptedVaultPayload)

/** 볼트 서버 포트 (api-spec 2.3·2.4). 실패는 ServerFailure로 알린다 */
interface VaultApi {
    /** @return 저장된 볼트가 없으면 null */
    suspend fun get(): RemoteVault?

    /**
     * @return 저장된 볼트의 새 revision
     * @throws ServerError 409 VAULT_CONFLICT: expectedRevision이 서버 값과 다를 때
     */
    suspend fun save(expectedRevision: Int, payload: EncryptedVaultPayload): Int
}

class VaultSyncConflictException : Exception("자동 병합 재시도를 모두 실패함")

class ReadOnlyVaultException : Exception("새 버전 앱에서 저장한 볼트라 저장하지 않음")

class VaultTooLargeException(val sizeInBytes: Int) : Exception("볼트가 허용 용량을 넘음: ${sizeInBytes}바이트")

/** 저장 응답의 revision이 기대값(+1)과 다름. 서버가 규약을 어겼으므로 잠시 실패로 숨기지 않는다 */
class RevisionMismatchException(expected: Int, actual: Int) : UnexpectedResponseError("저장 응답 revision이 ${expected}이 아님: $actual")

/** 서버 revision이 이 기기가 마지막으로 본 값보다 낮음. 사용자가 확인해야 받아들인다 (VLT-001) */
class RevisionRollbackException(val lastSeenRevision: Int, val serverRevision: Int) :
    Exception("서버 볼트가 되돌아감: $lastSeenRevision → $serverRevision")

class SyncedVault(
    val revision: Int,
    val data: VaultData,
    val readOnly: Boolean,
    /** 서버에 있는 암호화 볼트. 잠금 해제 확인에 쓴다 (SEC-003). 서버에 볼트가 없으면 null */
    val payload: EncryptedVaultPayload?,
)

/** 이 기기가 마지막으로 본 서버 revision. 잠금 해제·재로그인으로 동기화를 새로 만들어도 이어서 쓴다 */
class RevisionMemory(initial: Int = 0) {
    private var lastSeen = initial

    fun get(): Int = lastSeen

    /** 더 큰 값일 때만 바꾼다. 응답 순서가 뒤바뀌어도 기준이 낮아지지 않는다 */
    fun raise(revision: Int) {
        lastSeen = maxOf(lastSeen, revision)
    }

    /** 사용자가 되돌림을 받아들였을 때만 쓴다 */
    fun reset(revision: Int) {
        lastSeen = revision
    }
}

/**
 * 서버에 닿지 못했거나 서버가 잠시 처리하지 못한 실패. 변경 사항을 동기화 대기로 두고 나중에 다시 보낸다 (VLT-002).
 * 응답 형식 오류는 공용 와이파이 로그인 페이지처럼 중간에서 가로챈 응답일 수 있다
 */
fun isTransientSyncError(error: Throwable): Boolean = when (error) {
    is RevisionMismatchException -> false
    is NetworkError, is UnexpectedResponseError -> true
    is ServerError -> error.status >= 500 || error.status == 429
    else -> false
}

/** @throws VaultTooLargeException */
fun assertWithinLimit(payload: EncryptedVaultPayload) {
    val size = payload.toJson().toString().encodeToByteArray().size
    if (size > MAX_PAYLOAD_BYTES) throw VaultTooLargeException(size)
}

class VaultSync(
    private val api: VaultApi,
    private val encryptionKey: EncryptionKey,
    private val clock: Clock,
    private val revisionMemory: RevisionMemory,
) {
    /**
     * 서버에 보내지 않고 저장 전 검사와 암호화만 한다. 동기화 대기 내용을 기기에 남길 때 쓴다
     * @throws ReadOnlyVaultException, InvalidVaultDataException, VaultTooLargeException
     */
    suspend fun seal(data: VaultData): EncryptedVaultPayload {
        if (isReadOnly(data)) throw ReadOnlyVaultException()
        val json = encodeVaultData(data)
        // 규격에 맞지 않는 볼트를 올리면 모든 기기가 열지 못하게 되므로 보내기 전에 다시 확인한다
        parseVaultData(json)
        val payload = sealVault(encryptionKey, json)
        assertWithinLimit(payload)
        return payload
    }

    /** 백업 파일에 담을 볼트를 새 IV로 암호화한다. 읽기 전용 볼트도 내보내므로 저장 전 검사를 하지 않는다 (VLT-004) */
    suspend fun sealBackup(data: VaultData): EncryptedVaultPayload = sealVault(encryptionKey, encodeVaultData(data))

    /** 이 기기에 남은 암호화 볼트(다른 탭이 쓴 기기 기록 등)를 연다. 서버에 요청하지 않는다 */
    suspend fun open(payload: EncryptedVaultPayload): VaultData = parseVaultData(openVault(encryptionKey, payload))

    /** @param acceptRollback 되돌림 경고를 사용자가 확인했을 때만 true */
    suspend fun load(acceptRollback: Boolean = false): SyncedVault = fetchRemote(acceptRollback)

    /**
     * [base]는 [data]를 만들 때 바탕으로 삼은 불러오기·저장 결과다. 충돌하면 서버 볼트와 병합해 다시 저장한다
     * @return 실제로 저장된(병합됐을 수 있는) 볼트
     */
    suspend fun save(base: SyncedVault, data: VaultData): SyncedVault {
        var expectedRevision = base.revision
        var toSave = data
        var attempt = 0
        while (true) {
            val payload = seal(toSave)
            try {
                val revision = api.save(expectedRevision, payload)
                // 서버는 저장할 때마다 1씩 올린다. 다른 값이면 되돌림 감지 기준을 흔들 수 있으므로 믿지 않는다
                if (revision != expectedRevision + 1) throw RevisionMismatchException(expectedRevision + 1, revision)
                revisionMemory.raise(revision)
                return SyncedVault(revision, toSave, readOnly = false, payload = payload)
            } catch (error: ServerError) {
                if (error.code != ServerErrorCode.VAULT_CONFLICT) throw error
                if (attempt == MAX_MERGE_RETRIES) throw VaultSyncConflictException()
            }
            val remote = fetchRemote(acceptRollback = false)
            toSave = mergeVaults(toSave, remote.data)
            expectedRevision = remote.revision
            attempt++
        }
    }

    private suspend fun fetchRemote(acceptRollback: Boolean): SyncedVault {
        // 요청을 보내는 시점의 값과 비교한다. 응답을 기다리는 사이 저장이 끝나 기준이 올라가도 되돌림으로 오해하지 않는다
        val seenBeforeRequest = revisionMemory.get()
        val remote = api.get()
        val revision = remote?.revision ?: 0
        if (revision < seenBeforeRequest && !acceptRollback) throw RevisionRollbackException(seenBeforeRequest, revision)
        val data = remote?.let { parseVaultData(openVault(encryptionKey, it.payload)) } ?: emptyVault(clock.now())
        if (acceptRollback) revisionMemory.reset(revision) else revisionMemory.raise(revision)
        return SyncedVault(revision, data, isReadOnly(data), remote?.payload)
    }
}
