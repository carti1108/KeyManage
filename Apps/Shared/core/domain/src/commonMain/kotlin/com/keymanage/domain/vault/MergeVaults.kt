package com.keymanage.domain.vault

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

// 409 충돌 뒤 로컬 볼트와 서버 볼트를 항목 단위로 합친다 (vault-spec 4장, VLT-001)
// 원본: Apps/Web/src/domain/vault/mergeVaults.ts

/** 같은 id면 최종 변경 시각이 늦은 쪽을 통째로 고르고, 같으면 서버(remote) 쪽을 고른다 */
fun mergeVaults(local: VaultData, remote: VaultData): VaultData {
    val localById = local.items.associateBy { it.id }
    val remoteIds = remote.items.mapTo(mutableSetOf()) { it.id }

    val merged = remote.items.map { remoteItem ->
        val localItem = localById[remoteItem.id]
        if (localItem != null && lastChange(localItem) > lastChange(remoteItem)) localItem else remoteItem
    }
    val localOnly = local.items.filterNot { it.id in remoteIds }

    // 모르는 최상위 필드는 서버 쪽을 따른다. 키 순서도 서버 쪽 그대로다
    return VaultData(
        JsonObject(
            remote.json + mapOf(
                "updatedAt" to JsonPrimitive(later(local.updatedAt, remote.updatedAt)),
                "items" to JsonArray((merged + localOnly).map { it.json }),
            ),
        ),
    )
}

private fun lastChange(item: VaultItem): Instant {
    val updated = toInstant(item.updatedAt)
    val deleted = item.deletedAt ?: return updated
    return maxOf(updated, toInstant(deleted))
}

private fun later(a: String, b: String): String = if (toInstant(a) > toInstant(b)) a else b

// 읽을 수 없는 시각은 가장 오래된 것으로 본다. 그래야 형식이 맞는 쪽이 이긴다
private fun toInstant(time: String): Instant = parseTimeOrNull(time) ?: Instant.DISTANT_PAST
