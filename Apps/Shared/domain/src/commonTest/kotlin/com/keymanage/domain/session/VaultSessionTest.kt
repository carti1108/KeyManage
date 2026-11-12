package com.keymanage.domain.session

import com.keymanage.domain.auth.AuthService
import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.server.UnexpectedResponseError
import com.keymanage.domain.tabs.SingleTab
import com.keymanage.domain.vault.RemoteVault
import com.keymanage.domain.vault.RevisionMemory
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.domain.vault.addItem
import com.keymanage.domain.vault.emptyVault
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

// 원본: Apps/Web/src/domain/session/vaultSession.test.ts

class VaultSessionVaultExistsTest {
    @Test
    fun registerSavesEmptyVaultAndUnlocks() = sessionTest {
        val ctx = setup()

        ctx.session.register("new@example.com", PASSWORD)

        assertEquals(1, ctx.vaultApi.saves)
        val state = ctx.unlockedState()
        assertEquals("new@example.com", state.email)
        assertEquals(1, state.vault.revision)
    }

    @Test
    fun loginWithoutVaultSavesEmptyVaultBeforeOpening() = sessionTest {
        val ctx = setup()

        ctx.session.login("user@example.com", PASSWORD)

        assertEquals(1, ctx.vaultApi.saves)
        assertEquals(1, ctx.unlockedState().vault.revision)
    }

    @Test
    fun existingVaultOpensAsIs() = sessionTest {
        val vaultApi = unlockedWithItem().vaultApi
        val other = VaultSession(
            VaultSessionDeps(
                auth = AuthService(FakeAuthApi()),
                vaultSyncFor = vaultSyncFor(vaultApi),
                revisionMemory = RevisionMemory(),
                deviceStore = FakeDeviceStore(),
                tabs = SingleTab(),
                scope = backgroundScope,
            ),
        )

        other.login("user@example.com", PASSWORD)

        assertEquals(2, vaultApi.saves)
        assertEquals(listOf("a"), activeIds(other.state()))
    }
}

class VaultSessionLockTest {
    @Test
    fun lockDropsVaultAndNotifies() = sessionTest {
        val ctx = unlockedWithItem()

        ctx.session.lock()

        assertEquals(SessionState.Locked("user@example.com", pendingSync = false), ctx.session.state())
        assertEquals(1, ctx.locks())
    }

    @Test
    fun rightPasswordUnlocksOfflineWithStoredVault() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()
        ctx.vaultApi.offline = true

        val result = ctx.session.unlock(PASSWORD)

        assertEquals(VaultSession.UnlockResult(offline = true), result)
        assertEquals(listOf("a"), activeIds(ctx.session.state()))
    }

    @Test
    fun onlineUnlockFetchesServerVault() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()
        val before = ctx.vaultApi.saves

        assertEquals(VaultSession.UnlockResult(offline = false), ctx.session.unlock(PASSWORD))
        assertEquals(before, ctx.vaultApi.saves)
        assertEquals(2, ctx.unlockedState().vault.revision)
    }

    @Test
    fun wrongPasswordKeepsLocked() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()

        assertFailsWith<WrongMasterPasswordException> { ctx.session.unlock("wrong password") }
        assertIs<SessionState.Locked>(ctx.session.state())
    }

    @Test
    fun canSaveAfterUnlock() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()
        ctx.session.unlock(PASSWORD)

        ctx.session.save(addItem(ctx.unlockedState().vault.data, fields, NOW, "b"))

        assertEquals(3, ctx.vaultApi.revision)
    }

    @Test
    fun cannotSaveWhenLockedAndRepeatedLockNotifiesOnce() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()
        ctx.session.lock()

        assertEquals(1, ctx.locks())
        assertFailsWith<SessionStateException> { ctx.session.save(emptyVault(NOW)) }
        setup().session.lock()
    }
}

class VaultSessionAutoLockNotificationTest {
    @Test
    fun everyOpenNotifiesUnlock() = sessionTest {
        val ctx = unlockedWithItem()
        assertEquals(1, ctx.unlocks())

        ctx.session.lock()
        ctx.session.unlock(PASSWORD)
        assertEquals(2, ctx.unlocks())

        val fresh = setup()
        fresh.session.register("new@example.com", PASSWORD)
        assertEquals(1, fresh.unlocks())
    }

    @Test
    fun saveDoesNotNotifyUnlockAgain() = sessionTest {
        assertEquals(1, unlockedWithItem().unlocks())
    }
}

class VaultSessionUnlockRollbackTest {
    @Test
    fun rollbackKeepsLockedUntilAccepted() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()
        // 서버를 백업(revision 1)에서 되돌린 상황
        val restored = RemoteVault(1, ctx.vaultApi.payload!!)
        ctx.vaultApi.getHook = { restored }

        assertFailsWith<RevisionRollbackException> { ctx.session.unlock(PASSWORD) }
        assertIs<SessionState.Locked>(ctx.session.state())

        ctx.session.unlock(PASSWORD, acceptRollback = true)
        assertEquals(1, ctx.unlockedState().vault.revision)
    }
}

class VaultSessionLogoutTest {
    @Test
    fun logoutWhileUnlockedNotifiesLock() = sessionTest {
        val ctx = unlockedWithItem()

        ctx.session.logout()

        assertEquals(1, ctx.locks())
    }

