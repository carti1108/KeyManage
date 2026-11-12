package com.keymanage.domain.session

import com.keymanage.domain.auth.DeleteAccountRequest
import com.keymanage.domain.auth.AuthService
import com.keymanage.domain.auth.MasterPasswordChangedException
import com.keymanage.domain.crypto.EncryptedVaultPayload
import com.keymanage.domain.crypto.EncryptionKey
import com.keymanage.domain.crypto.Vectors
import com.keymanage.domain.crypto.sealVault
import com.keymanage.domain.crypto.text
import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.tabs.InMemoryTabHub
import com.keymanage.domain.tabs.SingleTab
import com.keymanage.domain.tabs.Tabs
import com.keymanage.domain.vault.RemoteVault
import com.keymanage.domain.vault.RevisionMemory
import com.keymanage.domain.vault.VaultApi
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.VaultSync
import com.keymanage.domain.vault.addItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 계정 삭제: 비밀번호로 만든 인증 해시를 보내 서버에서 지우고, 이 기기의 기록과 탭 기억을 지워 모든 탭을 로그아웃한다 (SEC-005, ADR-0024)
// 원본: Apps/Web/src/domain/session/vaultSession.deleteAccount.test.ts

private const val EMAIL = "user@example.com"

private class DeleteTestServer(val authApi: FakeAuthApi, val vaultApi: FakeVaultApi)

private fun newServer(): DeleteTestServer {
    val authApi = FakeAuthApi()
    val vaultApi = FakeVaultApi()
    authApi.vaultApi = vaultApi
    return DeleteTestServer(authApi, vaultApi)
}

/** 실제 서버처럼 폐기된 토큰의 볼트 요청을 401로 거절한다 */
private class TokenCheckedVaultApi(private val server: DeleteTestServer, private val token: String) : VaultApi {
    private fun check() {
        if (token in server.authApi.revoked) throw ServerError(401, ServerErrorCode.UNAUTHORIZED)
    }

    override suspend fun get(): RemoteVault? {
        check()
        return server.vaultApi.get()
    }

    override suspend fun save(expectedRevision: Int, payload: EncryptedVaultPayload): Int {
        check()
        return server.vaultApi.save(expectedRevision, payload)
    }
}

/** 한 기기(또는 탭). 같은 서버·기기 저장소·탭 허브를 넘기면 같은 브라우저의 다른 탭이 된다 */
private class DeleteTestDevice(
    val session: VaultSession,
    val server: DeleteTestServer,
    val deviceStore: FakeDeviceStore,
    val tabMemory: FakeTabMemory,
    val syncErrors: MutableList<Throwable?>,
) {
    val authApi: FakeAuthApi get() = server.authApi
    val vaultApi: FakeVaultApi get() = server.vaultApi
}

private fun TestScope.device(
    server: DeleteTestServer = newServer(),
    deviceStore: FakeDeviceStore = FakeDeviceStore(),
    tabs: Tabs = SingleTab(),
    tabMemory: FakeTabMemory = FakeTabMemory(),
): DeleteTestDevice {
    val syncErrors = mutableListOf<Throwable?>()
    val session = VaultSession(
        VaultSessionDeps(
            auth = AuthService(server.authApi),
            vaultSyncFor = { token, encryptionKey, revisionMemory -> VaultSync(TokenCheckedVaultApi(server, token), encryptionKey, clockAtNow, revisionMemory) },
            revisionMemory = RevisionMemory(),
            deviceStore = deviceStore,
            tabs = tabs,
            scope = backgroundScope,
            tabMemory = tabMemory,
        ),
    )
    session.onSyncChange { error -> syncErrors += error }
    return DeleteTestDevice(session, server, deviceStore, tabMemory, syncErrors)
}

private suspend fun TestScope.signedIn(server: DeleteTestServer = newServer()): DeleteTestDevice {
    val ctx = device(server)
    ctx.session.login(EMAIL, PASSWORD)
    return ctx
}

