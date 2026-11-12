package com.keymanage.domain.backup

import com.keymanage.domain.vault.InvalidVaultDataException
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.VaultItem
import com.keymanage.domain.vault.formatTime
import com.keymanage.domain.vault.parseTimeOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// 백업 항목을 백업 시점 내용으로 되돌린다 (VLT-004 규칙 5~10, ADR-0022 결정 5). 원본: Apps/Web/src/domain/backup/restoreBackup.ts
// 동기화 병합과 달리 복원은 사용자의 명시적 되돌리기이므로, 되돌린 항목의 수정 시각을 복원 시각으로 올려 병합에서도 이기게 한다.
// 세션 상태를 쓰는 restoreBackupInto는 세션과 함께 옮긴다(domain-migration-plan.md 5단계)

data class RestoredVault(
    val data: VaultData,
    /** 실제로 되돌리거나 되살리거나 더한 항목 수 */
    val restored: Int,
)

// "보이는 내용"을 비교할 때 빼는 필드. 시각은 내용이 아니고, 삭제 표시는 따로 본다
private val NOT_CONTENT = setOf("createdAt", "updatedAt", "deletedAt")

/**
 * 백업에서 지워지지 않은 항목을 백업 시점 내용으로 되돌린다. 고친 항목은 백업 내용으로, 지운 항목은 되살리고,
 * 지금 볼트에 없는 항목은 더한다. 수정 시각은 복원 시각(지금 항목보다 늦게)으로 정하고 삭제 표시는 없앤다.
 * 백업에서 지워진 항목, 백업에 없는 항목, 보이는 내용이 백업과 같은 항목은 그대로 둔다.
 * 항목의 모르는 필드는 백업 것을, 볼트 최상위 필드는 지금 볼트 것을 쓴다
 * @return 바뀐 것이 없으면 data는 current 그대로다
 * @throws InvalidVaultDataException 백업에 같은 항목 id가 두 번 이상 있음. 지금 볼트를 바꾸기 전에 거절한다
 */
fun restoreItems(current: VaultData, backup: VaultData, now: Instant): RestoredVault {
    assertUniqueIds(backup)
    val currentById = current.items.associateBy { it.id }
    val replaced = mutableMapOf<String, JsonObject>()
    val added = mutableListOf<JsonObject>()
    for (saved in backup.items) {
        if (saved.isDeleted) continue
        val existing = currentById[saved.id]
        if (existing != null && !existing.isDeleted && sameContent(existing, saved)) continue
        val restored = JsonObject(saved.json + ("updatedAt" to JsonPrimitive(restoreTime(existing, now))))
        if (existing == null) added += restored else replaced[saved.id] = restored
    }
    val count = replaced.size + added.size
    if (count == 0) return RestoredVault(current, 0)
    val items = current.items.map { replaced[it.id] ?: it.json } + added
    val updatedAt = (listOf(current.updatedAt) + items.map { (it.getValue("updatedAt") as JsonPrimitive).content })
        .reduce { a, b -> if (toInstant(b) > toInstant(a)) b else a }
    return RestoredVault(VaultData(JsonObject(current.json + mapOf("updatedAt" to JsonPrimitive(updatedAt), "items" to JsonArray(items)))), count)
}

// parseVaultData도 같은 검사를 하지만, 이 함수만 쓰는 곳에서도 중복 항목이 그대로 더해지지 않게 한다
private fun assertUniqueIds(data: VaultData) {
    val ids = mutableSetOf<String>()
    data.items.forEach { if (!ids.add(it.id)) throw InvalidVaultDataException("같은 id가 두 번 나옴: ${it.id}") }
}

// 같은 항목의 변경 시각은 늘 이전보다 커야 병합에서 이긴다 (vaultData의 nextChangeTime과 같은 규칙)
private fun restoreTime(existing: VaultItem?, now: Instant): String {
    if (existing == null) return formatTime(now)
    val updated = toInstant(existing.updatedAt)
    val last = existing.deletedAt?.let { maxOf(updated, toInstant(it)) } ?: updated
    return formatTime(maxOf(now, last + 1.seconds))
}

/** 시각·삭제 표시를 뺀 모든 필드(모르는 필드 포함)를 필드 순서와 상관없이 비교한다. JSON 객체 비교는 키 순서를 보지 않는다 */
private fun sameContent(a: VaultItem, b: VaultItem): Boolean = contentOf(a) == contentOf(b)

private fun contentOf(item: VaultItem): Map<String, Any> = item.json.filterKeys { it !in NOT_CONTENT }

// 읽을 수 없는 시각은 가장 오래된 것으로 본다 (mergeVaults와 같다)
private fun toInstant(time: String): Instant = parseTimeOrNull(time) ?: Instant.DISTANT_PAST