    @Test
    fun logoutRevokesTokenAndSignsOut() = sessionTest {
        val ctx = unlockedWithItem()

        ctx.session.logout()

        assertEquals(listOf("token-1"), ctx.authApi.loggedOut)
        assertEquals(SessionState.SignedOut, ctx.session.state())
    }

    @Test
    fun logoutSignsOutLocallyEvenOffline() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.authApi.logoutHook = { throw NetworkError() }

        assertFailsWith<NetworkError> { ctx.session.logout() }
        assertEquals(SessionState.SignedOut, ctx.session.state())
    }
}

class VaultSessionOverlapTest {
    @Test
    fun lockWhileSavingDoesNotReopen() = sessionTest {
        val ctx = unlockedWithItem()
        val data = ctx.unlockedState().vault.data
        val release = gateSaves(ctx.vaultApi)

        val saving = begin { ctx.session.save(addItem(data, fields, NOW, "b")) }
        // 원본은 암호화를 기다리는 사이 잠갔다. JVM의 암호화는 중단하지 않아 대상마다 끼어드는 곳이 달라지므로, 저장 요청이 서버에 닿은 뒤 잠근다
        waitFor { ctx.vaultApi.gate!!.waiting > 0 }
        ctx.session.lock()
        release()

        saving.await()
        assertIs<SessionState.Locked>(ctx.session.state())
        assertEquals(1, ctx.unlocks())
    }

    @Test
    fun lockOrLogoutWhileUnlockingDoesNotOpen() = sessionTest {
        val locked = unlockedWithItem()
        locked.session.lock()
        val unlocking = begin { locked.session.unlock(PASSWORD) }
        locked.session.lock()
        assertFailsWith<SessionStateException> { unlocking.await() }
        assertIs<SessionState.Locked>(locked.session.state())

        val out = unlockedWithItem()
        out.session.lock()
        val unlocking2 = begin { out.session.unlock(PASSWORD) }
        out.session.logout()
        assertFailsWith<SessionStateException> { unlocking2.await() }
        assertEquals(SessionState.SignedOut, out.session.state())
    }

    @Test
    fun logoutWhileLoggingInDoesNotRevive() = sessionTest {
        val ctx = setup()
        val release = gateSaves(ctx.vaultApi)

        val loggingIn = begin { ctx.session.login("user@example.com", PASSWORD) }
        waitFor { ctx.vaultApi.gate!!.waiting > 0 }
        ctx.session.logout()
        release()

        assertFailsWith<SessionStateException> { loggingIn.await() }
        assertEquals(SessionState.SignedOut, ctx.session.state())
    }
}

class VaultSessionAccountSwitchTest {
    @Test
    fun logoutClearsRollbackBaseline() = sessionTest {
        val ctx = unlockedWithItem()
        assertEquals(2, ctx.revisionMemory.get())

        ctx.session.logout()

        assertEquals(0, ctx.revisionMemory.get())
    }

    @Test
    fun loginRollbackIsReportedThenAccepted() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()
        ctx.revisionMemory.raise(5)

        assertFailsWith<RevisionRollbackException> { ctx.session.login("user@example.com", PASSWORD) }
        ctx.session.login("user@example.com", PASSWORD, acceptRollback = true)

        assertEquals(ctx.vaultApi.revision, ctx.unlockedState().vault.revision)
    }
}

class VaultSessionUnlockSyncFailureTest {
    @Test
    fun unauthorizedSignsOut() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()
        ctx.vaultApi.getHook = { throw ServerError(401, ServerErrorCode.UNAUTHORIZED) }

        assertFailsWith<SessionExpiredException> { ctx.session.unlock(PASSWORD) }
        assertEquals(SessionState.SignedOut, ctx.session.state())
    }

    @Test
    fun serverOrUnexpectedErrorOpensStoredVault() = sessionTest {
        for (failure in listOf(ServerError(503, ServerErrorCode.UNKNOWN), UnexpectedResponseError("형식 오류"))) {
            val ctx = unlockedWithItem()
            ctx.session.lock()
            ctx.vaultApi.getHook = { throw failure }

            assertEquals(VaultSession.UnlockResult(offline = true), ctx.session.unlock(PASSWORD))
            assertIs<SessionState.Unlocked>(ctx.session.state())
        }
    }

    @Test
    fun acceptedRollbackWithoutServerVaultUploadsStoredVault() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()
        ctx.vaultApi.payload = null
        ctx.vaultApi.revision = 0

        assertFailsWith<RevisionRollbackException> { ctx.session.unlock(PASSWORD) }
        ctx.session.unlock(PASSWORD, acceptRollback = true)

        assertEquals(listOf("a"), activeIds(ctx.session.state()))
        assertEquals(1, ctx.vaultApi.revision)
    }
}

class VaultSessionRegisterSetupFailureTest {
    @Test
    fun registerSucceedsButVaultSetupFailureIsDistinct() = sessionTest {
        val ctx = setup()
        ctx.vaultApi.offline = true

        assertFailsWith<VaultSetupException> { ctx.session.register("new@example.com", PASSWORD) }
        assertEquals(1, ctx.authApi.registered.size)
        assertEquals(SessionState.SignedOut, ctx.session.state())
    }
}
