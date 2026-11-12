package com.keymanage.domain.vault

import com.keymanage.domain.crypto.EncryptedVaultPayload
import com.keymanage.domain.crypto.EncryptionKey
import com.keymanage.domain.crypto.Vectors
import com.keymanage.domain.crypto.openVault
import com.keymanage.domain.crypto.sealVault
import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.server.UnexpectedResponseError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// 원본: Apps/Web/src/domain/vault/vaultSync.test.ts (VLT-001, api-spec 2.3·2.4)
private val NOW = Instant.parse("2026-10-29T00:10:00Z")
private val fields = ItemFields(serviceName = "S", username = "u", password = "p")
private val clock = object : Clock {
    override fun now(): Instant = NOW
}

/** 서버처럼 revision을 관리하고, 기대 revision이 다르면 409를 낸다. 다른 기기의 저장도 흉내 낸다 */
private class FakeVaultApi(private val key: EncryptionKey) : VaultApi {
    var revision = 0
    var payload: EncryptedVaultPayload? = null
    var saves = 0
    var otherDeviceWritesBeforeEachSave = 0
    var getOverride: (suspend () -> RemoteVault?)? = null
    var saveOverride: (suspend (Int, EncryptedVaultPayload) -> Int)? = null

    override suspend fun get(): RemoteVault? = getOverride?.invoke() ?: payload?.let { RemoteVault(revision, it) }

    override suspend fun save(expectedRevision: Int, payload: EncryptedVaultPayload): Int {
        saveOverride?.let { return it(expectedRevision, payload) }
        saves += 1
        if (otherDeviceWritesBeforeEachSave > 0) {
            otherDeviceWritesBeforeEachSave -= 1
            storeAsOtherDevice(addItem(current(), fields, NOW, id = "other-$revision"))
        }
        if (expectedRevision != revision) throw ServerError(409, ServerErrorCode.VAULT_CONFLICT)
        this.payload = payload
        revision += 1
        return revision
    }

    suspend fun storeAsOtherDevice(data: VaultData) {
        payload = sealVault(key, encodeVaultData(data))
        revision += 1
    }

    suspend fun current(): VaultData = payload?.let { parseVaultData(openVault(key, it)) } ?: emptyVault(NOW)
}

private val readOnlyVault = VaultData(
    JsonObject(mapOf("version" to JsonPrimitive(2), "updatedAt" to JsonPrimitive("2026-10-29T00:00:00Z"), "items" to JsonArray(emptyList()))),
)

@OptIn(ExperimentalCoroutinesApi::class)
private fun syncTest(body: suspend TestScope.(EncryptionKey) -> Unit) =
    runTest(UnconfinedTestDispatcher(), timeout = 5.minutes) { body(Vectors.keysOf("ascii-passphrase").encryptionKey) }

private fun syncWith(api: VaultApi, key: EncryptionKey, memory: RevisionMemory = RevisionMemory()) = VaultSync(api, key, clock, memory)

class VaultSyncLoadTest {
    @Test
    fun noServerVaultStartsEmptyAtRevisionZero() = syncTest { key ->
        val loaded = syncWith(FakeVaultApi(key), key).load()

        assertEquals(0, loaded.revision)
        assertEquals(emptyList(), loaded.data.items)
        assertNull(loaded.payload)
    }

    @Test
    fun decryptsServerVaultWithRevision() = syncTest { key ->
        val api = FakeVaultApi(key)
        api.storeAsOtherDevice(addItem(emptyVault(NOW), fields, NOW, id = "a"))

        val loaded = syncWith(api, key).load()

        assertEquals(1, loaded.revision)
        assertEquals(listOf("a"), loaded.data.items.map { it.id })
        // 잠금 해제 확인용으로 암호화 볼트도 돌려준다 (SEC-003)
        assertEquals(api.payload, loaded.payload)
    }
}

class VaultSyncSaveTest {
    @Test
    fun savesOnlyCiphertextAndReturnsNewRevision() = syncTest { key ->
        val api = FakeVaultApi(key)
        val sync = syncWith(api, key)
        val loaded = sync.load()

        val saved = sync.save(loaded, addItem(loaded.data, fields, NOW, id = "a"))

        assertEquals(1, saved.revision)
        assertEquals(api.payload, saved.payload)
        assertFalse(api.payload!!.toJson().toString().contains("\"password\""))
        assertEquals(listOf("a"), api.current().items.map { it.id })
    }

