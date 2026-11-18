package com.keymanage.android.domain.backup

import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultFormatError
import com.keymanage.android.domain.vault.VaultItem
import com.keymanage.android.domain.vault.VaultRules
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 복원 결과. [restored]가 0이면 [vault]는 지금 볼트 그대로라 저장하지 않는다. */
data class RestoreResult(val vault: VaultData, val restored: Int)

/**
 * 백업 항목을 지금 볼트에 되돌린다(VLT-004 규칙 6·7, vault-spec 6장 5). 웹 `restoreItems`와 같은 규칙이다.
 * 백업에서 지워지지 않은 항목은 백업 내용으로 되돌리고(지운 항목은 되살림), 지금 볼트에 없으면 뒤에 더한다.
 * 백업에서 지워진 항목, 백업에 없는 항목, 보이는 내용이 같은 항목은 그대로 둔다.
 * 항목의 모르는 필드는 백업 것을, 볼트 최상위 필드는 지금 볼트 것을 쓴다.
 */
object VaultRestorer {
    fun restore(current: VaultData, backup: VaultData, now: Instant): RestoreResult {
        requireUniqueIds(backup)
        val currentById = current.items.associateBy { it.id }
        val replaced = LinkedHashMap<String, VaultItem>()
        val added = mutableListOf<VaultItem>()
        for (saved in backup.items) {
            if (saved.isDeleted) continue
            val existing = currentById[saved.id]
            if (existing != null && !existing.isDeleted && sameContent(existing, saved)) continue
            val restored = saved.copy(updatedAt = restoreTime(existing, now), deletedAt = null)
            if (existing == null) added += restored else replaced[saved.id] = restored
        }
        val count = replaced.size + added.size
        if (count == 0) return RestoreResult(current, 0)

        val items = current.items.map { replaced[it.id] ?: it } + added
        val updatedAt = (items.map { it.updatedAt } + current.updatedAt).max()
        return RestoreResult(current.copy(updatedAt = updatedAt, items = items), count)
    }

    private fun restoreTime(existing: VaultItem?, now: Instant): Instant =
        if (existing == null) now.truncatedTo(ChronoUnit.SECONDS) else VaultRules.nextChangeTime(existing.lastChange, now)

    /** 시각과 삭제 표시를 뺀 보이는 내용이 같은지. */
    private fun sameContent(a: VaultItem, b: VaultItem): Boolean =
        a.copy(createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH, deletedAt = null) ==
            b.copy(createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH, deletedAt = null)

    private fun requireUniqueIds(vault: VaultData) {
        val seen = HashSet<String>()
        for (item in vault.items) {
            if (!seen.add(item.id)) throw VaultFormatError("같은 id가 두 번 나옴: ${item.id}")
        }
    }
}
