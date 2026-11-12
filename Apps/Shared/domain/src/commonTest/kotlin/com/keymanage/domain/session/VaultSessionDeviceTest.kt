package com.keymanage.domain.session

import com.keymanage.domain.auth.MasterPasswordChangedException
import com.keymanage.domain.device.DeviceStorageException
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.activeItems
import com.keymanage.domain.vault.addItem
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// 원본: Apps/Web/src/domain/session/vaultSession.device.test.ts
// 웹 기기 저장: 재시작 뒤 잠금 화면, 세션 없는 잠금 해제, 한 계정만 보관 (ADR-0019, SEC-003, VLT-003)

private const val NEW_SALT = "AAAAAAAAAAAAAAAAAAAAAA=="

private fun unlockedData(session: VaultSession): VaultData =
    (session.state() as? SessionState.Unlocked ?: error("잠금 해제 상태가 아님")).vault.data

private fun itemIds(session: VaultSession): List<String> = activeItems(unlockedData(session)).map { it.id }.sorted()

private suspend fun saveItem(session: VaultSession, id: String) {
    session.save(addItem(unlockedData(session), fields.copy(serviceName = id), NOW, id))
}

/** 항목 a를 올린 뒤 오프라인에서 항목 b를 저장해 기기에 대기로 남긴다 */
private suspend fun TestScope.pendingOnDevice(): SessionSetup {
    val ctx = unlockedWithItem()
    ctx.vaultApi.offline = true
    saveItem(ctx.session, "b")
    return ctx
}

class VaultSessionDeviceStoredValuesTest {
    @Test
    fun loginStoresPublicAndEncryptedValuesOnly() = sessionTest {
        val ctx = unlockedWithItem()

        val record = ctx.deviceStore.record!!
        assertEquals("user@example.com", record.email)
        assertEquals(2, record.revision)
        assertEquals(2, record.lastSeenRevision)
        assertFalse(record.pendingSync)
        val stored = ctx.deviceStore.stored!!
        assertTrue(stored.contains("\"version\":1"))
        assertFalse(stored.contains("token-1"))
        assertFalse(stored.contains(ctx.authApi.logins[0].authHash))
        assertFalse(stored.contains("octocat"))
    }

    @Test
    fun offlineSaveStaysOnDeviceAsPending() = sessionTest {
        val ctx = pendingOnDevice()

        val record = ctx.deviceStore.record!!
        assertEquals(2, record.revision)
        assertTrue(record.pendingSync)
    }
}

class VaultSessionDeviceReopenTest {
    @Test
    fun startsLockedWhenDeviceRecordExists() = sessionTest {
        val ctx = unlockedWithItem()

        val next = restart(ctx)

        assertEquals(SessionState.Locked("user@example.com", pendingSync = false), next.session.state())
    }

    @Test
    fun startsSignedOutWhenDeviceRecordIsMissingOrBroken() = sessionTest {
        val ctx = setup()
        ctx.deviceStore.stored = """{"version":1,"email":"user@example.com"}"""

        val next = restart(ctx)

        assertEquals(SessionState.SignedOut, next.session.state())
    }

    @Test
    fun onlineUnlockLogsInAndSyncsWithServer() = sessionTest {
        val ctx = unlockedWithItem()
        val next = restart(ctx)
        val loginsBefore = next.authApi.logins.size

        assertEquals(VaultSession.UnlockResult(offline = false), next.session.unlock(PASSWORD))

        assertEquals(loginsBefore + 1, next.authApi.logins.size)
        val state = next.unlockedState()
        assertTrue(state.signedIn)
        assertEquals(2, state.vault.revision)
    }

    @Test
    fun wrongPasswordSendsNoLoginRequestAndStaysLocked() = sessionTest {
        val ctx = unlockedWithItem()
        val next = restart(ctx)
        val loginsBefore = next.authApi.logins.size

        assertFailsWith<WrongMasterPasswordException> { next.session.unlock("wrong password") }
        assertEquals(loginsBefore, next.authApi.logins.size)
    }

