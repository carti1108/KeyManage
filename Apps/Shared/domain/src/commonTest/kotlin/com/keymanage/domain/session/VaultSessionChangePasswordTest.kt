package com.keymanage.domain.session

import com.keymanage.domain.auth.AuthService
import com.keymanage.domain.auth.KdfParams
import com.keymanage.domain.auth.MasterPasswordChangedException
import com.keymanage.domain.auth.UnlockKdf
import com.keymanage.domain.crypto.EncryptedVaultPayload
import com.keymanage.domain.crypto.EncryptionKey
import com.keymanage.domain.crypto.Vectors
import com.keymanage.domain.crypto.deriveKeys
import com.keymanage.domain.crypto.openVault
import com.keymanage.domain.crypto.sealVault
import com.keymanage.domain.crypto.text
import com.keymanage.domain.encoding.fromBase64
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.tabs.InMemoryTabHub
import com.keymanage.domain.tabs.SingleTab
import com.keymanage.domain.tabs.Tabs
import com.keymanage.domain.vault.ReadOnlyVaultException
import com.keymanage.domain.vault.RemoteVault
import com.keymanage.domain.vault.RevisionMemory
import com.keymanage.domain.vault.VaultApi
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.VaultSync
import com.keymanage.domain.vault.VaultSyncConflictException
import com.keymanage.domain.vault.activeItems
import com.keymanage.domain.vault.addItem
import com.keymanage.domain.vault.parseVaultData
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// 원본: Apps/Web/src/domain/session/vaultSession.changePassword.test.ts
// 마스터 비밀번호 변경: 새 키로 볼트를 다시 암호화해 한 요청으로 바꾸고 다른 기기·탭은 로그인 화면으로 보낸다 (SEC-004, ADR-0023)

private const val EMAIL = "user@example.com"
private const val NEW_PASSWORD = "a brand new master password"

private class ChangePasswordServer(val authApi: FakeAuthApi, val vaultApi: FakeVaultApi)

private fun newServer(): ChangePasswordServer {
    val authApi = FakeAuthApi()
    val vaultApi = FakeVaultApi()
    authApi.vaultApi = vaultApi
    return ChangePasswordServer(authApi, vaultApi)
}

