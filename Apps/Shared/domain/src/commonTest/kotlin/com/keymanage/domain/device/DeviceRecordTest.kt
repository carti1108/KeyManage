package com.keymanage.domain.device

import com.keymanage.domain.auth.UnlockKdf
import com.keymanage.domain.crypto.EncryptedVaultPayload
import com.keymanage.domain.vault.ItemChanges
import com.keymanage.domain.vault.ItemFields
import com.keymanage.domain.vault.addItem
import com.keymanage.domain.vault.deleteItem
import com.keymanage.domain.vault.emptyVault
import com.keymanage.domain.vault.mergeVaults
import com.keymanage.domain.vault.updateItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

// 원본: Apps/Web/src/domain/device/deviceRecord.test.ts, reconcileDeviceRecord.test.ts (ADR-0019, ADR-WEB-001)
private val KDF = UnlockKdf("AAAAAAAAAAAAAAAAAAAAAA==", 600_000)
private const val EMAIL = "user@example.com"

private val sample = DeviceRecord(
    email = EMAIL,
    kdf = KDF,
    payload = EncryptedVaultPayload(1, "AES-256-GCM", "aXY=", "Y3Q=", "dGFn"),
    revision = 3,
    lastSeenRevision = 4,
    pendingSync = true,
)

class ParseDeviceRecordTest {
    private val json: JsonObject = Json.parseToJsonElement(encodeDeviceRecord(sample)).jsonObject

    private fun with(field: String, value: JsonElement) = JsonObject(json + (field to value)).toString()
    private fun withKdf(value: String) = with("kdf", Json.parseToJsonElement(value))

    @Test
    fun returnsValidRecordUnchanged() {
        assertEquals(sample, parseDeviceRecord(encodeDeviceRecord(sample)))
    }

    @Test
    fun encodesInWebKeyOrder() {
        assertEquals(
            """{"version":1,"email":"user@example.com","kdf":{"salt":"AAAAAAAAAAAAAAAAAAAAAA==","iterations":600000},""" +
                """"payload":{"version":1,"cipher":"AES-256-GCM","iv":"aXY=","ciphertext":"Y3Q=","tag":"dGFn"},""" +
                """"revision":3,"lastSeenRevision":4,"pendingSync":true}""",
            encodeDeviceRecord(sample),
        )
    }

    @Test
    fun missingRecordIsNull() {
        assertNull(parseDeviceRecord(null))
        assertNull(parseDeviceRecord("null"))
    }

    @Test
    fun otherFormatVersionIsNotUsed() {
        assertNull(parseDeviceRecord(with("version", JsonPrimitive(2))))
    }

    @Test
    fun missingOrWrongTypedFieldsAreNotUsed() {
        val broken = listOf(
            "\"text\"",
            "not json",
            with("email", JsonPrimitive(42)),
            withKdf("""{"salt":"AAAA"}"""),
            withKdf("""{"salt":"AAAA","iterations":1.5}"""),
            withKdf("""{"salt":"AAAAAAAAAAAAAAAAAAAAAA==","iterations":5}"""),
            withKdf("""{"salt":"AAAAAAAAAAAAAAAAAAAAAA==","iterations":20000000}"""),
            withKdf("""{"salt":"AAAA","iterations":600000}"""),
            withKdf("""{"salt":"!!not base64!!","iterations":600000}"""),
            with("payload", Json.parseToJsonElement("""{"version":1,"cipher":"AES-256-GCM","iv":7,"ciphertext":"Y3Q=","tag":"dGFn"}""")),
            with("payload", JsonNull),
            with("revision", JsonPrimitive(-1)),
            with("lastSeenRevision", JsonPrimitive("4")),
            with("pendingSync", JsonPrimitive("yes")),
        )
        broken.forEach { assertNull(parseDeviceRecord(it), it) }
    }
}

