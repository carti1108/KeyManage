package com.keymanage.domain.session

import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.backup.LocalDay
import com.keymanage.domain.backup.parseBackup
import com.keymanage.domain.crypto.EncryptionKey
import com.keymanage.domain.crypto.WipedKeyException
import com.keymanage.domain.crypto.Vectors
import com.keymanage.domain.crypto.openVault
import com.keymanage.domain.crypto.sealVault
import com.keymanage.domain.crypto.text
import com.keymanage.domain.vault.RevisionMemory
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.VaultSync
import com.keymanage.domain.vault.activeItems
import com.keymanage.domain.vault.addItem
import com.keymanage.domain.vault.parseVaultData
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// 잠기면 키 바이트를 0으로 지운다. 잠금과 겹친 전송은 원래 키로 끝낸 뒤 지운다 (SEC-003, VLT-002)

private const val NEW_PASSWORD = "a brand new master password"

private fun EncryptionKey.isZeroed(): Boolean = bytes.all { it == 0.toByte() }

/** 세션이 동기화 객체에 넘긴 키를 순서대로 모은다 */
private class KeyRecorder(val vaultApi: FakeVaultApi) {
    val keys = mutableListOf<EncryptionKey>()
    val syncs = mutableListOf<VaultSync>()
    val syncFor: (String, EncryptionKey, RevisionMemory) -> VaultSync = { token, key, memory ->
        keys.add(key)
        vaultSyncFor(vaultApi)(token, key, memory).also { syncs.add(it) }
    }
}

private suspend fun TestScope.signedIn(): Pair<SessionSetup, KeyRecorder> {
    val vaultApi = FakeVaultApi()
    val recorder = KeyRecorder(vaultApi)
    val ctx = setup(vaultApi = vaultApi, syncFor = recorder.syncFor)
    ctx.session.login("user@example.com", PASSWORD)
    return ctx to recorder
}

private fun withItem(ctx: SessionSetup, id: String): VaultData = addItem(ctx.unlockedState().vault.data, fields.copy(serviceName = id), NOW, id)

class VaultSessionKeyWipeTest {
    @Test
    fun lockWithNothingInFlightZeroesTheKeyBytes() = sessionTest {
        val (ctx, recorder) = signedIn()
        val key = recorder.keys.last()
        assertFalse(key.isZeroed())

        ctx.session.lock()

        assertIs<SessionState.Locked>(ctx.session.state())
        assertTrue(recorder.keys.all { it.isZeroed() })
    }

    @Test
    fun lockDuringSuspendedSaveFinishesWithTheRealKeyThenZeroes() = sessionTest {
        val (ctx, recorder) = signedIn()
        val key = recorder.keys.last()
        val original = EncryptionKey(key.bytes.copyOf())
        val release = gateSaves(ctx.vaultApi)

        val saving = begin { ctx.session.save(withItem(ctx, "a")) }
        waitFor { ctx.vaultApi.gate!!.waiting > 0 }
        ctx.session.lock()

        // 잠긴 상태로 바로 바뀌지만, 서버로 출발한 전송이 쓰는 키는 끝날 때까지 남는다
        assertIs<SessionState.Locked>(ctx.session.state())
        assertFalse(key.isZeroed())
        release()
        saving.await()

        val uploaded = ctx.vaultApi.payload ?: error("올린 볼트 없음")
        assertEquals(listOf("a"), activeItems(parseVaultData(openVault(original, uploaded))).map { it.id })
        assertTrue(key.isZeroed())
    }

    @Test
    fun noKeyOperationStartsAfterLock() = sessionTest {
        val (ctx, recorder) = signedIn()
        val data = ctx.unlockedState().vault.data
        ctx.session.lock()

        assertFailsWith<SessionStateException> { ctx.session.save(data) }
        assertFailsWith<WipedKeyException> { recorder.syncs.last().seal(data) }
    }

    @Test
    fun lockingTwiceIsHarmless() = sessionTest {
        val (ctx, recorder) = signedIn()

        ctx.session.lock()
        ctx.session.lock()

        assertEquals(1, ctx.locks())
        assertTrue(recorder.keys.last().isZeroed())
        assertEquals(VaultSession.UnlockResult(offline = false), ctx.session.unlock(PASSWORD))
        assertFalse(recorder.keys.last().isZeroed())
    }

    @Test
    fun logoutZeroesTheKey() = sessionTest {
        val (ctx, recorder) = signedIn()

        ctx.session.logout()

        assertTrue(recorder.keys.all { it.isZeroed() })
    }

    @Test
    fun passwordChangeZeroesTheOldKeyAndKeepsTheNewOne() = sessionTest {
        val (ctx, recorder) = signedIn()
        val oldKey = recorder.keys.last()

        ctx.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

        assertTrue(oldKey.isZeroed())
        val newKey = recorder.keys.last()
        assertFalse(newKey.isZeroed())
        ctx.session.save(withItem(ctx, "a"))
        assertEquals(listOf("a"), activeItems(ctx.unlockedState().vault.data).map { it.id })
    }

    @Test
    fun accountDeletionZeroesTheKey() = sessionTest {
        val (ctx, recorder) = signedIn()

        ctx.session.deleteAccount(PASSWORD)

        assertTrue(recorder.keys.all { it.isZeroed() })
    }