/** 실제 서버처럼 폐기된 토큰의 볼트 요청을 401로 거절하고, 볼트 요청에 쓴 토큰을 기록한다 */
private fun tokenChecked(server: ChangePasswordServer, token: String, used: MutableList<String>): VaultApi = object : VaultApi {
    private fun check() {
        used += token
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
private class ChangePasswordDevice(
    val session: VaultSession,
    val server: ChangePasswordServer,
    val deviceStore: FakeDeviceStore,
    val tokens: MutableList<String>,
    val syncErrors: MutableList<Throwable?>,
) {
    val authApi: FakeAuthApi get() = server.authApi
    val vaultApi: FakeVaultApi get() = server.vaultApi
}

private fun TestScope.device(
    server: ChangePasswordServer = newServer(),
    deviceStore: FakeDeviceStore = FakeDeviceStore(),
    tabs: Tabs = SingleTab(),
): ChangePasswordDevice {
    val tokens = mutableListOf<String>()
    val syncErrors = mutableListOf<Throwable?>()
    val session = VaultSession(
        VaultSessionDeps(
            auth = AuthService(server.authApi),
            vaultSyncFor = { token: String, encryptionKey: EncryptionKey, memory: RevisionMemory ->
                VaultSync(tokenChecked(server, token, tokens), encryptionKey, clockAtNow, memory)
            },
            revisionMemory = RevisionMemory(),
            deviceStore = deviceStore,
            tabs = tabs,
            scope = backgroundScope,
        ),
    )
    session.onSyncChange { syncErrors.add(it) }
    return ChangePasswordDevice(session, server, deviceStore, tokens, syncErrors)
}

private suspend fun TestScope.signedIn(server: ChangePasswordServer = newServer()): ChangePasswordDevice {
    val ctx = device(server)
    ctx.session.login(EMAIL, PASSWORD)
    return ctx
}

/** 같은 서버·기기 저장소로 앱을 새로 연다(새로고침·재시작) */
private suspend fun TestScope.reopen(previous: ChangePasswordDevice): ChangePasswordDevice {
    val next = device(previous.server, previous.deviceStore)
    next.session.restore()
    return next
}

private fun unlockedData(session: VaultSession): VaultData =
    (session.state() as? SessionState.Unlocked)?.vault?.data ?: error("잠금 해제 상태가 아님")

private fun unlockedState(session: VaultSession): SessionState.Unlocked =
    session.state() as? SessionState.Unlocked ?: error("잠금 해제 상태가 아님: ${session.state()}")

private fun itemIds(data: VaultData): List<String> = activeItems(data).map { it.id }.sorted()

private suspend fun saveItem(session: VaultSession, id: String) {
    session.save(addItem(unlockedData(session), fields.copy(serviceName = id), NOW, id))
}

/** 서버의 KDF 값과 비밀번호로 페이로드를 열어 항목 id를 준다 */
private suspend fun openedIds(payload: EncryptedVaultPayload?, kdf: KdfParams, password: String): List<String> {
    if (payload == null) error("볼트가 없음")
    val keys = deriveKeys(password, fromBase64(kdf.salt), kdf.iterations)
    return itemIds(parseVaultData(openVault(keys.encryptionKey, payload)))
}

/** 문을 세워 비밀번호 변경 요청을 기다리게 한다. @return 문을 열고 치우는 함수 */
private fun gateChanges(authApi: FakeAuthApi): () -> Unit {
    val gate = Gate()
    authApi.changeGate = gate
    return {
        authApi.changeGate = null
        gate.release()
    }
}

/** 변경 요청이 서버(가짜)의 문 앞에 닿기를 기다린다. 원본의 `vi.waitFor(() => expect(authApi.changes).toHaveLength(1))` */
private suspend fun waitForChangeRequest(authApi: FakeAuthApi) {
    waitFor { authApi.changes.size == 1 && authApi.changeGate!!.waiting > 0 }
}

class VaultSessionChangePasswordPreCheckTest {
    @Test
    fun rejectsTooShortNewPasswordWithoutRequest() = sessionTest {
        val ctx = signedIn()

        assertFailsWith<NewMasterPasswordTooShortException> { ctx.session.changeMasterPassword(PASSWORD, "short pw 11") }
        assertEquals(0, ctx.authApi.changes.size)
    }

    @Test
    fun rejectsSamePasswordWithoutRequest() = sessionTest {
        val ctx = signedIn()

        assertFailsWith<SameMasterPasswordException> { ctx.session.changeMasterPassword(PASSWORD, PASSWORD) }
        assertEquals(0, ctx.authApi.changes.size)
    }

    @Test
    fun rejectsVaultOpenedWithoutSessionOffline() = sessionTest {
        val first = signedIn()
        first.authApi.offline = true
        val reopened = device(first.server, first.deviceStore)
        reopened.session.restore()
        reopened.session.unlock(PASSWORD)
        first.authApi.offline = false

        assertFailsWith<PasswordChangeOfflineException> { reopened.session.changeMasterPassword(PASSWORD, NEW_PASSWORD) }
        assertEquals(0, first.authApi.changes.size)
    }

    @Test
    fun rejectsReadOnlyVault() = sessionTest {
        val server = newServer()
        val encryptionKey = Vectors.keysOf(Vectors.keyCases[0].text("name")).encryptionKey
        server.vaultApi.payload = sealVault(encryptionKey, """{"version":2,"updatedAt":"2026-10-30T00:00:00Z","items":[]}""")
        server.vaultApi.revision = 1
        val ctx = signedIn(server)

        assertFailsWith<ReadOnlyVaultException> { ctx.session.changeMasterPassword(PASSWORD, NEW_PASSWORD) }
        assertEquals(0, ctx.authApi.changes.size)
    }

    @Test
    fun rejectsWhenLocked() = sessionTest {
        val ctx = signedIn()
        ctx.session.lock()

        assertFailsWith<SessionStateException> { ctx.session.changeMasterPassword(PASSWORD, NEW_PASSWORD) }
    }

    @Test
    fun wrongCurrentPasswordIsRejectedWithoutRequest() = sessionTest {
        val ctx = signedIn()

        assertFailsWith<WrongMasterPasswordException> { ctx.session.changeMasterPassword("not the password", NEW_PASSWORD) }
        assertEquals(0, ctx.authApi.changes.size)
    }
}

class VaultSessionChangePasswordRequestTest {
    @Test
    fun sendsNewKeysAndReEncryptedVaultInOneRequest() = sessionTest {
        val ctx = signedIn()
        val oldKdf = ctx.authApi.kdf
        val oldHash = ctx.authApi.authHash
        saveItem(ctx.session, "a")

        ctx.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

        val (accessToken, request) = ctx.authApi.changes.first()
        assertEquals("token-1", accessToken)
        assertEquals(oldHash, request.currentAuthHash)
        assertNotEquals(oldHash, request.newAuthHash)
        assertEquals(16, fromBase64(request.kdf.salt).size)
        assertNotEquals(oldKdf.salt, request.kdf.salt)
        assertEquals(oldKdf.iterations, request.kdf.iterations)
        assertEquals(2, request.vault.expectedRevision)
        assertEquals(3, ctx.vaultApi.revision)
        assertEquals(listOf("a"), openedIds(ctx.vaultApi.payload, ctx.authApi.kdf, NEW_PASSWORD))
    }

    @Test
    fun reEncryptsPendingChangesAndClearsPendingFlag() = sessionTest {
        val ctx = signedIn()
        ctx.vaultApi.offline = true
        saveItem(ctx.session, "pending")
        ctx.vaultApi.offline = false
        val saves = ctx.vaultApi.saves

        ctx.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

        assertEquals(listOf("pending"), openedIds(ctx.vaultApi.payload, ctx.authApi.kdf, NEW_PASSWORD))
        assertEquals(saves, ctx.vaultApi.saves)
        assertEquals(false, unlockedState(ctx.session).pendingSync)
    }

    @Test
    fun serverRejectingCurrentAuthHashKeepsOldPassword() = sessionTest {
        val ctx = signedIn()
        ctx.authApi.changeError = ServerError(401, ServerErrorCode.AUTH_FAILED)

        assertFailsWith<WrongMasterPasswordException> { ctx.session.changeMasterPassword(PASSWORD, NEW_PASSWORD) }

        assertIs<SessionState.Unlocked>(ctx.session.state())
        ctx.session.lock()
        ctx.session.unlock(PASSWORD)
    }

    @Test
    fun conflictFromOtherDeviceChangesNothing() = sessionTest {
        val ctx = signedIn()
        val kdfBefore = ctx.deviceStore.record?.kdf
        ctx.vaultApi.revision += 1

        assertFailsWith<VaultSyncConflictException> { ctx.session.changeMasterPassword(PASSWORD, NEW_PASSWORD) }

        assertEquals(Vectors.keyCases[0].text("salt"), ctx.authApi.kdf.salt)
        assertEquals(kdfBefore, ctx.deviceStore.record?.kdf)
        ctx.session.lock()
        ctx.session.unlock(PASSWORD)
    }

    @Test
    fun rateLimitIsReportedAsServerError() = sessionTest {
        val ctx = signedIn()
        ctx.authApi.changeError = ServerError(429, ServerErrorCode.TOO_MANY_ATTEMPTS, 900)

        val failure = assertFailsWith<ServerError> { ctx.session.changeMasterPassword(PASSWORD, NEW_PASSWORD) }

        assertEquals(ServerErrorCode.TOO_MANY_ATTEMPTS, failure.code)
        assertIs<SessionState.Unlocked>(ctx.session.state())
    }

    @Test
    fun expiredSessionSignsOut() = sessionTest {
        val ctx = signedIn()
        ctx.authApi.changeError = ServerError(401, ServerErrorCode.UNAUTHORIZED)

        assertFailsWith<SessionExpiredException> { ctx.session.changeMasterPassword(PASSWORD, NEW_PASSWORD) }
        assertEquals(SessionState.SignedOut, ctx.session.state())
    }
}

class VaultSessionChangePasswordAfterTest {
    @Test
    fun switchesToNewTokenForLaterRequests() = sessionTest {
        val ctx = signedIn()
        ctx.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)
        ctx.tokens.clear()

        saveItem(ctx.session, "after")

        assertEquals(listOf("token-2"), ctx.tokens)
        val state = unlockedState(ctx.session)
        assertEquals(false, state.pendingSync)
        assertTrue(state.signedIn)
    }

    @Test
    fun unlocksWithNewPasswordAndRejectsOldAfterLock() = sessionTest {
        val ctx = signedIn()
        saveItem(ctx.session, "a")
        ctx.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)
        ctx.session.lock()

        assertFailsWith<WrongMasterPasswordException> { ctx.session.unlock(PASSWORD) }
        ctx.session.unlock(NEW_PASSWORD)

        assertEquals(listOf("a"), itemIds(unlockedData(ctx.session)))
    }

    @Test
    fun deviceRecordHoldsNewKdfAndOpensWithNewPasswordAfterReload() = sessionTest {
        val first = signedIn()
        saveItem(first.session, "a")
        first.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

        assertEquals(UnlockKdf(first.authApi.kdf.salt, first.authApi.kdf.iterations), first.deviceStore.record?.kdf)
        assertEquals(false, first.deviceStore.record?.pendingSync)
        val reopened = reopen(first)
        reopened.session.unlock(NEW_PASSWORD)
        assertEquals(listOf("a"), itemIds(unlockedData(reopened.session)))
    }

    @Test
    fun saveDuringRequestStaysPendingWithNewKeyAndIsSentNext() = sessionTest {
        val ctx = signedIn()
        val release = gateChanges(ctx.authApi)
        val changing = begin { ctx.session.changeMasterPassword(PASSWORD, NEW_PASSWORD) }
        waitForChangeRequest(ctx.authApi)
        ctx.vaultApi.offline = true
        // 저장의 서버 전송은 변경이 끝난 뒤로 줄을 서므로 기다리지 않고, 이 탭에 반영될 때까지만 기다린다
        val saving = begin { saveItem(ctx.session, "during") }
        waitFor { activeIds(ctx.session.state()) == listOf("during") }

        release()
        changing.await()
        saving.await()
        assertEquals(true, unlockedState(ctx.session).pendingSync)
        ctx.vaultApi.offline = false
        ctx.session.retryPendingSync()

        assertEquals(listOf("during"), openedIds(ctx.vaultApi.payload, ctx.authApi.kdf, NEW_PASSWORD))
        assertEquals(false, unlockedState(ctx.session).pendingSync)
    }

    @Test
    fun saveDuringRequestDoesNotSignOutAndUploadsWithNewKeyAndToken() = sessionTest {
        val ctx = signedIn()
        val release = gateChanges(ctx.authApi)
        val changing = begin { ctx.session.changeMasterPassword(PASSWORD, NEW_PASSWORD) }
        waitForChangeRequest(ctx.authApi)
        ctx.tokens.clear()
        val saving = begin { saveItem(ctx.session, "during") }
        waitFor { activeIds(ctx.session.state()) == listOf("during") }

        release()
        changing.await()
        saving.await()

        assertEquals(emptyList(), ctx.syncErrors.filterIsInstance<SessionExpiredException>())
        assertTrue("token-1" !in ctx.tokens)
        val state = unlockedState(ctx.session)
        assertTrue(state.signedIn)
        assertEquals(false, state.pendingSync)
        assertEquals(listOf("during"), openedIds(ctx.vaultApi.payload, ctx.authApi.kdf, NEW_PASSWORD))
    }

    @Test
    fun lockDuringRequestAcceptsNewPasswordWhileLocked() = sessionTest {
        val ctx = signedIn()
        val release = gateChanges(ctx.authApi)
        val changing = begin { ctx.session.changeMasterPassword(PASSWORD, NEW_PASSWORD) }
        waitForChangeRequest(ctx.authApi)
        ctx.session.lock()

        release()
        runCatching { changing.await() }

        assertIs<SessionState.Locked>(ctx.session.state())
        assertFailsWith<WrongMasterPasswordException> { ctx.session.unlock(PASSWORD) }
        ctx.session.unlock(NEW_PASSWORD)
    }

    @Test
    fun otherDeviceGetsSessionExpiredAndPasswordChangedNoticeOnReopen() = sessionTest {
        val server = newServer()
        val other = signedIn(server)
        val mine = signedIn(server)
        other.server.vaultApi.offline = true
        saveItem(other.session, "unsynced")
        other.server.vaultApi.offline = false

        mine.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

        other.session.retryPendingSync()
        assertTrue(other.syncErrors.any { it is SessionExpiredException })
        assertEquals(SessionState.SignedOut, other.session.state())
        val reopened = device(server, other.deviceStore)
        reopened.session.restore()
        assertFailsWith<MasterPasswordChangedException> { reopened.session.unlock(PASSWORD) }
        assertEquals(emptyList(), openedIds(server.vaultApi.payload, server.authApi.kdf, NEW_PASSWORD))
    }

    @Test
    fun otherTabInSameBrowserGoesToSignInAndDoesNotOverwriteRecordWithOldKdf() = sessionTest {
        val hub = InMemoryTabHub()
        val server = newServer()
        val deviceStore = FakeDeviceStore()
        val a = device(server, deviceStore, hub.connect())
        val b = device(server, deviceStore, hub.connect())
        a.session.login(EMAIL, PASSWORD)
        b.session.restore()
        b.session.unlock(PASSWORD)

        a.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

        waitFor { b.session.state() == SessionState.SignedOut }
        assertTrue(b.syncErrors.any { it is MasterPasswordChangedException })
        assertEquals(server.authApi.kdf.salt, deviceStore.record?.kdf?.salt)
        saveItem(a.session, "a")
        assertEquals(server.authApi.kdf.salt, deviceStore.record?.kdf?.salt)
        assertEquals(listOf("a"), openedIds(deviceStore.record?.payload, server.authApi.kdf, NEW_PASSWORD))
    }
}