    @Test
    fun offlineOpensStoredVaultWithoutSessionAndKeepsSavePending() = sessionTest {
        val ctx = unlockedWithItem()
        val next = restart(ctx)
        next.authApi.offline = true
        next.vaultApi.offline = true

        assertEquals(VaultSession.UnlockResult(offline = true), next.session.unlock(PASSWORD))
        saveItem(next.session, "b")

        val state = next.unlockedState()
        assertFalse(state.signedIn)
        assertTrue(state.pendingSync)
        assertEquals(listOf("a", "b"), itemIds(next.session))
        assertTrue(next.deviceStore.record!!.pendingSync)
    }

    @Test
    fun reconnectWithPasswordLogsInAndUploadsPending() = sessionTest {
        val ctx = unlockedWithItem()
        val next = restart(ctx)
        next.authApi.offline = true
        next.vaultApi.offline = true
        next.session.unlock(PASSWORD)
        saveItem(next.session, "b")
        next.authApi.offline = false
        next.vaultApi.offline = false

        assertFailsWith<WrongMasterPasswordException> { next.session.reconnect("wrong password") }
        next.session.reconnect(PASSWORD)

        val state = next.unlockedState()
        assertTrue(state.signedIn)
        assertFalse(state.pendingSync)
        assertEquals(listOf("a", "b"), serverItemIds(next.vaultApi))
    }

    @Test
    fun reconnectRollbackStaysSignedOutRevokesTokenAndAcceptingSyncs() = sessionTest {
        val ctx = unlockedWithItem()
        val next = restart(ctx)
        next.authApi.offline = true
        next.vaultApi.offline = true
        next.session.unlock(PASSWORD)
        next.authApi.offline = false
        next.vaultApi.offline = false
        next.vaultApi.revision = 1

        assertFailsWith<RevisionRollbackException> { next.session.reconnect(PASSWORD) }
        assertFalse(next.unlockedState().signedIn)
        // 토큰 폐기 요청은 기다리지 않고 보내므로, 가짜 서버가 받을 때까지 큐에 쌓인 작업을 끝낸다
        waitFor { next.authApi.loggedOut.isNotEmpty() }
        assertEquals(1, next.authApi.loggedOut.size)

        next.session.reconnect(PASSWORD, acceptRollback = true)
        assertTrue(next.unlockedState().signedIn)
    }

    @Test
    fun unlockOpensVaultAndReportsWhenServerRejectsWithoutSession() = sessionTest {
        val ctx = unlockedWithItem()
        val next = restart(ctx)
        val results = mutableListOf<Throwable?>()
        next.session.onSyncChange { error -> results.add(error) }
        next.authApi.preloginHook = { throw ServerError(403, ServerErrorCode.ORIGIN_NOT_ALLOWED) }

        assertEquals(VaultSession.UnlockResult(offline = true), next.session.unlock(PASSWORD))

        assertFalse(next.unlockedState().signedIn)
        assertIs<ServerError>(results.last())
    }

    @Test
    fun reconnectRejectedByServerThrowsActualReason() = sessionTest {
        val ctx = unlockedWithItem()
        val next = restart(ctx)
        next.authApi.offline = true
        next.vaultApi.offline = true
        next.session.unlock(PASSWORD)
        next.authApi.offline = false
        next.vaultApi.offline = false

        for (failure in listOf(ServerError(403, ServerErrorCode.ORIGIN_NOT_ALLOWED), ServerError(503, ServerErrorCode.UNKNOWN))) {
            next.authApi.preloginHook = { throw failure }
            val thrown = assertFailsWith<ServerError> { next.session.reconnect(PASSWORD) }
            assertSame(failure, thrown)
        }
        assertFalse(next.unlockedState().signedIn)
    }

    @Test
    fun unlockWithSameVaultDoesNotRewriteDeviceRecord() = sessionTest {
        val ctx = unlockedWithItem()
        val next = restart(ctx)
        val writes = next.deviceStore.writes

        next.session.unlock(PASSWORD)

        assertEquals(writes, next.deviceStore.writes)
    }

    @Test
    fun loginRereadsDeviceRecordAfterFailedRestoreAndMergesPending() = sessionTest {
        val ctx = pendingOnDevice()
        ctx.vaultApi.offline = false
        ctx.deviceStore.failLoads = 1
        val next = setup(authApi = ctx.authApi, vaultApi = ctx.vaultApi, deviceStore = ctx.deviceStore)
        assertFailsWith<DeviceStorageException> { next.session.restore() }
        assertEquals(SessionState.SignedOut, next.session.state())

        next.session.login("user@example.com", PASSWORD)

        assertEquals(listOf("a", "b"), itemIds(next.session))
        assertEquals(listOf("a", "b"), serverItemIds(ctx.vaultApi))
        assertFalse(ctx.deviceStore.record!!.pendingSync)
    }

