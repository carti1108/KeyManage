package com.keymanage.domain.device

import com.keymanage.domain.auth.UnlockKdf
import com.keymanage.domain.crypto.EncryptedVaultPayload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 원본: Apps/Web/src/domain/device/devicePersistence.ts (ADR-0019, VLT-003)
// 원본에는 전용 테스트가 없어(세션 테스트가 덮는다) 주석에 적힌 동작에서 만들었다
private class FakeStore : DeviceRecordStore {
    val writes = mutableListOf<String>()
    var failing = false
    var hold: CompletableDeferred<Unit>? = null

    override suspend fun load(): String? = writes.lastOrNull()?.takeIf { it != "cleared" }

    override suspend fun save(recordJson: String) {
        hold?.await()
        if (failing) throw IllegalStateException("저장 공간 부족")
        writes += recordJson
    }

    override suspend fun clear() {
        if (failing) throw IllegalStateException("저장 공간 부족")
        writes += "cleared"
    }
}

private fun record(revision: Int) = DeviceRecord(
    "user@example.com",
    UnlockKdf("AAAAAAAAAAAAAAAAAAAAAA==", 600_000),
    EncryptedVaultPayload(1, "AES-256-GCM", "aXY=", "Y3Q=", "dGFn"),
    revision,
    revision,
    false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DevicePersistenceTest {
    @Test
    fun writesInRequestOrderOneAtATime() = runTest(UnconfinedTestDispatcher()) {
        val store = FakeStore()
        val gate = CompletableDeferred<Unit>().also { store.hold = it }
        val persistence = DevicePersistence(store) {}

        val first = async { persistence.save(record(1)) }
        val second = async { persistence.save(record(2)) }
        assertTrue(persistence.isBehind())
        gate.complete(Unit)

        assertEquals(listOf(true, true), listOf(first.await(), second.await()))
        assertEquals(listOf(encodeDeviceRecord(record(1)), encodeDeviceRecord(record(2))), store.writes)
        assertFalse(persistence.isBehind())
    }

    @Test
    fun failureIsReportedNotThrown_andNextSuccessClearsIt() = runTest(UnconfinedTestDispatcher()) {
        val store = FakeStore().apply { failing = true }
        val errors = mutableListOf<Throwable>()
        val persistence = DevicePersistence(store) { errors += it }

        assertFalse(persistence.save(record(1)))
        assertTrue(persistence.hasFailed())
        assertTrue(persistence.isBehind())
        assertEquals(1, errors.size)

        store.failing = false
        assertTrue(persistence.clear())
        assertFalse(persistence.hasFailed())
        assertFalse(persistence.isBehind())
    }

    @Test
    fun markFailedReportsStaleDeviceWithoutWriting() = runTest(UnconfinedTestDispatcher()) {
        val store = FakeStore()
        val errors = mutableListOf<Throwable>()
        val persistence = DevicePersistence(store) { errors += it }

        persistence.markFailed(IllegalStateException("기기 기록을 읽지 못함"))

        assertTrue(persistence.hasFailed())
        assertTrue(persistence.isBehind())
        assertEquals(emptyList(), store.writes)
        assertEquals(1, errors.size)
    }
}