    @Test
    fun conflictMergesWithLatestAndRetries() = syncTest { key ->
        val api = FakeVaultApi(key)
        val sync = syncWith(api, key)
        val loaded = sync.load()
        api.otherDeviceWritesBeforeEachSave = 1

        val saved = sync.save(loaded, addItem(loaded.data, fields, NOW, id = "mine"))

        assertEquals(2, api.saves)
        assertEquals(api.revision, saved.revision)
        assertEquals(listOf("mine", "other-0"), api.current().items.map { it.id }.sorted())
        assertEquals(listOf("mine", "other-0"), saved.data.items.map { it.id }.sorted())
    }

    @Test
    fun threeRetriesSucceed_fourthConflictFails() = syncTest { key ->
        val succeeds = FakeVaultApi(key).apply { otherDeviceWritesBeforeEachSave = 3 }
        val sync = syncWith(succeeds, key)
        sync.save(sync.load(), emptyVault(NOW))
        assertEquals(4, succeeds.saves)

        val fails = FakeVaultApi(key).apply { otherDeviceWritesBeforeEachSave = 4 }
        val failingSync = syncWith(fails, key)
        val loaded = failingSync.load()
        assertFailsWith<VaultSyncConflictException> { failingSync.save(loaded, emptyVault(NOW)) }
        assertEquals(4, fails.saves)
    }

    @Test
    fun nonConflictServerErrorIsNotRetried() = syncTest { key ->
        val api = FakeVaultApi(key)
        var calls = 0
        api.saveOverride = { _, _ ->
            calls += 1
            throw ServerError(401, ServerErrorCode.UNAUTHORIZED)
        }
        val sync = syncWith(api, key)
        val loaded = sync.load()

        val error = assertFailsWith<ServerError> { sync.save(loaded, emptyVault(NOW)) }
        assertEquals(ServerErrorCode.UNAUTHORIZED, error.code)
        assertEquals(1, calls)
    }

    @Test
    fun invalidVaultIsNeverSent() = syncTest { key ->
        val api = FakeVaultApi(key)
        val sync = syncWith(api, key)
        val loaded = sync.load()
        val brokenItem = JsonObject(
            mapOf(
                "id" to JsonPrimitive("a"), "serviceName" to JsonPrimitive("S"), "username" to JsonPrimitive("u"),
                "createdAt" to JsonPrimitive(NOW.toString()), "updatedAt" to JsonPrimitive(NOW.toString()),
            ),
        )
        val broken = VaultData(JsonObject(loaded.data.json + ("items" to JsonArray(listOf(brokenItem)))))

        assertFailsWith<InvalidVaultDataException> { sync.save(loaded, broken) }
        assertEquals(0, api.saves)
    }

    @Test
    fun saveResponseRevisionMustBeExactlyOneMore() = syncTest { key ->
        val api = FakeVaultApi(key).apply { saveOverride = { _, _ -> 5 } }
        val sync = syncWith(api, key)
        val loaded = sync.load()

        assertFailsWith<UnexpectedResponseError> { sync.save(loaded, emptyVault(NOW)) }
    }

    @Test
    fun readOnlyVaultFetchedAfterConflictIsNotSaved() = syncTest { key ->
        val api = FakeVaultApi(key)
        val sync = syncWith(api, key)
        val loaded = sync.load()
        api.storeAsOtherDevice(readOnlyVault)

        assertFailsWith<ReadOnlyVaultException> { sync.save(loaded, emptyVault(NOW)) }
        assertEquals(1, api.saves)
    }

    @Test
    fun higherSchemaVersionIsNotSaved() = syncTest { key ->
        val api = FakeVaultApi(key)
        api.storeAsOtherDevice(readOnlyVault)
        val sync = syncWith(api, key)
        val loaded = sync.load()

        assertTrue(loaded.readOnly)
        assertFailsWith<ReadOnlyVaultException> { sync.save(loaded, loaded.data) }
        assertEquals(0, api.saves)
    }
}