    @Test
    fun unreadableDeviceRecordIsNotOverwrittenAndStoreFailureIsReported() = sessionTest {
        val ctx = pendingOnDevice()
        ctx.vaultApi.offline = false
        val before = ctx.deviceStore.stored
        ctx.deviceStore.failLoads = 3
        val next = setup(authApi = ctx.authApi, vaultApi = ctx.vaultApi, deviceStore = ctx.deviceStore)
        runCatching { next.session.restore() }

        next.session.login("user@example.com", PASSWORD)

        assertEquals(before, ctx.deviceStore.stored)
        assertFalse(next.unlockedState().storedOnDevice)
    }

    @Test
    fun lateReadPendingRecordIsNotOverwrittenAndReloadMergesIt() = sessionTest {
        val ctx = pendingOnDevice()
        ctx.vaultApi.offline = false
        val before = ctx.deviceStore.stored
        // 시작할 때와 로그인할 때는 읽지 못하고, 처음 쓰기 직전에야 읽힌다
        ctx.deviceStore.failLoads = 2
        val next = setup(authApi = ctx.authApi, vaultApi = ctx.vaultApi, deviceStore = ctx.deviceStore)
        runCatching { next.session.restore() }

        next.session.login("user@example.com", PASSWORD)

        assertEquals(before, ctx.deviceStore.stored)
        assertFalse(next.unlockedState().storedOnDevice)

        val reopened = restart(ctx)
        reopened.session.unlock(PASSWORD)
        assertEquals(listOf("a", "b"), serverItemIds(ctx.vaultApi))
    }

    @Test
    fun pendingSurvivesRestartAndUploadsOnUnlock() = sessionTest {
        val ctx = pendingOnDevice()
        val next = restart(ctx)
        next.vaultApi.offline = false

        val locked = assertIs<SessionState.Locked>(next.session.state())
        assertTrue(locked.pendingSync)
        next.session.unlock(PASSWORD)

        assertEquals(listOf("a", "b"), serverItemIds(next.vaultApi))
        assertFalse(next.unlockedState().pendingSync)
    }

    @Test
    fun rollbackBaselineSurvivesRestart() = sessionTest {
        val ctx = unlockedWithItem()
        // 서버가 백업에서 복구되어 이 기기가 본 revision 2보다 낮은 1로 돌아갔다
        ctx.vaultApi.revision = 1
        val next = restart(ctx)

        assertFailsWith<RevisionRollbackException> { next.session.unlock(PASSWORD) }
    }
}

class VaultSessionDevicePasswordChangedTest {
    @Test
    fun differentServerKdfNotifiesAndGoesToSignedOut() = sessionTest {
        val ctx = unlockedWithItem()
        val next = restart(ctx)
        next.authApi.kdf = next.authApi.kdf.copy(salt = "AAAAAAAAAAAAAAAAAAAAAA==")

        assertFailsWith<MasterPasswordChangedException> { next.session.unlock(PASSWORD) }
        assertEquals(SessionState.SignedOut, next.session.state())
    }

    @Test
    fun sameEmailLoginWithChangedKdfStillChecksRollbackAndReplacesCopyOnlyAfterAccepting() = sessionTest {
        val ctx = pendingOnDevice()
        val next = restart(ctx)
        // 서버가 salt만 바꾸고 빈 볼트를 준다. 비밀번호 변경이 아니라 서버 쪽 유실·조작일 수 있다
        next.authApi.kdf = next.authApi.kdf.copy(salt = NEW_SALT)
        next.vaultApi.offline = false
        next.vaultApi.payload = null
        next.vaultApi.revision = 0

        assertFailsWith<RevisionRollbackException> { next.session.login("user@example.com", PASSWORD) }
        assertTrue(next.deviceStore.record!!.pendingSync)
        assertNotEquals(NEW_SALT, next.deviceStore.record!!.kdf.salt)

        next.session.login("user@example.com", PASSWORD, acceptRollback = true)

        val record = next.deviceStore.record!!
        assertEquals(NEW_SALT, record.kdf.salt)
        assertFalse(record.pendingSync)
        assertEquals(emptyList<String>(), itemIds(next.session))
    }
}

