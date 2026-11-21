package com.keymanage.domain.vault

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

// 웹 TypeScript 도메인과 바이트 단위로 같은 볼트 JSON을 만드는지 확인한다 (domain-migration-plan.md 1단계)
// 기대값은 compat/generate-vault-fixtures.ts가 실제 웹 코드로 만든 것이다. 편집 순서도 그 스크립트와 같다
class VaultCompatTest {
    private val expected = Json.parseToJsonElement(VAULT_COMPAT_JSON).jsonObject.mapValues { it.value.jsonPrimitive.content }
    private val tricky = "q\"uote \\back\nline\ttab \u0001 \u2028 </script> 이모지😀 \$dollar"
    private val t1 = Instant.parse("2026-10-29T00:01:00Z")
    private val t2 = Instant.parse("2026-10-29T00:02:00Z")

    private val parsed = parseVaultData(expected.getValue("fixture"))
    private val added = addItem(parsed, ItemFields(serviceName = "새 항목", username = "new", password = tricky, notes = "메모"), t1, id = "n-1")
    private val updated = updateItem(added, "a", ItemChanges(password = "p2", url = OptionalChange.Remove, notes = OptionalChange.Set("새 메모")), t1)
    private val deleted = deleteItem(updated, "b", t2)
    private val remote = updateItem(parsed, "b", ItemChanges(username = "remote-ub"), t2)

    @Test
    fun parsedVaultReEncodesLikeWeb() = check("parsed", parsed)

    @Test
    fun addItemMatchesWeb() = check("added", added)

    @Test
    fun updateItemMatchesWeb() = check("updated", updated)

    @Test
    fun deleteItemMatchesWeb() = check("deleted", deleted)

    @Test
    fun mergeMatchesWebInBothDirections() {
        check("mergedLocalFirst", mergeVaults(deleted, remote))
        check("mergedRemoteFirst", mergeVaults(remote, deleted))
    }

    @Test
    fun emptyVaultMatchesWeb() = check("empty", emptyVault(t1))

    private fun check(name: String, actual: VaultData) {
        assertEquals(expected.getValue(name), encodeVaultData(actual), name)
    }
}
