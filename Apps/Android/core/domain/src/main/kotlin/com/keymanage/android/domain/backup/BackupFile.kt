package com.keymanage.android.domain.backup

import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import java.time.Instant

/**
 * 암호화 백업 파일(vault-spec 6장). 이메일·revision·세션은 담지 않는다(VLT-004 규칙 3).
 * [kdfSalt]는 Standard Base64. 범위 검사는 열 때 [com.keymanage.android.domain.crypto.KdfParameters]가 한다.
 */
data class BackupFile(
    val createdAt: Instant,
    val kdfIterations: Int,
    val kdfSalt: String,
    val vault: EncryptedVaultPayload,
)

/** 백업 파일 바이트 변환. 형식·크기 위반은 [BackupError.NotBackupFile], 높은 형식 버전은 [BackupError.NewerVersion]. */
interface BackupCodec {
    fun encode(file: BackupFile): ByteArray
    fun decode(bytes: ByteArray): BackupFile
}

/** 백업을 열지 못한 이유. 문구는 VLT-004 사용자 문구 표를 따른다. */
sealed class BackupError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** 형식이 아니거나 크기 초과, KDF 값이 범위 밖. */
    class NotBackupFile(message: String, cause: Throwable? = null) : BackupError(message, cause)

    /** 더 새 버전의 앱에서 만든 백업(형식 버전 또는 볼트 스키마 버전). */
    class NewerVersion(message: String) : BackupError(message)

    /** 비밀번호가 틀렸거나 파일이 손상됨. AES-GCM은 둘을 구분하지 못한다. */
    class CannotOpen(cause: Throwable? = null) : BackupError("백업을 열 수 없다", cause)
}
