package com.keymanage.domain.backup

import com.keymanage.domain.vault.InvalidVaultDataException
import com.keymanage.domain.vault.ItemChanges
import com.keymanage.domain.vault.ItemFields
import com.keymanage.domain.vault.OptionalChange
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.VaultItem
import com.keymanage.domain.vault.addItem
import com.keymanage.domain.vault.deleteItem
import com.keymanage.domain.vault.emptyVault
import com.keymanage.domain.vault.formatTime
import com.keymanage.domain.vault.mergeVaults
import com.keymanage.domain.vault.updateItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Instant

// 원본: Apps/Web/src/domain/backup/backupSession.test.ts '복원 규칙 (VLT-004 규칙 6·7)'
// restoreBackupInto(세션 상태를 쓰는 부분)는 세션과 함께 5단계에서 옮긴다
private val RESTORED_AT = Instant.parse("2026-11-01T10:00:00Z")
private val created = Instant.parse("2026-10-01T00:00:00Z")
private val afterBackup = Instant.parse("2026-10-20T00:00:00Z")
private val fields = ItemFields("GitHub", "octocat", "pw-1")
private val backupData = addItem(addItem(addItem(emptyVault(created), fields, created, id = "a"), fields, created, id = "b"), fields, created, id = "gone")
private val backup = deleteItem(backupData, "gone", created)

private fun itemOf(data: VaultData, id: String): VaultItem = data.items.first { it.id == id }

private fun VaultData.withItems(items: List<JsonObject>, vararg top: Pair<String, String>) =
    VaultData(JsonObject(json + ("items" to JsonArray(items)) + top.map { (k, v) -> k to JsonPrimitive(v) }))

private fun VaultItem.with(vararg fields: Pair<String, String>) = JsonObject(json + fields.map { (k, v) -> k to JsonPrimitive(v) })

class RestoreItemsTest {
    @Test
    fun editedRestoredToBackup_deletedRevived_timeIsRestoreTime_noDeleteMark() {
        val current = deleteItem(updateItem(backup, "a", ItemChanges(username = "edited", url = OptionalChange.Set("https://x")), afterBackup), "b", afterBackup)

        val (data, restored) = restoreItems(current, backup, RESTORED_AT)

        assertEquals(2, restored)
        assertEquals(JsonObject(itemOf(backup, "a").json + ("updatedAt" to JsonPrimitive(formatTime(RESTORED_AT)))), itemOf(data, "a").json)
        assertEquals(JsonObject(itemOf(backup, "b").json + ("updatedAt" to JsonPrimitive(formatTime(RESTORED_AT)))), itemOf(data, "b").json)
    }

    @Test
    fun deletedInBackupOrMissingFromBackupStayAsIs() {
        val revived = backup.withItems(backup.items.map { if (it.id == "gone") itemOf(backupData, "gone").with("username" to "alive") else it.json })
        val current = addItem(revived, fields, afterBackup, id = "new")

        val (data, restored) = restoreItems(current, backup, RESTORED_AT)

        assertEquals(0, restored)
        assertSame(current, data)
    }

    @Test
    fun backupOnlyItemsAreAdded() {
        val current = addItem(emptyVault(created), fields, created, id = "mine")

        val (data, restored) = restoreItems(current, backup, RESTORED_AT)

        assertEquals(2, restored)
        assertEquals(listOf("mine", "a", "b"), data.items.map { it.id })
    }

    @Test
    fun sameVisibleContentIsLeftAlone_evenWithDifferentTimesAndFieldOrder() {
        val reordered = backup.withItems(
            backup.items.map { item ->
                JsonObject(LinkedHashMap(item.json.entries.reversed().associate { it.key to it.value }) + ("updatedAt" to JsonPrimitive(formatTime(afterBackup))))
            },
        )

        val (data, restored) = restoreItems(reordered, backup, RESTORED_AT)

        assertEquals(0, restored)
        assertSame(reordered, data)
    }

    @Test
    fun unknownItemFieldsFromBackup_topLevelFieldsFromCurrent() {
        val backupWithField = backup.withItems(backup.items.map { if (it.id == "a") it.with("future" to "backup") else it.json }, "note" to "backup-top")
        val current = backup.withItems(backup.items.map { if (it.id == "a") it.with("future" to "current") else it.json }, "note" to "current-top")

        val (data, restored) = restoreItems(current, backupWithField, RESTORED_AT)

        assertEquals(1, restored)
        assertEquals("backup", itemOf(data, "a")["future"]?.jsonPrimitive?.content)
        assertEquals("current-top", data["note"]?.jsonPrimitive?.content)
        assertEquals(current.version, data.version)
        assertEquals(formatTime(RESTORED_AT), data.updatedAt)
    }

    @Test
    fun duplicateIdsInBackupAreRejectedBeforeChangingCurrent() {
        val duplicated = backup.withItems(backup.items.map { it.json } + itemOf(backup, "a").with("username" to "second"))

        assertFailsWith<InvalidVaultDataException> { restoreItems(backup, duplicated, RESTORED_AT) }
    }

    @Test
    fun restoreInSameSecondAsCurrentMovesOneSecondLater() {
        val current = updateItem(backup, "a", ItemChanges(username = "edited"), Instant.parse("2026-11-01T10:00:00Z"))
        assertEquals("2026-11-01T10:00:00Z", itemOf(current, "a").updatedAt)

        val (data) = restoreItems(current, backup, Instant.parse("2026-11-01T10:00:00.700Z"))

        assertEquals("2026-11-01T10:00:01Z", itemOf(data, "a").updatedAt)
    }

    @Test
    fun currentLaterThanRestoreTimeMovesOneSecondAfterIt() {
        val current = updateItem(backup, "a", ItemChanges(username = "edited"), Instant.parse("2026-12-01T00:00:00Z"))

        val (data) = restoreItems(current, backup, RESTORED_AT)

        assertEquals("2026-12-01T00:00:01Z", itemOf(data, "a").updatedAt)
    }

    @Test
    fun restoredContentSurvivesSyncMergeWithOtherDevicesEarlierEdit() {
        val otherDevice = updateItem(backup, "a", ItemChanges(username = "other-device"), afterBackup)
        val current = deleteItem(backup, "a", afterBackup)
        val (data) = restoreItems(current, backup, RESTORED_AT)

        listOf(mergeVaults(data, otherDevice), mergeVaults(otherDevice, data)).forEach { merged ->
            assertEquals("octocat", itemOf(merged, "a").username)
            assertEquals(formatTime(RESTORED_AT), itemOf(merged, "a").updatedAt)
            assertNull(itemOf(merged, "a").deletedAt)
        }
    }
}
