package com.keymanage.websession

import com.keymanage.domain.tabs.SingleTab
import com.keymanage.domain.vault.ItemFields
import com.keymanage.domain.vault.activeItems
import com.keymanage.domain.vault.addItem
import com.keymanage.domain.vault.deleteItem
import com.keymanage.domain.vault.encodeVaultData
import com.keymanage.domain.vault.parseVaultData
import com.keymanage.serverhttp.keyManageHttpClient
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val EMAIL = "user@example.com"
private const val PASSWORD = "correct horse battery staple"
private val TEST_TIMEOUT = 2.minutes

private fun newSession(server: FakeServer, store: MemoryDeviceStore = MemoryDeviceStore()) =
    buildKotlinSession(TEST_BASE_URL, keyManageHttpClient(server.engine), store, SingleTab(), tabMemory = null)

/** TypeScript가 받는 것과 같이, reject된 값을 가공 없이 꺼낸다 */
internal suspend fun Promise<*>.rejection(): Any? =
    then({ null as Any? }, { reason -> reason as Any? }).await()

private fun KotlinVaultSession.vault() = parseVaultData(checkNotNull(state().vaultJson))

private fun nowMs(): Double = Clock.System.now().toEpochMilliseconds().toDouble()

class KotlinVaultSessionTest {
    @Test
    fun registerSaveLockUnlockRoundTrip() = runTest(timeout = TEST_TIMEOUT) {
        val server = FakeServer()
        val session = newSession(server)
        assertEquals("signedOut", session.state().status)

        session.register(EMAIL, PASSWORD).await()
        val registered = session.state()
        assertEquals("unlocked", registered.status)
        assertEquals(EMAIL, registered.email)
        assertEquals(1, registered.revision)
        assertTrue(registered.signedIn)
        assertFalse(registered.pendingSync)
        assertFalse(registered.readOnly)
        assertNotNull(registered.payloadJson)
        assertEquals(0, activeItems(session.vault()).size)

        val item = addItem(session.vault(), ItemFields("GitHub", "octocat", "pw-1"), Clock.System.now(), "item-1")
        session.save(encodeVaultData(item)).await()
        assertEquals(2, session.state().revision)
        assertEquals(2, server.vaultRevision)
        assertFalse(session.state().pendingSync)

        session.lock("allTabs")
        val locked = session.state()
        assertEquals("locked", locked.status)
        assertEquals(EMAIL, locked.email)
        assertNull(locked.vaultJson)
        assertNull(locked.payloadJson)

        assertFalse(session.unlock(PASSWORD, false).await())
        val reopened = session.state()
        assertEquals("unlocked", reopened.status)
        assertEquals(2, reopened.revision)
        assertEquals(listOf("item-1"), activeItems(session.vault()).map { it.id })
        assertEquals(listOf("POST /api/v1/auth/register", "GET /api/v1/vault", "POST /api/v1/vault"), server.calls.take(3))
    }

    @Test
    fun stateIsCachedWhileNothingChanges() = runTest(timeout = TEST_TIMEOUT) {
        val session = newSession(FakeServer())
        session.register(EMAIL, PASSWORD).await()
        assertTrue(session.state() === session.state())
    }

    @Test
    fun wrongPasswordRejectsWithWrongMasterPassword() = runTest(timeout = TEST_TIMEOUT) {
        val session = newSession(FakeServer())
        session.register(EMAIL, PASSWORD).await()
        session.lock("thisTab")

        val info = assertNotNull(errorInfo(session.unlock("wrong password!!", false).rejection()))
        assertEquals("WrongMasterPasswordException", info.kind)
        assertNull(info.status)
        assertNull(info.code)
        assertNull(info.detailJson)
        assertEquals("locked", session.state().status)
    }

    @Test
    fun serverErrorFieldsAreExposed() = runTest(timeout = TEST_TIMEOUT) {
        val server = FakeServer().apply { preloginFailure = Triple(429, "TOO_MANY_ATTEMPTS", 7) }
        val session = newSession(server)

        val info = assertNotNull(errorInfo(session.login(EMAIL, PASSWORD, false).rejection()))
        assertEquals("ServerError", info.kind)
        assertEquals(429, info.status)
        assertEquals("TOO_MANY_ATTEMPTS", info.code)
        assertEquals(7, info.retryAfterSeconds)
        assertNull(info.detailJson)
    }

    @Test
    fun serverFailureWithoutRetryAfter() = runTest(timeout = TEST_TIMEOUT) {
        val server = FakeServer().apply { preloginFailure = Triple(500, "INTERNAL_ERROR", null) }
        val session = newSession(server)

        val info = assertNotNull(errorInfo(session.login(EMAIL, PASSWORD, false).rejection()))
        assertEquals("ServerError", info.kind)
        assertEquals(500, info.status)
        assertNull(info.retryAfterSeconds)
    }

    @Test
    fun revisionRollbackCarriesRevisionsAndCanBeAccepted() = runTest(timeout = TEST_TIMEOUT) {
        val server = FakeServer()
        val session = newSession(server)
        session.register(EMAIL, PASSWORD).await()
        session.save(encodeVaultData(addItem(session.vault(), ItemFields("GitHub", "octocat", "pw-1"), Clock.System.now(), "item-1"))).await()
        session.lock("allTabs")
        server.vaultRevision = 1

        val info = assertNotNull(errorInfo(session.unlock(PASSWORD, false).rejection()))
        assertEquals("RevisionRollbackException", info.kind)
        val detail = Json.parseToJsonElement(assertNotNull(info.detailJson)).jsonObject
        assertEquals(2, detail.getValue("lastSeenRevision").jsonPrimitive.int)
        assertEquals(1, detail.getValue("serverRevision").jsonPrimitive.int)
        assertEquals("locked", session.state().status)

        session.unlock(PASSWORD, true).await()
        assertEquals("unlocked", session.state().status)
        assertEquals(1, session.state().revision)
    }

