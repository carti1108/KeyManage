package com.keymanage.domain.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

// 원본: Apps/Web/src/domain/vault/itemList.test.ts
private val NOW = Instant.parse("2026-10-31T00:00:00Z")

private fun vaultWithNames(vararg names: String): VaultData = names.foldIndexed(emptyVault(NOW)) { index, data, name ->
    addItem(
        data,
        ItemFields(serviceName = name, username = "${name.lowercase()}@me", password = "pw", url = "https://${name.lowercase()}.com"),
        NOW,
        id = "id-$index",
    )
}

class ListItemsTest {
    @Test
    fun excludesDeleted_andSortsKoreanFirstThenLatinIgnoringCase() {
        val data = deleteItem(vaultWithNames("GitHub", "네이버", "apple", "Zoom", "구글"), "id-3", NOW)

        assertEquals(listOf("구글", "네이버", "apple", "GitHub"), listItems(data, "").map { it.serviceName })
    }

    // 원본 Intl.Collator의 numeric 옵션과 같이 숫자는 값으로 비교한다
    @Test
    fun comparesNumbersByValue() {
        val data = vaultWithNames("계정 10", "계정 2", "Item 10", "Item 2")

        assertEquals(listOf("계정 2", "계정 10", "Item 2", "Item 10"), listItems(data, "").map { it.serviceName })
    }

    @Test
    fun sameNameSortsById() {
        assertEquals(listOf("id-0", "id-1"), listItems(vaultWithNames("Same", "Same"), "").map { it.id })
    }

    @Test
    fun searchesServiceNameUsernameAndUrlIgnoringCase() {
        val data = vaultWithNames("GitHub", "Google", "Naver")

        assertEquals(listOf("GitHub"), listItems(data, "  git ").map { it.serviceName })
        assertEquals(listOf("Naver"), listItems(data, "NAVER@").map { it.serviceName })
        assertEquals(listOf("Google"), listItems(data, "google.com").map { it.serviceName })
    }

    @Test
    fun doesNotSearchPasswordOrNotes() {
        val data = addItem(emptyVault(NOW), ItemFields(serviceName = "A", username = "", password = "secret123", notes = "secret123"), NOW, id = "a")

        assertEquals(emptyList(), listItems(data, "secret123"))
    }
}

class CheckItemFieldsTest {
    @Test
    fun blankServiceNameIsMissing() {
        assertEquals(ItemFieldsProblem.MISSING_SERVICE_NAME, checkItemFields(ItemFields(serviceName = "   ", username = "", password = "")))
    }

    @Test
    fun onlyServiceNameIsRequired() {
        assertNull(checkItemFields(ItemFields(serviceName = "GitHub", username = "", password = "")))
    }
}

// 원본 itemList.ts의 itemChanges. 원본에는 전용 테스트가 없어 주석의 규칙에서 만들었다
class ItemChangesTest {
    private val item = addItem(
        emptyVault(NOW),
        ItemFields(serviceName = "GitHub", username = "octocat", password = "pw", url = "https://github.com"),
        NOW,
        id = "a",
    ).items[0]

    @Test
    fun noChangeReturnsNull() {
        assertNull(itemChanges(item, ItemFields(serviceName = "GitHub", username = "octocat", password = "pw", url = "https://github.com", notes = "")))
    }

    @Test
    fun returnsOnlyChangedFields() {
        val changes = itemChanges(item, ItemFields(serviceName = "GitHub", username = "octocat", password = "pw-2", url = "https://github.com"))

        assertEquals(ItemChanges(password = "pw-2"), changes)
    }

    @Test
    fun clearedOptionalFieldBecomesRemove_andNewOptionalFieldBecomesSet() {
        val changes = itemChanges(item, ItemFields(serviceName = "GitHub", username = "octocat", password = "pw", url = "", notes = "memo"))

        assertEquals(ItemChanges(url = OptionalChange.Remove, notes = OptionalChange.Set("memo")), changes)
    }

    @Test
    fun appliedChangesNeverLeaveEmptyStrings() {
        val changes = itemChanges(item, ItemFields(serviceName = "GitHub", username = "octocat", password = "pw", url = ""))
        val updated = updateItem(VaultData.of(item), "a", checkNotNull(changes), NOW).items[0]

        assertTrue("url" !in updated)
    }
}
