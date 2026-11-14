package com.keymanage.domain.vault

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

// 원본: Apps/Web/src/domain/vault/vaultData.test.ts (vault-spec 4장, VLT-001)
private val T0 = Instant.parse("2026-10-29T00:00:00Z")
private val T1 = Instant.parse("2026-10-29T00:01:00Z")
private val T2 = Instant.parse("2026-10-29T00:02:00Z")
private const val TS = "2026-10-29T00:00:00Z"

private val fields = ItemFields(serviceName = "GitHub", username = "octocat", password = "pw-1", url = "https://github.com", notes = "메모")

internal fun itemJson(vararg entries: Pair<String, Any?>): JsonObject = buildJsonObject {
    entries.forEach { (key, value) ->
        put(
            key,
            when (value) {
                null -> JsonNull
                is JsonElement -> value
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                else -> error("unsupported $value")
            },
        )
    }
}

internal fun fullItem(id: String, updatedAt: String = TS, vararg extra: Pair<String, Any?>): JsonObject = itemJson(
    "id" to id, "serviceName" to "GitHub", "username" to "octocat", "password" to "pw-1",
    "url" to "https://github.com", "notes" to "메모", "createdAt" to TS, "updatedAt" to updatedAt, *extra,
)

internal fun vaultJson(vararg items: JsonObject, updatedAt: String = TS, extra: Map<String, JsonElement> = emptyMap()): String =
    buildJsonObject {
        put("version", 1)
        put("updatedAt", updatedAt)
        extra.forEach { (key, value) -> put(key, value) }
        put("items", buildJsonArray { items.forEach { add(it) } })
    }.toString()

internal fun vaultWith(vararg items: JsonObject): VaultData = parseVaultData(vaultJson(*items))

class VaultDataParseTest {
    @Test
    fun readsValidVault_andKeepsUnknownFields() {
        val json = vaultJson(
            itemJson("id" to "a", "serviceName" to "S", "username" to "u", "password" to "p", "createdAt" to TS, "updatedAt" to TS, "totp" to "SECRET"),
            extra = mapOf("futureTop" to buildJsonObject { put("a", 1) }),
        )

        val data = parseVaultData(json)

        assertEquals("""{"a":1}""", data["futureTop"].toString())
        assertEquals("SECRET", data.items[0]["totp"]?.jsonPrimitive?.content)
        assertEquals(Json.parseToJsonElement(json), Json.parseToJsonElement(encodeVaultData(data)))
    }

    @Test
    fun readsDeletedItemWithOnlyIdAndTimes() {
        val json = vaultJson(itemJson("id" to "a", "createdAt" to TS, "updatedAt" to TS, "deletedAt" to TS))

        assertEquals(1, parseVaultData(json).items.size)
    }

    @Test
    fun rejectsMissingOrWrongTypedFieldsOrNonJson() {
        val bad = listOf(
            "not json",
            """{"updatedAt":"$TS","items":[]}""",
            """{"version":0,"updatedAt":"$TS","items":[]}""",
            """{"version":1.5,"updatedAt":"$TS","items":[]}""",
            """{"version":"1","updatedAt":"$TS","items":[]}""",
            """{"version":1,"updatedAt":"$TS","items":{}}""",
            """[]""",
            vaultJson(itemJson("id" to "a", "username" to "u", "password" to "p", "createdAt" to TS, "updatedAt" to TS)),
            vaultJson(itemJson("id" to "a", "serviceName" to "S", "username" to "u", "password" to 1, "createdAt" to TS, "updatedAt" to TS)),
            vaultJson(itemJson("id" to "a", "serviceName" to "S", "username" to "u", "password" to "p", "url" to 3, "createdAt" to TS, "updatedAt" to TS)),
            vaultJson(itemJson("id" to "a", "createdAt" to TS, "updatedAt" to TS, "deletedAt" to 5)),
            vaultJson(itemJson("id" to 7, "createdAt" to TS, "updatedAt" to TS, "deletedAt" to TS)),
        )
        bad.forEach { json -> assertFailsWith<InvalidVaultDataException>(json) { parseVaultData(json) } }
    }

    @Test
    fun rejectsTimesWithoutUtcDesignator() {
        listOf("2026-10-29T00:10:00", "", "yesterday").forEach { time ->
            val json = vaultJson(fullItem("a", updatedAt = time))
            assertFailsWith<InvalidVaultDataException>(time) { parseVaultData(json) }
        }
        assertFailsWith<InvalidVaultDataException> { parseVaultData("""{"version":1,"updatedAt":"now","items":[]}""") }
        assertEquals(1, parseVaultData(vaultJson(fullItem("a", updatedAt = "2026-10-29T09:00:00.123+09:00"))).items.size)
    }

    @Test
    fun rejectsSpaceSeparatedDateAndTime() {
        // 옛 Swift VaultModuleTests.testStrictIso8601UtcTimeFormatValidation에서 옮겼다
        val json = vaultJson(fullItem("a", updatedAt = "2026-10-27 11:20:00Z"))

        assertFailsWith<InvalidVaultDataException> { parseVaultData(json) }
    }