    @Test
    fun listenersStopAfterUnsubscribe() = runTest(timeout = TEST_TIMEOUT) {
        val session = newSession(FakeServer())
        session.register(EMAIL, PASSWORD).await()
        var unlocks = 0
        var locks = 0
        val offUnlock = session.onUnlock { unlocks++ }
        val offLock = session.onLock { locks++ }

        session.lock("allTabs")
        session.unlock(PASSWORD, false).await()
        assertEquals(1, locks)
        assertEquals(1, unlocks)

        offUnlock()
        offLock()
        session.lock("allTabs")
        session.unlock(PASSWORD, false).await()
        assertEquals(1, locks)
        assertEquals(1, unlocks)
    }

    @Test
    fun invalidLockScopeIsRejectedSynchronously() = runTest(timeout = TEST_TIMEOUT) {
        val session = newSession(FakeServer())
        assertFailsWithIllegalArgument { session.lock("everywhere") }
    }

    @Test
    fun exportAndRestoreBackup() = runTest(timeout = TEST_TIMEOUT) {
        val session = newSession(FakeServer())
        session.register(EMAIL, PASSWORD).await()
        val added = Clock.System.now()
        session.save(encodeVaultData(addItem(session.vault(), ItemFields("GitHub", "octocat", "pw-1"), added, "item-1"))).await()

        val backup = session.exportBackup(nowMs()).await()
        val body = Json.parseToJsonElement(backup).jsonObject
        assertEquals("keymanage-backup", body.getValue("format").jsonPrimitive.content)
        assertEquals(1, body.getValue("formatVersion").jsonPrimitive.int)

        session.save(encodeVaultData(deleteItem(session.vault(), "item-1", added + 10.seconds))).await()
        assertEquals(0, activeItems(session.vault()).size)

        val restored = session.restoreBackup(backup, PASSWORD, nowMs() + 60_000).await()
        assertEquals(1, restored)
        assertEquals(listOf("item-1"), activeItems(session.vault()).map { it.id })
    }

    @Test
    fun backupErrorsAreReadableByKind() = runTest(timeout = TEST_TIMEOUT) {
        val session = newSession(FakeServer())
        session.register(EMAIL, PASSWORD).await()
        val backup = session.exportBackup(nowMs()).await()

        val notBackup = assertNotNull(errorInfo(session.restoreBackup("not json", PASSWORD, nowMs()).rejection()))
        assertEquals("NotABackupFileException", notBackup.kind)

        val wrong = assertNotNull(errorInfo(session.restoreBackup(backup, "wrong password!!", nowMs()).rejection()))
        assertEquals("BackupDecryptException", wrong.kind)
    }

    @Test
    fun restoreReadsDeviceRecordWrittenBySession() = runTest(timeout = TEST_TIMEOUT) {
        val server = FakeServer()
        val store = MemoryDeviceStore()
        newSession(server, store).register(EMAIL, PASSWORD).await()
        assertNotNull(store.stored)

        val reopened = newSession(server, store)
        reopened.restore().await()
        assertEquals("locked", reopened.state().status)
        assertEquals(EMAIL, reopened.state().email)
        assertFalse(reopened.hasUnsavedChanges())
        assertFalse(reopened.pendingOnDevice().await())
    }

    @Test
    fun logoutSignsOutAndClearsDevice() = runTest(timeout = TEST_TIMEOUT) {
        val server = FakeServer()
        val store = MemoryDeviceStore()
        val session = newSession(server, store)
        session.register(EMAIL, PASSWORD).await()

        session.logout().await()
        assertEquals("signedOut", session.state().status)
        assertNull(store.stored)
    }

    // 원본 TS는 호출 즉시 epoch를 읽는다. Promise가 늦게 시작하면 바로 뒤의 lock이 새어 나간다
    @Test
    fun lockRightAfterUnlockCallEndsLocked() = runTest(timeout = TEST_TIMEOUT) {
        val session = newSession(FakeServer())
        session.register(EMAIL, PASSWORD).await()
        session.lock("thisTab")

        val pending = session.unlock(PASSWORD, false)
        session.lock("thisTab")
        pending.rejection()

        assertEquals("locked", session.state().status)
    }

    // 원본 TS logout은 첫 await 전에 로그인 상태를 지운다
    @Test
    fun logoutSignsOutSynchronouslyOnCall() = runTest(timeout = TEST_TIMEOUT) {
        val session = newSession(FakeServer())
        session.register(EMAIL, PASSWORD).await()

        val pending = session.logout()
        assertEquals("signedOut", session.state().status)
        pending.await()
    }

    @Test
    fun lockDropsCachedPlainVault() = runTest(timeout = TEST_TIMEOUT) {
        val session = newSession(FakeServer())
        session.register(EMAIL, PASSWORD).await()
        assertNotNull(session.state().vaultJson)
        assertNotNull(session.cachedJs)

        session.lock("thisTab")

        assertNull(session.cachedJs)
        assertEquals("locked", session.state().status)
    }

    @Test
    fun invalidBaseUrlThrowsAtCreation() {
        val failure = runCatching { createKotlinSession("http://example.com", noopStore(), noopTabs(), null) }.exceptionOrNull()
        assertEquals("InvalidBaseUrlException", errorInfo(failure)?.kind)
    }
}

private inline fun assertFailsWithIllegalArgument(block: () -> Unit) {
    val failure = runCatching(block).exceptionOrNull()
    assertEquals("IllegalArgumentException", errorInfo(failure)?.kind)
}
