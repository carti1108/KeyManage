package com.keymanage.domain.session

import com.keymanage.domain.auth.Session
import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.server.UnexpectedResponseError
import com.keymanage.domain.vault.RevisionMismatchException
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.VaultSyncConflictException
import com.keymanage.domain.vault.VaultTooLargeException
import com.keymanage.domain.vault.activeItems
import com.keymanage.domain.vault.addItem
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// 원본: Apps/Web/src/domain/session/vaultSession.pending.test.ts
// 저장이 서버에 닿지 못했을 때 동기화 대기로 두고 나중에 올린다 (VLT-002)

private fun unlockedData(session: VaultSession): VaultData =
    (session.state() as? SessionState.Unlocked ?: error("잠금 해제 상태가 아님")).vault.data

private fun itemIds(session: VaultSession): List<String> = activeItems(unlockedData(session)).map { it.id }.sorted()

private suspend fun saveItem(session: VaultSession, id: String) {
    session.save(addItem(unlockedData(session), fields.copy(serviceName = id), NOW, id))
}

/** 대기 상태의 준비물. 원본의 `{ ...ctx, syncResults }` */
private class Pending(val ctx: SessionSetup, val syncResults: MutableList<Throwable?>)

/** 항목 a를 올린 뒤 오프라인에서 항목 b를 저장해 대기 상태를 만든다 */
private suspend fun TestScope.pendingWithItemB(): Pending {
    val ctx = unlockedWithItem()
    val syncResults = mutableListOf<Throwable?>()
    ctx.session.onSyncChange { error -> syncResults.add(error) }
    ctx.vaultApi.offline = true
    saveItem(ctx.session, "b")
    return Pending(ctx, syncResults)
}

class VaultSessionPendingOnUnreachableTest {
    @Test
    fun networkErrorSucceedsAndShowsEditsWithPendingMark() = sessionTest {
        val ctx = pendingWithItemB().ctx

        val state = ctx.unlockedState()
        assertTrue(state.pendingSync)
        assertEquals(2, state.vault.revision)
        assertEquals(listOf("a", "b"), itemIds(ctx.session))
        assertEquals(2, ctx.vaultApi.revision)
    }

    @Test
    fun serverErrorTooManyRequestsAndUnexpectedResponseAlsoStayPending() = sessionTest {
        val failures = listOf(
            ServerError(503, ServerErrorCode.UNKNOWN),
            ServerError(429, ServerErrorCode.TOO_MANY_ATTEMPTS),
            UnexpectedResponseError("형식 오류"),
        )
        for (failure in failures) {
            val ctx = unlockedWithItem()
            ctx.vaultApi.saveHook = { _, _ -> throw failure }

            saveItem(ctx.session, "b")

            assertTrue(ctx.unlockedState().pendingSync)
            assertEquals(listOf("a", "b"), itemIds(ctx.session))
        }
    }

    @Test
    fun staysPendingWhenLockedOrExpiredAndNeedsLeaveConfirmationIfNotStoredOnDevice() = sessionTest {
        val ctx = pendingWithItemB().ctx
        ctx.deviceStore.failing = true
        saveItem(ctx.session, "c")

        ctx.session.lock()
        assertEquals(SessionState.Locked("user@example.com", pendingSync = true), ctx.session.state())
        assertTrue(ctx.session.hasUnsavedChanges())

        ctx.vaultApi.offline = false
        ctx.vaultApi.getHook = { throw ServerError(401, ServerErrorCode.UNAUTHORIZED) }
        assertFailsWith<SessionExpiredException> { ctx.session.unlock(PASSWORD) }
        assertTrue(ctx.session.hasUnsavedChanges())
    }

    @Test
    fun clearRejectionLikeTooLargeIsNotQueuedAndThrows() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.vaultApi.offline = true
        val huge = addItem(unlockedData(ctx.session), fields.copy(notes = "x".repeat(6_000_000)), NOW, "big")