class VaultSessionDeviceSingleAccountTest {
    @Test
    fun otherAccountLoginReplacesPreviousRecord() = sessionTest {
        val ctx = unlockedWithItem()
        val next = restart(ctx)

        next.session.login("other@example.com", PASSWORD)

        assertEquals("other@example.com", next.deviceStore.record?.email)
    }

    @Test
    fun otherAccountAuthClearsPreviousRecordEvenIfVaultSetupFails() = sessionTest {
        val ctx = unlockedWithItem()
        val next = restart(ctx)
        next.vaultApi.getHook = { throw ServerError(500, ServerErrorCode.INTERNAL_ERROR) }

        assertFailsWith<ServerError> { next.session.login("other@example.com", PASSWORD) }

        assertNull(next.deviceStore.stored)
    }

    @Test
    fun failedLoginKeepsRecord() = sessionTest {
        val ctx = unlockedWithItem()
        val next = restart(ctx)
        next.authApi.loginHook = { throw Exception("인증 실패") }

        assertFailsWith<Exception> { next.session.login("other@example.com", PASSWORD) }

        assertEquals("user@example.com", next.deviceStore.record?.email)
    }

    @Test
    fun logoutClearsRecord() = sessionTest {
        val ctx = unlockedWithItem()

        ctx.session.logout()

        assertNull(ctx.deviceStore.stored)
    }

    @Test
    fun logoutStillSignsOutAndReportsWhenRecordCannotBeCleared() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.deviceStore.failing = true

        assertFailsWith<DeviceStorageException> { ctx.session.logout() }

        assertEquals(SessionState.SignedOut, ctx.session.state())
    }

    @Test
    fun logoutFromLockScreenClearsRecord() = sessionTest {
        val ctx = unlockedWithItem()
        val next = restart(ctx)

        next.session.logout()

        assertNull(next.deviceStore.stored)
        assertEquals(SessionState.SignedOut, next.session.state())
    }
}

class VaultSessionDeviceStorageFailureTest {
    @Test
    fun failedStoreKeepsVaultReportsFailureAndNeedsLeaveConfirmation() = sessionTest {
        val ctx = unlockedWithItem()
        val results = mutableListOf<Throwable?>()
        ctx.session.onSyncChange { error -> results.add(error) }
        ctx.deviceStore.failing = true
        ctx.vaultApi.offline = true

        saveItem(ctx.session, "b")

        assertEquals(listOf("a", "b"), itemIds(ctx.session))
        assertIs<DeviceStorageException>(results.last())
        assertTrue(ctx.session.hasUnsavedChanges())
        assertFalse(ctx.unlockedState().storedOnDevice)
    }

    @Test
    fun nextSuccessfulDeviceStoreClearsFailureFlag() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.deviceStore.failing = true
        saveItem(ctx.session, "b")
        ctx.deviceStore.failing = false

        saveItem(ctx.session, "c")

        assertTrue(ctx.unlockedState().storedOnDevice)
    }

    @Test
    fun pendingStoredOnDeviceNeedsNoLeaveConfirmation() = sessionTest {
        val ctx = pendingOnDevice()

        assertFalse(ctx.session.hasUnsavedChanges())
    }
}

class VaultSessionDeviceLateResponseTest {
    @Test
    fun lateSaveResponseOfPreviousAccountDoesNotRaiseRollbackBaseline() = sessionTest {
        val ctx = unlockedWithItem()
        val gate = Gate()
        // 원본의 `vaultApi.save`를 한 번만 바꿔 끼우고 원래 save를 부르던 것과 같다
        ctx.vaultApi.saveHook = { expectedRevision, payload ->
            ctx.vaultApi.saveHook = null
            gate.pass()
            ctx.vaultApi.save(expectedRevision, payload)
        }
        val saving = begin { runCatching { saveItem(ctx.session, "b") } }
        waitFor { gate.waiting > 0 }
        ctx.session.logout()
        ctx.session.login("other@example.com", PASSWORD)
        val seen = ctx.revisionMemory.get()

        gate.release()
        saving.await()

        assertEquals(seen, ctx.revisionMemory.get())
    }
}