private fun unlockedData(session: VaultSession): VaultData =
    (session.state() as? SessionState.Unlocked ?: error("잠금 해제 상태가 아님")).vault.data

private suspend fun saveItem(session: VaultSession, id: String) =
    session.save(addItem(unlockedData(session), fields.copy(serviceName = id), NOW, id))

private val vector = Vectors.keyCases[0]

/** 서버에 보내는 인증 해시. 테스트 벡터의 값이다 */
private fun expectedAuthHash(): String = vector.text("authHash")

/** 계정 삭제 요청을 문 앞에 세운다. 원본의 `deleteGate` Promise에 해당한다 */
private fun gateDeletes(authApi: FakeAuthApi): Gate = Gate().also { authApi.deleteGate = it }

private fun releaseDeletes(authApi: FakeAuthApi, gate: Gate) {
    authApi.deleteGate = null
    gate.release()
}

/** 원본의 `setTimeout(20)`처럼 진행 중인 작업이 갈 수 있는 곳까지 가도록 실제 시간으로 잠시 기다린다 */
private suspend fun settle() {
    yield()
    withContext(Dispatchers.Default) { delay(100) }
    yield()
}

class DeleteAccountPreChecksTest {
    @Test
    fun withoutSessionOfflineOpenedVaultRejectsWithoutSendingToServer() = sessionTest {
        val first = signedIn()
        first.authApi.offline = true
        val reopened = device(first.server, first.deviceStore)
        reopened.session.restore()
        reopened.session.unlock(PASSWORD)
        first.authApi.offline = false

        assertFailsWith<AccountDeletionOfflineException> { reopened.session.deleteAccount(PASSWORD) }
        assertEquals(0, first.authApi.deletions.size)
        assertIs<SessionState.Unlocked>(reopened.session.state())
    }

    @Test
    fun rejectsWhenLocked() = sessionTest {
        val ctx = signedIn()
        ctx.session.lock()

        assertFailsWith<SessionStateException> { ctx.session.deleteAccount(PASSWORD) }
        assertEquals(0, ctx.authApi.deletions.size)
    }

    @Test
    fun wrongPasswordDoesNotSendAndVaultStaysOpen() = sessionTest {
        val ctx = signedIn()

        assertFailsWith<WrongMasterPasswordException> { ctx.session.deleteAccount("not the master password") }
        assertEquals(0, ctx.authApi.deletions.size)
        assertIs<SessionState.Unlocked>(ctx.session.state())
        assertEquals(EMAIL, ctx.deviceStore.record?.email)
    }

    @Test
    fun canDeleteFromReadOnlyVault() = sessionTest {
        val server = newServer()
        val key: EncryptionKey = Vectors.keysOf(vector.text("name")).encryptionKey
        server.vaultApi.payload = sealVault(key, """{"version":2,"updatedAt":"2026-10-30T00:00:00Z","items":[]}""")
        server.vaultApi.revision = 1
        val ctx = signedIn(server)

        ctx.session.deleteAccount(PASSWORD)

        assertTrue(ctx.authApi.deleted)
        assertEquals(SessionState.SignedOut, ctx.session.state())
    }
}

class DeleteAccountRequestTest {
    @Test
    fun sendsAuthHashFromCurrentTokenAndAccountKdf() = sessionTest {
        val ctx = signedIn()

        ctx.session.deleteAccount(PASSWORD)

        assertEquals(listOf("token-1" to DeleteAccountRequest(expectedAuthHash())), ctx.authApi.deletions)
    }

    @Test
    fun serverRejectingAuthHashIsWrongPasswordAndSignInAndDeviceRecordStay() = sessionTest {
        val ctx = signedIn()
        ctx.authApi.deleteError = ServerError(401, ServerErrorCode.AUTH_FAILED)

        assertFailsWith<WrongMasterPasswordException> { ctx.session.deleteAccount(PASSWORD) }
        assertIs<SessionState.Unlocked>(ctx.session.state())
        assertEquals(EMAIL, ctx.deviceStore.record?.email)
    }

