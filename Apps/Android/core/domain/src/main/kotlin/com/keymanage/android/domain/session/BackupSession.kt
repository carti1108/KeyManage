package com.keymanage.android.domain.session

import com.keymanage.android.domain.backup.BackupUseCase
import com.keymanage.android.domain.crypto.KdfParameters
import com.keymanage.android.domain.vault.VaultData
import java.time.Instant
import java.util.Base64

/** 복원 결과. 되돌린 항목이 없으면 [saved]는 null이고 저장하지 않는다. */
class RestoredBackup(val restored: Int, val saved: SavedVault?)

/**
 * 잠금이 풀린 볼트의 백업 내보내기·복원(VLT-004). 키와 계정 KDF 값은 [OpenedVault]에서 가져오고,
 * 복원 결과는 일반 수정처럼 [save]로 저장한다(규칙 9). 서버에 따로 보내거나 남기지 않는다(규칙 11).
 */
class BackupSession(
    private val backup: BackupUseCase,
    private val save: (OpenedVault, VaultData) -> SavedVault,
) {
    /** 화면의 볼트(대기 중인 변경 포함)를 지금 키로 다시 암호화한 파일 바이트(규칙 2). */
    fun export(opened: OpenedVault, vault: VaultData, now: Instant): ByteArray {
        val kdf = KdfParameters(Base64.getDecoder().decode(opened.account.kdfSalt), opened.account.kdfIterations)
        return backup.export(vault, opened.encryptionKey, kdf, now)
    }

    /** 파일의 비밀번호로 열어 [current]에 되돌리고 저장한다. 실패는 `BackupError`·`SyncError`로 던진다. */
    fun restore(opened: OpenedVault, current: VaultData, bytes: ByteArray, password: String, now: Instant): RestoredBackup {
        val result = backup.restore(current, bytes, password, now)
        if (result.restored == 0) return RestoredBackup(0, null)
        return RestoredBackup(result.restored, save(opened, result.vault))
    }
}
