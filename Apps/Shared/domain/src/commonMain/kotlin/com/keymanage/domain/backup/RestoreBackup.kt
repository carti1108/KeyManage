package com.keymanage.domain.backup

import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.SessionStateException
import com.keymanage.domain.vault.ReadOnlyVaultException
import com.keymanage.domain.vault.VaultData
import kotlin.time.Instant

// 백업을 열어 백업에 있던 항목을 백업 시점 내용으로 되돌리고 일반 저장 흐름으로 저장한다 (VLT-004 규칙 5~10, ADR-0022 결정 5)
// 원본: Apps/Web/src/domain/backup/restoreBackup.ts의 restoreBackupInto. 항목 규칙은 RestoreItems.kt

/** 복원이 쓰는 세션 기능. 저장은 서버에 닿지 못하면 동기화 대기로 둔다 (VLT-002) */
interface RestoreTarget {
    fun state(): SessionState

    suspend fun save(data: VaultData)
}

/**
 * @param now 복원 시각. 되돌린 항목의 수정 시각이 된다
 * @return 복원한 항목 수(restoreItems). 0이면 저장하지 않는다
 * @throws ReadOnlyVaultException, SessionStateException(잠김·계정 바뀜), openBackup의 오류, save의 오류
 */
suspend fun restoreBackupInto(target: RestoreTarget, backup: VaultBackup, password: String, now: Instant): Int {
    val before = writableUnlocked(target)
    val backupData = openBackup(backup, password)
    // 키를 만드는 사이 잠기거나 다른 계정으로 바뀌었으면 복원하지 않는다. 그사이 고친 내용은 지금 볼트에 있다
    val current = writableUnlocked(target)
    if (current.email != before.email) throw SessionStateException("복원하는 사이 계정이 바뀜")
    val (data, restored) = restoreItems(current.vault.data, backupData, now)
    if (restored > 0) target.save(data)
    return restored
}

private fun writableUnlocked(target: RestoreTarget): SessionState.Unlocked {
    val state = target.state() as? SessionState.Unlocked ?: throw SessionStateException("잠금 해제 상태가 아님")
    if (state.vault.readOnly) throw ReadOnlyVaultException()
    return state
}
