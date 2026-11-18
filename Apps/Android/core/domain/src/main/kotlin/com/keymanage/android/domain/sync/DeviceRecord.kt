package com.keymanage.android.domain.sync

import com.keymanage.android.domain.crypto.EncryptedVaultPayload

/** 기기 기록의 계정 식별값. KDF 값이 바뀌면 대기 내용을 버린다(VLT-003 규칙 5). [kdfSalt]는 Base64. */
data class DeviceAccount(val email: String, val kdfIterations: Int, val kdfSalt: String)

/**
 * 기기에 두는 한 계정의 기록(VLT-003 규칙 1). 볼트는 암호화된 페이로드로만 둔다.
 *
 * @param baseRevision 마지막으로 서버와 맞춘 revision. 대기 내용을 올릴 때 expectedRevision으로 쓴다.
 * @param lastSeenRevision 서버에서 마지막으로 본 revision. 되돌림 감지 기준이다.
 * @param hasPending [payload]가 서버에 아직 없는 대기 내용인지.
 */
data class DeviceRecord(
    val account: DeviceAccount,
    val payload: EncryptedVaultPayload,
    val baseRevision: Int,
    val lastSeenRevision: Int,
    val hasPending: Boolean,
)

/** 기기 기록 저장소. 기기에는 한 계정만 둔다(VLT-003 규칙 2). 실패는 [DeviceStorageError]로 던진다. */
interface DeviceRecordStore {
    fun read(): DeviceRecord?
    fun write(record: DeviceRecord)
    fun clear()
}

/** 기기 기록을 읽거나 쓰거나 지우지 못했다(VLT-003 규칙 6~8). */
class DeviceStorageError(message: String, cause: Throwable? = null) : Exception(message, cause)