    @Test
    fun rateLimitIsReportedAsTooManyAttemptsAndStateStays() = sessionTest {
        val ctx = signedIn()
        ctx.authApi.deleteError = ServerError(429, ServerErrorCode.TOO_MANY_ATTEMPTS, 900)

        val error = assertFailsWith<ServerError> { ctx.session.deleteAccount(PASSWORD) }
        assertEquals(ServerErrorCode.TOO_MANY_ATTEMPTS, error.code)
        assertIs<SessionState.Unlocked>(ctx.session.state())
    }

    @Test
    fun noResponseMeansUnknownResultSoErrorIsReportedAndStateStays() = sessionTest {
        val ctx = signedIn()
        ctx.authApi.deleteError = NetworkError()

        assertFailsWith<NetworkError> { ctx.session.deleteAccount(PASSWORD) }
        assertIs<SessionState.Unlocked>(ctx.session.state())
        assertEquals(EMAIL, ctx.deviceStore.record?.email)
    }

    @Test
    fun invalidTokenSignsOutAsSessionExpired() = sessionTest {
        val ctx = signedIn()
        ctx.authApi.deleteError = ServerError(401, ServerErrorCode.UNAUTHORIZED)

        assertFailsWith<SessionExpiredException> { ctx.session.deleteAccount(PASSWORD) }
        assertEquals(SessionState.SignedOut, ctx.session.state())
        assertTrue(ctx.syncErrors.any { it is SessionExpiredException })
    }

    @Test
    fun sendsRequestAfterEarlierSaveFinishes() = sessionTest {
        val ctx = signedIn()
        val saveGate = Gate()
        ctx.vaultApi.gate = saveGate
        val saving = begin { saveItem(ctx.session, "a") }
        // 원본은 동기화 대기 표시만 기다렸다. 저장 요청이 서버 문 앞에 닿은 것까지 기다려 보내는 중인 저장이 있음을 확실히 한다
        waitFor { (ctx.session.state() as? SessionState.Unlocked)?.pendingSync == true && saveGate.waiting > 0 }

        val deleting = begin { ctx.session.deleteAccount(PASSWORD) }
        settle()
        assertEquals(0, ctx.authApi.deletions.size)
        ctx.vaultApi.gate = null
        saveGate.release()

        saving.await()
        deleting.await()
        assertEquals(1, ctx.authApi.deletions.size)
        assertEquals(SessionState.SignedOut, ctx.session.state())
    }
}

class DeleteAccountDeviceCleanupTest {
    @Test
    fun clearsDeviceRecordAndTabMemoryAndSignsOut() = sessionTest {
        val ctx = signedIn()
        assertEquals(EMAIL, ctx.deviceStore.record?.email)
        assertEquals(EMAIL, ctx.tabMemory.value?.email)

        ctx.session.deleteAccount(PASSWORD)

        assertEquals(SessionState.SignedOut, ctx.session.state())
        assertNull(ctx.deviceStore.stored)
        assertNull(ctx.tabMemory.value)
    }

    @Test
    fun doesNotReuseDeletedAccountTokenAndReopenedAppIsSignedOutNotLockScreen() = sessionTest {
        val first = signedIn()

        first.session.deleteAccount(PASSWORD)

        val reopened = device(first.server, first.deviceStore)
        reopened.session.restore()
        assertEquals(SessionState.SignedOut, reopened.session.state())
    }

