package com.keymanage.domain.session

import com.keymanage.domain.auth.AuthService
import com.keymanage.domain.crypto.EncryptionKey
import com.keymanage.domain.crypto.EncryptionKeyWipedException
import com.keymanage.domain.tabs.SingleTab
import com.keymanage.domain.vault.RevisionMemory
import com.keymanage.domain.vault.VaultSync
import com.keymanage.domain.vault.addItem
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 잠그면 메모리의 암호화 키를 지운다 (SEC-003 규칙 1·6)

private const val EMAIL = "user@example.com"

/** 세션이 동기화 객체를 만들 때 받은 키를 모아 둔다. 세션이 쥔 것과 같은 객체다 */
private class KeyCapture {
    val authApi = FakeAuthApi()
    val vaultApi = FakeVaultApi()
    val deviceStore = FakeDeviceStore()
    val keys = mutableListOf<EncryptionKey>()
    val syncErrors = mutableListOf<Throwable?>()

    init {
        authApi.vaultApi = vaultApi
    }

    fun start(scope: TestScope): VaultSession = VaultSession(
        VaultSessionDeps(
            auth = AuthService(authApi),
            vaultSyncFor = { _, encryptionKey, memory ->
                keys += encryptionKey
                VaultSync(vaultApi, encryptionKey, clockAtNow, memory)
            },
            revisionMemory = RevisionMemory(),
            deviceStore = deviceStore,
            tabs = SingleTab(),
            scope = scope.backgroundScope,
        ),
    ).also { session -> session.onSyncChange { syncErrors += it } }
}

private fun EncryptionKey.isWiped(): Boolean = runCatching { bytes }.exceptionOrNull() is EncryptionKeyWipedException

private fun unlockedData(session: VaultSession) =
    (session.state() as? SessionState.Unlocked ?: error("잠금 해제 상태가 아님")).vault.data

class VaultSessionKeyWipeTest {
    @Test
    fun lockWipesTheKeyInMemory() = sessionTest {
        val capture = KeyCapture()
        val session = capture.start(this)
        session.login(EMAIL, PASSWORD)
        val key = capture.keys.last()
        val material = key.bytes
        assertTrue(material.any { it != 0.toByte() })

        session.lock()

        assertTrue(key.isWiped())
        assertTrue(material.all { it == 0.toByte() }, "키 바이트를 0으로 덮는다")
    }

    @Test
    fun logoutWipesTheKeyInMemory() = sessionTest {
        val capture = KeyCapture()
        val session = capture.start(this)
        session.login(EMAIL, PASSWORD)
        val key = capture.keys.last()

        session.logout()

        assertTrue(key.isWiped())
    }

    @Test
    fun theKeyStaysWhileTheVaultIsOpen() = sessionTest {
        val capture = KeyCapture()
        val session = capture.start(this)
        session.login(EMAIL, PASSWORD)

        session.save(addItem(unlockedData(session), fields, NOW, "a"))

        assertFalse(capture.keys.last().isWiped())
    }

    @Test
    fun unlockingAgainUsesAFreshKeyAndOpensTheVault() = sessionTest {
        val capture = KeyCapture()
        val session = capture.start(this)
        session.login(EMAIL, PASSWORD)
        session.save(addItem(unlockedData(session), fields, NOW, "a"))
        val wiped = capture.keys.last()
        session.lock()

        session.unlock(PASSWORD)

        assertTrue(wiped.isWiped())
        assertFalse(capture.keys.last().isWiped())
        assertEquals(listOf("a"), activeIds(session.state()))
    }

    @Test
    fun aWipedKeyCannotSealOrOpen() = sessionTest {
        val capture = KeyCapture()
        val session = capture.start(this)
        session.login(EMAIL, PASSWORD)
        val key = capture.keys.last()
        val sync = VaultSync(capture.vaultApi, key, clockAtNow, RevisionMemory())
        val payload = sync.seal(unlockedData(session))

        session.lock()

        // 0으로 덮은 키로 암호화해 서버에 올리는 일이 없어야 한다
        assertFailsWith<EncryptionKeyWipedException> { sync.seal(parseEmptyVault()) }
        assertFailsWith<EncryptionKeyWipedException> { sync.open(payload) }
    }

    @Test
    fun lockingWhileAPendingChangeIsBeingSentKeepsItPendingWithoutAnError() = sessionTest {
        val capture = KeyCapture()
        val session = capture.start(this)
        session.login(EMAIL, PASSWORD)
        // 서버가 앞서 있어, 보내는 도중 병합해 다시 암호화해야 하는 상황을 만든다
        saveFromOtherDevice(capture.vaultApi, "b")
        val release = gateSaves(capture.vaultApi)
        val saving = begin { session.save(addItem(unlockedData(session), fields, NOW, "a")) }
        waitFor { capture.vaultApi.gate?.waiting == 1 }

        session.lock()
        release()
        runCatching { saving.await() }

        assertIs<SessionState.Locked>(session.state())
        assertTrue(capture.syncErrors.filterNotNull().isEmpty(), "잠금으로 멈춘 보내기는 오류로 알리지 않는다: ${capture.syncErrors}")
        assertEquals(true, capture.deviceStore.record?.pendingSync, "보내지 못한 변경은 기기에 대기로 남는다")

        session.unlock(PASSWORD)
        assertEquals(listOf("a", "b"), activeIds(session.state())?.sorted())
        assertNull(capture.syncErrors.lastOrNull())
    }
}

private fun parseEmptyVault() = com.keymanage.domain.vault.emptyVault(NOW)