private fun record(
    email: String = EMAIL,
    kdf: UnlockKdf = KDF,
    payload: EncryptedVaultPayload = EncryptedVaultPayload(1, "AES-256-GCM", "iv", "c1", "t"),
    revision: Int = 2,
    lastSeenRevision: Int = 2,
    pendingSync: Boolean = false,
) = DeviceRecord(email, kdf, payload, revision, lastSeenRevision, pendingSync)

private val otherPayload = EncryptedVaultPayload(1, "AES-256-GCM", "iv2", "c2", "t2")

class ReconcileDeviceRecordTest {
    private fun reconcile(current: DeviceRecord?, lastSeen: DeviceRecord?, unlocked: Boolean = true) =
        reconcileDeviceRecord(current, lastSeen, EMAIL, KDF, unlocked)

    @Test
    fun noRecordAndNeverSeenWrites() {
        assertEquals(Reconciliation.Write, reconcile(null, null))
    }

    @Test
    fun seenRecordDisappearedMeansLoggedOutElsewhere() {
        assertEquals(Reconciliation.LoggedOutElsewhere, reconcile(null, record()))
    }

    @Test
    fun otherAccountsRecordMeansLoggedOutElsewhere() {
        assertEquals(Reconciliation.LoggedOutElsewhere, reconcile(record(email = "other@example.com"), record()))
    }

    @Test
    fun sameEmailDifferentKdfMeansPasswordChanged() {
        assertEquals(Reconciliation.PasswordChanged, reconcile(record(kdf = KDF.copy(iterations = 700_000)), record()))
    }

    @Test
    fun sameAsLastSeenWrites() {
        assertEquals(Reconciliation.Write, reconcile(record(), record(), unlocked = false))
    }

    @Test
    fun otherTabWroteWhileUnlockedMerges() {
        val current = record(payload = otherPayload, pendingSync = true)
        assertEquals(Reconciliation.Merge(current), reconcile(current, record()))
    }

    @Test
    fun otherTabWroteWhileLockedSkips() {
        assertEquals(Reconciliation.Skip, reconcile(record(payload = otherPayload), record(), unlocked = false))
    }
}

class MergeSyncFieldsTest {
    @Test
    fun pendingIfEitherIsPending() {
        assertEquals(true, mergeSyncFields(SyncFields(2, 2, false), record(pendingSync = true)).pendingSync)
        assertEquals(true, mergeSyncFields(SyncFields(2, 2, true), record(pendingSync = false)).pendingSync)
        assertEquals(false, mergeSyncFields(SyncFields(2, 2, false), record()).pendingSync)
    }

    @Test
    fun revisionTakesLarger() {
        assertEquals(5, mergeSyncFields(SyncFields(2, 2, false), record(revision = 5)).revision)
        assertEquals(7, mergeSyncFields(SyncFields(7, 2, false), record(revision = 5)).revision)
    }

    @Test
    fun rollbackBaselineNeverLowers() {
        assertEquals(9, mergeSyncFields(SyncFields(2, 9, false), record(lastSeenRevision = 3)).lastSeenRevision)
        assertEquals(9, mergeSyncFields(SyncFields(2, 3, false), record(lastSeenRevision = 9)).lastSeenRevision)
    }

    @Test
    fun myLaterDeleteBeatsOtherTabsEarlierEdit() {
        val t0 = Instant.parse("2026-10-31T00:00:00Z")
        val base = addItem(emptyVault(t0), ItemFields("GitHub", "octocat", "pw"), t0, id = "a")
        val theirs = updateItem(base, "a", ItemChanges(password = "pw-2"), Instant.parse("2026-10-31T00:01:00Z"))
        val mine = deleteItem(base, "a", Instant.parse("2026-10-31T00:02:00Z"))

        assertEquals("2026-10-31T00:02:00Z", mergeVaults(mine, theirs).items.first { it.id == "a" }.deletedAt)
    }
}

class RememberedKdfTest {
    @Test
    fun keepsOnlyNonSecretValues() {
        assertEquals(RememberedKdf(EMAIL, KDF.salt, KDF.iterations), rememberedKdf(EMAIL, KDF))
    }
}