    @Test
    fun signsOutOtherTabsOfSameBrowser() = sessionTest {
        val hub = InMemoryTabHub()
        val server = newServer()
        val deviceStore = FakeDeviceStore()
        val a = device(server, deviceStore, hub.connect())
        val b = device(server, deviceStore, hub.connect())
        a.session.login(EMAIL, PASSWORD)
        b.session.restore()
        b.session.unlock(PASSWORD)

        a.session.deleteAccount(PASSWORD)

        waitFor { b.session.state() == SessionState.SignedOut }
        assertNull(b.tabMemory.value)
        assertNull(deviceStore.stored)
    }

    @Test
    fun deviceRecordNotClearedMeansAccountDeletedSignedOutAndReportedAsDeviceNotCleared() = sessionTest {
        val ctx = signedIn()
        val deleting = begin { ctx.session.deleteAccount(PASSWORD) }
        ctx.deviceStore.failing = true

        assertFailsWith<AccountDeletedDeviceNotClearedException> { deleting.await() }
        assertTrue(ctx.authApi.deleted)
        assertEquals(SessionState.SignedOut, ctx.session.state())
    }

    @Test
    fun lockedWhileWaitingForRequestStillClearsRecordAndSignsOutWhenDeleted() = sessionTest {
        val ctx = signedIn()
        val gate = gateDeletes(ctx.authApi)
        val deleting = begin { ctx.session.deleteAccount(PASSWORD) }
        waitFor { gate.waiting > 0 }

        ctx.session.lock()
        releaseDeletes(ctx.authApi, gate)

        deleting.await()
        assertEquals(SessionState.SignedOut, ctx.session.state())
        assertNull(ctx.deviceStore.stored)
    }

    @Test
    fun registeringAgainWithSameEmailStartsWithEmptyVault() = sessionTest {
        val ctx = signedIn()
        saveItem(ctx.session, "a")

        ctx.session.deleteAccount(PASSWORD)
        ctx.session.register(EMAIL, PASSWORD)

        assertTrue(unlockedData(ctx.session).items.isEmpty())
    }
}

class DeleteAccountFromOtherDeviceTest {
    @Test
    fun otherDeviceGetsSessionExpiredOnNextRequestAndGoesToLogin() = sessionTest {
        val server = newServer()
        val other = signedIn(server)
        val mine = signedIn(server)

        mine.session.deleteAccount(PASSWORD)

        assertFailsWith<SessionExpiredException> { saveItem(other.session, "late") }
        assertEquals(SessionState.SignedOut, other.session.state())
        assertNull(server.vaultApi.payload)
    }

    @Test
    fun otherDeviceReopeningWithLeftoverDeviceRecordGetsSameNoticeAsPasswordChange() = sessionTest {
        val server = newServer()
        val other = signedIn(server)
        val mine = signedIn(server)

        mine.session.deleteAccount(PASSWORD)

        val reopened = device(server, other.deviceStore)
        reopened.session.restore()
        assertFailsWith<MasterPasswordChangedException> { reopened.session.unlock(PASSWORD) }
        assertEquals(SessionState.SignedOut, reopened.session.state())
    }
}

class DeleteAccountOverlappingTabLogoutTest {
    @Test
    fun deletionCompletesEvenIfOtherTabLogoutAlreadySignedOutAfterServerDeleted() = sessionTest {
        val hub = InMemoryTabHub()
        val server = newServer()
        val deviceStore = FakeDeviceStore()
        val a = device(server, deviceStore, hub.connect())
        val b = device(server, deviceStore, hub.connect())
        a.session.login(EMAIL, PASSWORD)
        b.session.restore()
        b.session.unlock(PASSWORD)
        val gate = gateDeletes(server.authApi)
        val deleting = begin { a.session.deleteAccount(PASSWORD) }
        waitFor { gate.waiting > 0 }

        b.session.logout()
        waitFor { a.session.state() == SessionState.SignedOut }
        releaseDeletes(server.authApi, gate)

        deleting.await()
        assertTrue(server.authApi.deleted)
        assertEquals(SessionState.SignedOut, a.session.state())
        assertNull(deviceStore.stored)
    }
}
