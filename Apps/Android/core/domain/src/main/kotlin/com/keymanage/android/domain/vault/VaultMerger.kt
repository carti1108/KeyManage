package com.keymanage.android.domain.vault

/**
 * 두 볼트를 항목별로 병합한다(VLT-001 규칙 5, vault-spec 4장 "병합과 호환 규칙").
 * 웹 `mergeVaults`와 같은 결과를 낸다: 서버 항목 순서 뒤에 이 기기에만 있는 항목을 붙인다.
 */
object VaultMerger {
    fun merge(local: VaultData, server: VaultData): VaultData {
        val localById = local.items.associateBy { it.id }
        val serverIds = server.items.mapTo(HashSet()) { it.id }

        val merged = server.items.map { serverItem ->
            val localItem = localById[serverItem.id]
            // 시각이 같으면 서버 쪽을 고른다
            if (localItem != null && localItem.lastChange > serverItem.lastChange) localItem else serverItem
        }
        val localOnly = local.items.filterNot { it.id in serverIds }

        // 모르는 최상위 필드와 버전은 서버 쪽을 따른다
        return server.copy(
            updatedAt = maxOf(local.updatedAt, server.updatedAt),
            items = merged + localOnly,
        )
    }
}
