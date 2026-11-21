package com.keymanage.domain.vault

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

// 원본: Apps/Web/src/domain/vault/vaultData.test.ts '항목 병합 (VLT-001)'
private const val TS = "2026-10-29T00:00:00Z"
private val T0 = Instant.parse(TS)
private val T1 = Instant.parse("2026-10-29T00:01:00Z")

class MergeVaultsTest {
    private fun deleted(id: String, updatedAt: String, deletedAt: String) =
        itemJson("id" to id, "createdAt" to TS, "updatedAt" to updatedAt, "deletedAt" to deletedAt)

    @Test
    fun itemsOnOnlyOneSideAreAllKept() {
        val merged = mergeVaults(vaultWith(fullItem("local")), vaultWith(fullItem("remote")))

        assertEquals(listOf("local", "remote"), merged.items.map { it.id }.sorted())
    }

    @Test
    fun laterEditWins() {
        val local = vaultWith(fullItem("a", "2026-10-29T00:02:00Z", "password" to "local"))
        val remote = vaultWith(fullItem("a", "2026-10-29T00:01:00Z", "password" to "remote"))

        assertEquals("local", mergeVaults(local, remote).items[0].password)
        assertEquals("local", mergeVaults(remote, local).items[0].password)
    }

    @Test
    fun laterDeleteWinsOverEarlierEdit_andLaterEditWinsOverEarlierDelete() {
        val edited = vaultWith(fullItem("a", "2026-10-29T00:01:00Z"))
        val deletedLater = vaultWith(deleted("a", "2026-10-29T00:02:00Z", "2026-10-29T00:02:00Z"))
        val deletedEarlier = vaultWith(deleted("a", "2026-10-29T00:00:30Z", "2026-10-29T00:00:30Z"))

        assertNotNull(mergeVaults(edited, deletedLater).items[0].deletedAt)
        assertNotNull(mergeVaults(deletedLater, edited).items[0].deletedAt)
        assertNull(mergeVaults(edited, deletedEarlier).items[0].deletedAt)
    }

    @Test
    fun deletionComparesByDeletedAtNotUpdatedAt() {
        val edited = vaultWith(fullItem("a", "2026-10-29T00:01:00Z"))
        val deletedItem = vaultWith(deleted("a", "2026-10-29T00:00:00Z", "2026-10-29T00:02:00Z"))

        assertEquals("2026-10-29T00:02:00Z", mergeVaults(edited, deletedItem).items[0].deletedAt)
        assertEquals("2026-10-29T00:02:00Z", mergeVaults(deletedItem, edited).items[0].deletedAt)
    }

    @Test
    fun unknownItemFieldsComeFromTheChosenSide() {
        val local = vaultWith(fullItem("a", "2026-10-29T00:02:00Z", "totp" to "LOCAL"))
        val remote = vaultWith(fullItem("a", "2026-10-29T00:01:00Z", "totp" to "REMOTE"))

        assertEquals("LOCAL", mergeVaults(local, remote).items[0]["totp"]?.jsonPrimitive?.content)
    }

    @Test
    fun reEditInTheSameSecondStillWins() {
        val fields = ItemFields(serviceName = "GitHub", username = "octocat", password = "pw-1")
        val saved = addItem(emptyVault(T0), fields, T1, id = "a")
        val editedAgain = updateItem(saved, "a", ItemChanges(password = "pw-2"), T1)

        assertEquals("pw-2", mergeVaults(editedAgain, saved).items[0].password)
    }

    @Test
    fun sameChangeTimePicksServerSide() {
        val local = vaultWith(fullItem("a", "2026-10-29T00:01:00Z", "password" to "local"))
        val remote = vaultWith(fullItem("a", "2026-10-29T00:01:00Z", "password" to "remote"))

        assertEquals("remote", mergeVaults(local, remote).items[0].password)
    }

    @Test
    fun vaultTimeIsTheLaterOne_andUnknownTopLevelFieldsFollowServer() {
        val local = parseVaultData(vaultJson(updatedAt = "2026-10-29T00:05:00Z", extra = mapOf("extra" to JsonPrimitive("local"))))
        val remote = parseVaultData(vaultJson(updatedAt = "2026-10-29T00:01:00Z", extra = mapOf("extra" to JsonPrimitive("remote"))))

        val merged = mergeVaults(local, remote)

        assertEquals("2026-10-29T00:05:00Z", merged.updatedAt)
        assertEquals("remote", merged["extra"]?.jsonPrimitive?.content)
    }
}