    @Test
    fun treatsNullOptionalFieldsFromOtherPlatformsAsMissing() {
        val json = vaultJson(fullItem("a", TS, "url" to null, "notes" to null, "deletedAt" to null))

        val item = parseVaultData(json).items[0]

        assertFalse("deletedAt" in item)
        assertFalse("url" in item)
        assertNull(item.url)
        assertEquals(1, activeItems(parseVaultData(json)).size)
    }

    @Test
    fun rejectsDuplicateIds() {
        val item = itemJson("id" to "a", "serviceName" to "S", "username" to "u", "password" to "p", "createdAt" to TS, "updatedAt" to TS)

        assertFailsWith<InvalidVaultDataException> { parseVaultData(vaultJson(item, item)) }
    }

    @Test
    fun higherSchemaVersionIsReadOnly() {
        assertTrue(isReadOnly(parseVaultData("""{"version":2,"updatedAt":"$TS","items":[]}""")))
        assertFalse(isReadOnly(emptyVault(T0)))
    }
}

class VaultDataEditTest {
    @Test
    fun addedItemHasEqualCreatedAndUpdatedTimes_andVaultTimeMoves() {
        val data = addItem(emptyVault(T0), fields, T1, id = "id-1")

        assertEquals(
            """{"id":"id-1","serviceName":"GitHub","username":"octocat","password":"pw-1","url":"https://github.com","notes":"메모","createdAt":"2026-10-29T00:01:00Z","updatedAt":"2026-10-29T00:01:00Z"}""",
            data.items[0].json.toString(),
        )
        assertEquals("2026-10-29T00:01:00Z", data.updatedAt)
    }

    @Test
    fun addItemWithoutId_generatesUuid() {
        val id = addItem(emptyVault(T0), fields, T1).items[0].id
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(id), id)
    }

    @Test
    fun updateChangesOnlyGivenFieldsAndTime_andKeepsUnknownFields() {
        val before = vaultWith(fullItem("a", TS, "totp" to "SECRET"))

        val after = updateItem(before, "a", ItemChanges(password = "pw-2"), T2)

        assertEquals(
            fullItem("a", "2026-10-29T00:02:00Z", "totp" to "SECRET").let { expected ->
                JsonObject(expected + ("password" to JsonPrimitive("pw-2")))
            },
            after.items[0].json,
        )
        // 원본은 바뀌지 않는다
        assertEquals("pw-1", before.items[0].password)
    }

    @Test
    fun deletedItemKeepsNoSecretsOrUnknownFields_andLeavesList() {
        val before = vaultWith(fullItem("a", TS, "totp" to "SECRET"))

        val after = deleteItem(before, "a", T2)

        assertEquals(
            """[{"id":"a","createdAt":"$TS","updatedAt":"2026-10-29T00:02:00Z","deletedAt":"2026-10-29T00:02:00Z"}]""",
            after.items.joinToString(",", "[", "]") { it.json.toString() },
        )
        assertEquals(emptyList(), activeItems(after))
    }

    @Test
    fun itemChangeTimeAlwaysIncreases() {
        val future = "2026-10-29T05:00:00Z"
        val data = vaultWith(fullItem("a", future))

        val edited = updateItem(data, "a", ItemChanges(password = "pw-2"), T1)
        val deleted = deleteItem(edited, "a", T1)

        assertEquals("2026-10-29T05:00:01Z", edited.items[0].updatedAt)
        assertEquals("2026-10-29T05:00:02Z", deleted.items[0].deletedAt)
    }

    @Test
    fun unspecifiedChangesKeepFields() {
        val data = vaultWith(fullItem("a"))

        val item = updateItem(data, "a", ItemChanges(notes = OptionalChange.Set("n")), T1).items[0]

        assertEquals("pw-1", item.password)
        assertEquals("n", item.notes)
    }

    @Test
    fun removingOptionalFieldsDropsThemInsteadOfLeavingEmptyStrings() {
        val data = vaultWith(fullItem("a", TS, "totp" to "KEEP"))

        val item = updateItem(data, "a", ItemChanges(url = OptionalChange.Remove, notes = OptionalChange.Remove), T1).items[0]

        assertFalse("url" in item)
        assertFalse("notes" in item)
        assertEquals("GitHub", item.serviceName)
        assertEquals("KEEP", item["totp"]?.jsonPrimitive?.content)
    }

    @Test
    fun doesNotAddDuplicateId() {
        val data = addItem(emptyVault(T0), fields, T0, id = "a")

        assertFailsWith<DuplicateItemIdException> { addItem(data, fields, T1, id = "a") }
    }

    @Test
    fun doesNotUpdateMissingOrDeletedItem() {
        val data = deleteItem(addItem(emptyVault(T0), fields, T0, id = "a"), "a", T1)

        assertFailsWith<VaultItemNotFoundException> { updateItem(data, "a", ItemChanges(password = "x"), T2) }
        assertFailsWith<VaultItemNotFoundException> { updateItem(data, "missing", ItemChanges(password = "x"), T2) }
    }

    @Test
    fun formatTimeDropsSubSecondPart() {
        assertEquals("2026-10-29T00:00:01Z", formatTime(Instant.parse("2026-10-29T00:00:01.999Z")))
    }
}