        assertFailsWith<VaultTooLargeException> { ctx.session.save(huge) }
        assertFalse(ctx.unlockedState().pendingSync)
        assertEquals(listOf("a"), itemIds(ctx.session))
    }

    @Test
    fun onlineSaveSuccessHasNoPendingMark() = sessionTest {
        val ctx = unlockedWithItem()

        val state = ctx.unlockedState()
        assertFalse(state.pendingSync)
        assertEquals(2, state.vault.revision)
    }
}

class VaultSessionPendingRetryTest {
    @Test
    fun editsAccumulateWhilePendingAndAllUploadOnReconnect() = sessionTest {
        val (ctx, syncResults) = pendingWithItemB().let { it.ctx to it.syncResults }
        saveItem(ctx.session, "c")
        ctx.vaultApi.offline = false

        ctx.session.retryPendingSync()

        val state = ctx.unlockedState()
        assertFalse(state.pendingSync)
        assertEquals(3, state.vault.revision)
        assertEquals(listOf("a", "b", "c"), serverItemIds(ctx.vaultApi))
        assertEquals(listOf<Throwable?>(null), syncResults)
    }

    @Test
    fun mergesWhenAnotherDeviceSavedInTheMeantime() = sessionTest {
        val ctx = pendingWithItemB().ctx
        ctx.vaultApi.offline = false
        saveFromOtherDevice(ctx.vaultApi, "x")

        ctx.session.retryPendingSync()

        assertEquals(listOf("a", "b", "x"), itemIds(ctx.session))
        assertEquals(listOf("a", "b", "x"), serverItemIds(ctx.vaultApi))
        assertFalse(ctx.unlockedState().pendingSync)
    }

    @Test
    fun networkErrorAgainQuietlyKeepsPending() = sessionTest {
        val (ctx, syncResults) = pendingWithItemB().let { it.ctx to it.syncResults }

        ctx.session.retryPendingSync()

        assertTrue(ctx.unlockedState().pendingSync)
        assertEquals(emptyList<Throwable?>(), syncResults)
    }

    @Test
    fun clearRejectionIsReportedAndNotResentUntilSaveOrUnlock() = sessionTest {
        val (ctx, syncResults) = pendingWithItemB().let { it.ctx to it.syncResults }
        ctx.vaultApi.offline = false
        ctx.vaultApi.saveHook = { _, _ -> throw ServerError(400, ServerErrorCode.INVALID_PARAMETER) }

        ctx.session.retryPendingSync()
        ctx.session.retryPendingSync()

        assertEquals(1, syncResults.size)
        assertIs<ServerError>(syncResults[0])
        assertTrue(ctx.unlockedState().pendingSync)
    }

    @Test
    fun mismatchedSaveResponseRevisionThrowsInsteadOfHidingAsTransientFailure() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.vaultApi.saveHook = { _, _ -> 99 }

