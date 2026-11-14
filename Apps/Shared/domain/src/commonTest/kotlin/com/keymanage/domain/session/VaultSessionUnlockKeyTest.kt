package com.keymanage.domain.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 생체 해제용 키 (SEC-003, ADR-0027). 비밀번호로 연 직후 암호화 키를 한 번 꺼내고, 그 키로 비밀번호 없이 연다.
// 원본: iOS VaultLockManager.unlockWithBiometrics(recoveredEncryptionKey:)
class VaultSessionUnlockKeyTest {
    @Test
    fun passwordUnlockExportsKeyOnlyWhenAsked() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()

        val plain = ctx.session.unlock(PASSWORD)
        ctx.session.lock()
        val exported = ctx.session.unlock(PASSWORD, exportUnlockKey = true)

        assertNull(plain.unlockKey)
        assertEquals(32, assertNotNull(exported.unlockKey).size)
    }

    @Test
    fun exportedKeyUnlocksWithoutPassword() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()
        val key = assertNotNull(ctx.session.unlock(PASSWORD, exportUnlockKey = true).unlockKey)
        ctx.session.lock()

        val result = ctx.session.unlockWithKey(key)

        assertEquals(VaultSession.UnlockResult(offline = false), result)
        assertEquals(listOf("a"), activeIds(ctx.session.state()))
    }

    @Test
    fun callerMayWipeKeyAfterUnlock() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()
        val key = assertNotNull(ctx.session.unlock(PASSWORD, exportUnlockKey = true).unlockKey)
        ctx.session.lock()
        ctx.session.unlockWithKey(key)
        key.fill(0)

        // 세션은 키를 복사해 쓰므로 부른 쪽이 지워도 저장은 그대로 된다
        ctx.session.save(ctx.unlockedState().vault.data)
        assertIs<SessionState.Unlocked>(ctx.session.state())
    }

    @Test
    fun keyUnlockWorksOffline() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()
        val key = assertNotNull(ctx.session.unlock(PASSWORD, exportUnlockKey = true).unlockKey)
        ctx.session.lock()
        ctx.vaultApi.offline = true

        assertEquals(VaultSession.UnlockResult(offline = true), ctx.session.unlockWithKey(key))
    }

    @Test
    fun keyUnlockAfterRestartOpensWithoutSignIn() = sessionTest {
        val first = unlockedWithItem()
        first.session.lock()
        val key = assertNotNull(first.session.unlock(PASSWORD, exportUnlockKey = true).unlockKey)
        val loginsBefore = first.authApi.logins.size

        val next = restart(first)
        val result = next.session.unlockWithKey(key)

        // 인증 해시가 없으므로 로그인하지 않고 세션 없이 연다. 동기화는 비밀번호로 재연결할 때 한다 (ADR-0019)
        assertEquals(VaultSession.UnlockResult(offline = true), result)
        assertEquals(false, next.unlockedState().signedIn)
        assertEquals(loginsBefore, first.authApi.logins.size)
    }

    @Test
    fun wrongKeyIsRejectedAndStaysLocked() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()

        assertFailsWith<StaleUnlockKeyException> { ctx.session.unlockWithKey(ByteArray(32) { 7 }) }
        assertIs<SessionState.Locked>(ctx.session.state())
    }

    @Test
    fun malformedKeyIsRejected() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()

        assertFailsWith<StaleUnlockKeyException> { ctx.session.unlockWithKey(ByteArray(5)) }
    }

    @Test
    fun keyFromBeforePasswordChangeNoLongerOpens() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()
        val oldKey = assertNotNull(ctx.session.unlock(PASSWORD, exportUnlockKey = true).unlockKey)
        ctx.session.changeMasterPassword(PASSWORD, "a brand new master password")
        ctx.session.lock()

        assertFailsWith<StaleUnlockKeyException> { ctx.session.unlockWithKey(oldKey) }
    }

    @Test
    fun keyUnlockBeforeSignInIsRejected() = sessionTest {
        val ctx = setup()

        assertFailsWith<SessionStateException> { ctx.session.unlockWithKey(ByteArray(32)) }
    }

    @Test
    fun keyUnlockNotifiesUnlockListeners() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()
        val key = assertNotNull(ctx.session.unlock(PASSWORD, exportUnlockKey = true).unlockKey)
        ctx.session.lock()
        val before = ctx.unlocks()

        ctx.session.unlockWithKey(key)

        assertTrue(ctx.unlocks() > before)
    }
}

// 열지 못하고 끝난 잠금 해제는 세션이 만든 암호화 키 사본을 지운다 (ADR-0027)
class VaultSessionUnlockKeyWipeTest {
    private suspend fun kotlinx.coroutines.test.TestScope.lockedWithKeySpy(): Pair<SessionSetup, MutableList<com.keymanage.domain.crypto.EncryptionKey>> {
        val keys = mutableListOf<com.keymanage.domain.crypto.EncryptionKey>()
        val vaultApi = FakeVaultApi()
        val base = vaultSyncFor(vaultApi)
        val ctx = setup(vaultApi = vaultApi, syncFor = { token, key, memory -> keys += key; base(token, key, memory) })
        ctx.session.login("user@example.com", PASSWORD)
        ctx.session.save(com.keymanage.domain.vault.addItem(ctx.unlockedState().vault.data, fields, NOW, "a"))
        ctx.session.lock()
        keys.clear()
        // 서버를 백업(revision 1)에서 되돌린 상황. 되돌림 확인 전에는 열지 않는다
        val restored = com.keymanage.domain.vault.RemoteVault(1, ctx.vaultApi.payload!!)
        ctx.vaultApi.getHook = { restored }
        return ctx to keys
    }

    @Test
    fun failedKeyUnlockWipesSessionKeyCopy() = sessionTest {
        val (ctx, keys) = lockedWithKeySpy()
        ctx.vaultApi.getHook = null
        val key = assertNotNull(ctx.session.unlock(PASSWORD, exportUnlockKey = true).unlockKey)
        ctx.session.lock()
        keys.clear()
        ctx.vaultApi.getHook = { com.keymanage.domain.vault.RemoteVault(1, ctx.vaultApi.payload!!) }

        assertFailsWith<com.keymanage.domain.vault.RevisionRollbackException> { ctx.session.unlockWithKey(key) }

        assertTrue(keys.isNotEmpty())
        assertTrue(keys.all { k -> k.bytes.all { it == 0.toByte() } })
        // 부른 쪽 키는 되돌림 확인에 다시 쓰므로 그대로 둔다
        assertTrue(key.any { it != 0.toByte() })
    }

    @Test
    fun failedPasswordUnlockWipesDerivedKey() = sessionTest {
        val (ctx, keys) = lockedWithKeySpy()

        assertFailsWith<com.keymanage.domain.vault.RevisionRollbackException> { ctx.session.unlock(PASSWORD) }

        assertTrue(keys.isNotEmpty())
        assertTrue(keys.all { k -> k.bytes.all { it == 0.toByte() } })
    }

    @Test
    fun successfulUnlockKeepsKeyForSession() = sessionTest {
        val (ctx, keys) = lockedWithKeySpy()
        ctx.vaultApi.getHook = null

        ctx.session.unlock(PASSWORD)

        assertTrue(keys.last().bytes.any { it != 0.toByte() })
        ctx.session.save(ctx.unlockedState().vault.data)
    }
}