class RevisionRollbackTest {
    @Test
    fun lowerServerRevisionStops_untilUserAccepts() = syncTest { key ->
        val api = FakeVaultApi(key)
        api.storeAsOtherDevice(emptyVault(NOW))
        api.storeAsOtherDevice(emptyVault(NOW))
        val sync = syncWith(api, key)
        sync.load()
        // 서버를 백업(revision 1)에서 되돌린 상황
        api.revision = 1

        val error = assertFailsWith<RevisionRollbackException> { sync.load() }
        assertEquals(2, error.lastSeenRevision)
        assertEquals(1, error.serverRevision)
        assertEquals(1, sync.load(acceptRollback = true).revision)
    }

    @Test
    fun newSyncAfterUnlockStillRemembersSeenRevision() = syncTest { key ->
        val api = FakeVaultApi(key)
        api.storeAsOtherDevice(emptyVault(NOW))
        api.storeAsOtherDevice(emptyVault(NOW))
        val memory = RevisionMemory()
        syncWith(api, key, memory).load()
        api.revision = 1

        assertFailsWith<RevisionRollbackException> { syncWith(api, key, memory).load() }
    }

    @Test
    fun saveFinishingBeforeSlowLoadIsNotMistakenForRollback() = syncTest { key ->
        val api = FakeVaultApi(key)
        api.storeAsOtherDevice(emptyVault(NOW))
        val sync = syncWith(api, key)
        val loaded = sync.load()
        // 불러오기 요청을 보낸 시점의 서버 상태(revision 1)를 늦게 돌려준다
        val stale = api.get()
        val gate = CompletableDeferred<Unit>()
        api.getOverride = {
            gate.await()
            stale
        }

        val slowLoad = async { sync.load() }
        sync.save(loaded, emptyVault(NOW))
        gate.complete(Unit)

        assertEquals(1, slowLoad.await().revision)
    }

    @Test
    fun disappearedServerVaultIsRollback() = syncTest { key ->
        val api = FakeVaultApi(key)
        api.storeAsOtherDevice(emptyVault(NOW))
        val sync = syncWith(api, key)
        sync.load()
        api.payload = null
        api.revision = 0

        assertFailsWith<RevisionRollbackException> { sync.load() }
    }

    @Test
    fun rolledBackVaultAfterConflictStopsWithoutMerging() = syncTest { key ->
        val api = FakeVaultApi(key)
        api.storeAsOtherDevice(emptyVault(NOW))
        api.storeAsOtherDevice(emptyVault(NOW))
        val sync = syncWith(api, key)
        val loaded = sync.load()
        api.revision = 1

        assertFailsWith<RevisionRollbackException> { sync.save(loaded, loaded.data) }
        assertEquals(1, api.saves)
    }
}

class VaultSizeTest {
    private fun payloadOfSize(bytes: Int): EncryptedVaultPayload {
        val empty = EncryptedVaultPayload(1, "AES-256-GCM", "", "", "")
        return empty.copy(ciphertext = "A".repeat(bytes - empty.toJson().toString().length))
    }

    @Test
    fun exactlyLimitIsAllowed_oneMoreIsRejectedBeforeSending() {
        assertWithinLimit(payloadOfSize(MAX_PAYLOAD_BYTES))
        assertFailsWith<VaultTooLargeException> { assertWithinLimit(payloadOfSize(MAX_PAYLOAD_BYTES + 1)) }
    }
}

// 원본 isTransientSyncError. 원본에서는 세션 테스트가 덮는다
class TransientSyncErrorTest {
    @Test
    fun networkUnexpectedResponse5xxAnd429AreTransient() {
        assertTrue(isTransientSyncError(NetworkError()))
        assertTrue(isTransientSyncError(UnexpectedResponseError("가로챈 응답")))
        assertTrue(isTransientSyncError(ServerError(503, ServerErrorCode.INTERNAL_ERROR)))
        assertTrue(isTransientSyncError(ServerError(429, ServerErrorCode.TOO_MANY_ATTEMPTS)))
    }

    @Test
    fun revisionMismatchClientErrorsAndOthersAreNot() {
        assertFalse(isTransientSyncError(RevisionMismatchException(2, 5)))
        assertFalse(isTransientSyncError(ServerError(401, ServerErrorCode.UNAUTHORIZED)))
        assertFalse(isTransientSyncError(ServerError(409, ServerErrorCode.VAULT_CONFLICT)))
        assertFalse(isTransientSyncError(IllegalStateException()))
    }
}