    @Test
    fun sealingWithAZeroedKeyIsRefused() = sessionTest {
        val source = Vectors.keysOf(Vectors.keyCases[0].text("name")).encryptionKey
        val key = EncryptionKey(source.bytes.copyOf())
        key.retire()

        assertFailsWith<WipedKeyException> { sealVault(key, "{}") }
    }

    @Test
    fun wipedIsDecidedByRetirementNotByByteValues() = sessionTest {
        // 0 바이트도 AES 키로는 유효하다. 지웠는지는 은퇴 표시로만 판단한다
        val key = EncryptionKey(ByteArray(32))

        assertEquals("{}", openVault(key, sealVault(key, "{}")))
    }

    @Test
    fun lockOverlappingConflictMergeReSealFinishesWithTheRealKey() = sessionTest {
        val (ctx, recorder) = signedIn()
        saveFromOtherDevice(ctx.vaultApi, "x")
        val key = recorder.keys.last()
        val original = EncryptionKey(key.bytes.copyOf())
        // 충돌 뒤 서버 볼트를 다시 받는 동안 잠근다. 병합한 볼트는 그 뒤에 다시 봉인된다
        val gate = Gate()
        ctx.vaultApi.getHook = {
            gate.pass()
            ctx.vaultApi.payload?.let { com.keymanage.domain.vault.RemoteVault(ctx.vaultApi.revision, it) }
        }

        val saving = begin { ctx.session.save(withItem(ctx, "a")) }
        waitFor { gate.waiting > 0 }
        ctx.session.lock()
        assertFalse(key.isZeroed())
        ctx.vaultApi.getHook = null
        gate.release()
        saving.await()

        val uploaded = ctx.vaultApi.payload ?: error("올린 볼트 없음")
        assertEquals(listOf("a", "x"), activeItems(parseVaultData(openVault(original, uploaded))).map { it.id }.sorted())
        assertTrue(key.isZeroed())
    }

    @Test
    fun lockOverlappingBackupExportNeverYieldsAZeroKeyBackup() = sessionTest {
        val (ctx, recorder) = signedIn()
        val key = recorder.keys.last()
        val original = EncryptionKey(key.bytes.copyOf())
        val calendar = LocalCalendar { LocalDay(2026, 10, 31) }

        val exporting = begin { ctx.session.exportBackup(NOW, calendar) }
        ctx.session.lock()
        val exported = runCatching { exporting.await() }

        // 끝났으면 원래 키로 열리고, 잠금이 먼저였으면 취소된다. 0 키로 봉인한 백업은 없다
        exported.onSuccess { openVault(original, parseBackup(it.text).vault) }
        exported.onFailure { assertIs<SessionStateException>(it) }
        assertTrue(key.isZeroed())
    }

    @Test
    fun passwordChangeWaitsForThePendingSendThatHoldsTheOldKey() = sessionTest {
        val (ctx, recorder) = signedIn()
        val oldKey = recorder.keys.last()
        val release = gateSaves(ctx.vaultApi)

        val saving = begin { ctx.session.save(withItem(ctx, "a")) }
        waitFor { ctx.vaultApi.gate!!.waiting > 0 }
        val changing = begin { ctx.session.changeMasterPassword(PASSWORD, NEW_PASSWORD) }
        assertFalse(oldKey.isZeroed())
        release()
        saving.await()
        changing.await()

        assertTrue(oldKey.isZeroed())
        assertFalse(recorder.keys.last().isZeroed())
        assertEquals(listOf("a"), activeItems(ctx.unlockedState().vault.data).map { it.id })
    }

    @Test
    fun cancellingALeaseHolderReleasesItAndWipesARetiredKey() = sessionTest {
        val source = Vectors.keysOf(Vectors.keyCases[0].text("name")).encryptionKey
        val key = EncryptionKey(source.bytes.copyOf())
        val never = kotlinx.coroutines.CompletableDeferred<Unit>()
        val holder = begin { key.lease { never.await() } }

        key.retire()
        assertFalse(key.isZeroed())
        holder.cancel()
        runCatching { holder.await() }

        assertTrue(key.isZeroed())
        assertFailsWith<WipedKeyException> { key.lease { } }
    }

    @Test
    fun lockBetweenOpeningAndResealingAnotherTabRecordStillSealsWithTheRealKey() = sessionTest {
        val source = Vectors.keysOf(Vectors.keyCases[0].text("name")).encryptionKey
        val key = EncryptionKey(source.bytes.copyOf())
        val original = EncryptionKey(source.bytes.copyOf())
        val sync = vaultSyncFor(FakeVaultApi())("", key, RevisionMemory())
        val empty = com.keymanage.domain.vault.emptyVault(NOW)
        val mineData = addItem(empty, fields, NOW, "mine")
        val theirData = addItem(empty, fields.copy(serviceName = "t"), NOW, "theirs")
        val mine = MemoryVault(mineData, sync.seal(mineData), 1, 1, pendingSync = true)
        val theirs = com.keymanage.domain.device.DeviceRecord(
            "user@example.com", com.keymanage.domain.auth.UnlockKdf("", 1), sync.seal(theirData), 1, 1, pendingSync = true,
        )

        // 다른 탭 기록을 연 뒤 다시 봉인하기 전에 잠긴다
        val merged = mergeRecordIntoMemory(mine, theirs, sync, afterOpen = { key.retire() })

        assertEquals(listOf("mine", "theirs"), activeItems(parseVaultData(openVault(original, merged.payload))).map { it.id }.sorted())
        assertTrue(key.isZeroed())
    }
}