        assertFailsWith<RevisionMismatchException> { saveItem(ctx.session, "b") }
    }

    @Test
    fun lockedWhileSendingStillClearsPendingMarkOnLockScreenAndNotifies() = sessionTest {
        val (ctx, syncResults) = pendingWithItemB().let { it.ctx to it.syncResults }
        ctx.vaultApi.offline = false
        val gate = Gate()
        // 원본의 `vaultApi.save`를 바꿔 끼우고 원래 save를 부르던 것과 같다. 한 번만 가로챈다
        ctx.vaultApi.saveHook = { expectedRevision, payload ->
            ctx.vaultApi.saveHook = null
            gate.pass()
            ctx.vaultApi.save(expectedRevision, payload)
        }

        val retrying = begin { ctx.session.retryPendingSync() }
        waitFor { gate.waiting > 0 }
        ctx.session.lock()
        gate.release()
        retrying.await()

        assertEquals(SessionState.Locked("user@example.com", pendingSync = false), ctx.session.state())
        assertEquals(listOf<Throwable?>(null), syncResults)
    }

    @Test
    fun noRequestWhenNothingIsPending() = sessionTest {
        val ctx = unlockedWithItem()
        val before = ctx.vaultApi.saves

        ctx.session.retryPendingSync()

        assertEquals(before, ctx.vaultApi.saves)
    }

    @Test
    fun editDuringResendIsSentInOrderAndBothUpload() = sessionTest {
        val ctx = pendingWithItemB().ctx
        ctx.vaultApi.offline = false
        val release = gateSaves(ctx.vaultApi)

        val retrying = begin { ctx.session.retryPendingSync() }
        // 원본은 암호화를 기다리는 사이 다시 보내기가 문에 먼저 닿았다. JVM은 암호화가 중단되지 않으므로, 다시 보내기가 문에 닿은 뒤 수정한다
        waitFor { ctx.vaultApi.gate!!.waiting > 0 }
        val saving = begin { saveItem(ctx.session, "c") }
        release()
        retrying.await()
        saving.await()

        assertEquals(listOf("a", "b", "c"), serverItemIds(ctx.vaultApi))
        assertFalse(ctx.unlockedState().pendingSync)
    }
}

class VaultSessionPendingLockUnlockTest {
    @Test
    fun unlockOfflineAfterLockShowsPendingContentAsIs() = sessionTest {
        val ctx = pendingWithItemB().ctx
        ctx.session.lock()

        assertEquals(VaultSession.UnlockResult(offline = true), ctx.session.unlock(PASSWORD))
        assertEquals(listOf("a", "b"), itemIds(ctx.session))
        assertTrue(ctx.unlockedState().pendingSync)
    }

    @Test
    fun unlockOnlineAfterLockMergesPendingInsteadOfOverwritingWithServerVault() = sessionTest {
        val ctx = pendingWithItemB().ctx
        ctx.session.lock()
        ctx.vaultApi.offline = false
        saveFromOtherDevice(ctx.vaultApi, "x")

        assertEquals(VaultSession.UnlockResult(offline = false), ctx.session.unlock(PASSWORD))
        assertEquals(listOf("a", "b", "x"), itemIds(ctx.session))
        assertEquals(listOf("a", "b", "x"), serverItemIds(ctx.vaultApi))
        assertFalse(ctx.unlockedState().pendingSync)
    }
}

class VaultSessionPendingUnlockRollbackTest {
    @Test
    fun fetchesServerVaultFirstEvenWithPendingAndCatchesServerRolledBackToBaseline() = sessionTest {
        val ctx = pendingWithItemB().ctx
        ctx.session.lock()
        ctx.vaultApi.offline = false
        // 이 기기는 revision 3까지 봤지만 서버는 백업에서 복구되어 2로 돌아갔다
        ctx.revisionMemory.raise(3)

        assertFailsWith<RevisionRollbackException> { ctx.session.unlock(PASSWORD) }
    }
}

private suspend fun TestScope.expiredWithPending(): Pending {
    val pending = pendingWithItemB()
    val vaultApi = pending.ctx.vaultApi
    vaultApi.offline = false
    vaultApi.saveHook = { _, _ -> throw ServerError(401, ServerErrorCode.UNAUTHORIZED) }
    pending.ctx.session.retryPendingSync()
    vaultApi.saveHook = null
    return pending
}

class VaultSessionPendingExpiredTest {
    @Test
    fun signsOutAndNotifiesExpiry() = sessionTest {
        val (ctx, syncResults) = expiredWithPending().let { it.ctx to it.syncResults }

        assertEquals(SessionState.SignedOut, ctx.session.state())
        assertEquals(1, syncResults.size)
        assertIs<SessionExpiredException>(syncResults[0])
        assertEquals(1, ctx.locks())
    }

