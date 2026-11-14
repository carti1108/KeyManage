package com.keymanage.domain.session

import com.keymanage.domain.crypto.EncryptedVaultPayload
import com.keymanage.domain.device.DeviceRecord
import com.keymanage.domain.device.SyncFields
import com.keymanage.domain.device.mergeSyncFields
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.VaultSync
import com.keymanage.domain.vault.encodeVaultData
import com.keymanage.domain.vault.mergeVaults

// 다른 탭이 쓴 기기 기록을 이 탭의 메모리 볼트와 합친다 (ADR-WEB-001). 원본: Apps/Web/src/domain/session/recordMerge.ts
// 항목 단위로 최종 변경 시각이 늦은 쪽을 고르므로(mergeVaults) 삭제를 되살리지 않는다

internal class MemoryVault(
    val data: VaultData,
    val payload: EncryptedVaultPayload,
    val revision: Int,
    val lastSeenRevision: Int,
    val pendingSync: Boolean,
)

internal class MergedVault(
    val data: VaultData,
    val payload: EncryptedVaultPayload,
    val revision: Int,
    val lastSeenRevision: Int,
    val pendingSync: Boolean,
    /** 합친 내용이 메모리와 다름. 화면을 다시 그려야 한다 */
    val changed: Boolean,
)

// 같은 내용을 다시 암호화하면 IV가 바뀌어 다른 탭이 또 병합하게 된다. 내용이 같으면 기존 암호문을 그대로 쓴다.
// 원본의 JSON.stringify 비교처럼 키 순서까지 같은지 본다
private fun sameData(a: VaultData, b: VaultData): Boolean = encodeVaultData(a) == encodeVaultData(b)

/**
 * 한쪽 내용이 다른 쪽을 모두 담으면 그쪽의 암호문과 대기 표시를 따른다. 그쪽이 서버와 맞춰졌으면 다른 쪽의 변경도 올라간 것이다.
 * 둘 다 새 내용을 가졌으면 새로 암호화하고, 둘 중 하나라도 대기면 대기로 둔다
 * @throws com.keymanage.domain.crypto.VaultDecryptException 등 기록을 열지 못함(다른 탭에서 바뀐 비밀번호로 로그인함)
 */
internal suspend fun mergeRecordIntoMemory(
    mine: MemoryVault,
    theirs: DeviceRecord,
    sync: VaultSync,
    /** 테스트가 열기와 다시 봉인 사이에 잠금을 끼워 넣는 자리 */
    afterOpen: () -> Unit = {},
): MergedVault = sync.holdingKey {
    // 열기부터 다시 봉인까지 한 번 빌린 키로 한다. 그사이 잠겨도 원래 키로 봉인한 결과를 남기고, 키는 끝난 뒤 지운다 (SEC-003)
    val theirData = if (mine.payload == theirs.payload) mine.data else open(theirs.payload)
    afterOpen()
    val merged = mergeVaults(mine.data, theirData)
    val revision = maxOf(mine.revision, theirs.revision)
    val lastSeenRevision = maxOf(mine.lastSeenRevision, theirs.lastSeenRevision)
    if (sameData(merged, theirData)) {
        return@holdingKey MergedVault(theirData, theirs.payload, revision, lastSeenRevision, theirs.pendingSync, changed = !sameData(theirData, mine.data))
    }
    if (sameData(merged, mine.data)) return@holdingKey MergedVault(mine.data, mine.payload, revision, lastSeenRevision, mine.pendingSync, changed = false)
    val fields = mergeSyncFields(SyncFields(mine.revision, mine.lastSeenRevision, mine.pendingSync), theirs)
    MergedVault(merged, seal(merged), fields.revision, fields.lastSeenRevision, fields.pendingSync, changed = true)
}