    @Test
    fun loginWithSameAccountMergesPendingAndUploads() = sessionTest {
        val ctx = expiredWithPending().ctx
        saveFromOtherDevice(ctx.vaultApi, "x")

        ctx.session.login("User@Example.com", PASSWORD)

        assertEquals(listOf("a", "b", "x"), itemIds(ctx.session))
        assertEquals(listOf("a", "b", "x"), serverItemIds(ctx.vaultApi))
        assertFalse(ctx.unlockedState().pendingSync)
    }

    @Test
    fun failedLoginWithOtherEmailKeepsPending() = sessionTest {
        val ctx = expiredWithPending().ctx
        ctx.authApi.loginHook = { throw ServerError(401, ServerErrorCode.AUTH_FAILED) }
        assertFailsWith<ServerError> { ctx.session.login("typo@example.co", PASSWORD) }
        ctx.authApi.loginHook = { Session("token-1", "2026-11-30T00:00:00Z") }

        ctx.session.login("user@example.com", PASSWORD)

        assertEquals(listOf("a", "b"), serverItemIds(ctx.vaultApi))
    }

    @Test
    fun repeatedConflictOnRelogFailsLoginAndKeepsPendingForNextLogin() = sessionTest {
        val ctx = expiredWithPending().ctx
        ctx.vaultApi.saveHook = { _, _ -> throw ServerError(409, ServerErrorCode.VAULT_CONFLICT) }
        assertFailsWith<VaultSyncConflictException> { ctx.session.login("user@example.com", PASSWORD) }
        ctx.vaultApi.saveHook = null

        ctx.session.login("user@example.com", PASSWORD)

        assertEquals(listOf("a", "b"), serverItemIds(ctx.vaultApi))
    }

    @Test
    fun serverRejectingPendingOnRelogOpensServerVaultDropsPendingAndNotifies() = sessionTest {
        val (ctx, syncResults) = expiredWithPending().let { it.ctx to it.syncResults }
        ctx.vaultApi.saveHook = { _, _ -> throw ServerError(413, ServerErrorCode.VAULT_TOO_LARGE) }

        ctx.session.login("user@example.com", PASSWORD)

        assertEquals(listOf("a"), itemIds(ctx.session))
        assertFalse(ctx.unlockedState().pendingSync)
        assertIs<ServerError>(syncResults.last())
    }

    @Test
    fun unreachableServerOnRelogOpensWithPendingMergedWithFetchedServerVault() = sessionTest {
        val ctx = expiredWithPending().ctx
        saveFromOtherDevice(ctx.vaultApi, "x")
        ctx.vaultApi.saveHook = { _, _ -> throw NetworkError() }

        ctx.session.login("user@example.com", PASSWORD)

        assertEquals(listOf("a", "b", "x"), itemIds(ctx.session))
        assertTrue(ctx.unlockedState().pendingSync)
    }

    @Test
    fun lateUnauthorizedDoesNotEndNewSessionAfterLogoutAndOtherAccountLoginDuringUnlock() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()
        val gate = Gate()
        // 원본의 `vaultApi.get`을 한 번만 바꿔 끼우고 문이 열리면 401을 내던 것과 같다
        ctx.vaultApi.getHook = {
            ctx.vaultApi.getHook = null
            gate.pass()
            throw ServerError(401, ServerErrorCode.UNAUTHORIZED)
        }

        val unlocking = begin { runCatching { ctx.session.unlock(PASSWORD) } }
        waitFor { gate.waiting > 0 }
        ctx.session.logout()
        ctx.session.login("other@example.com", PASSWORD)
        gate.release()
        unlocking.await()

        assertEquals("other@example.com", ctx.unlockedState().email)
    }

    @Test
    fun loginWithOtherAccountDropsPending() = sessionTest {
        val ctx = expiredWithPending().ctx

        ctx.session.login("other@example.com", PASSWORD)
        ctx.session.logout()
        ctx.session.login("user@example.com", PASSWORD)

        assertEquals(listOf("a"), serverItemIds(ctx.vaultApi))
    }
}
