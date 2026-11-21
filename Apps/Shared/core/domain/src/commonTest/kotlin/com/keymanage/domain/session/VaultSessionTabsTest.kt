package com.keymanage.domain.session

import com.keymanage.domain.auth.AuthService
import com.keymanage.domain.crypto.deriveKeys
import com.keymanage.domain.crypto.openVault
import com.keymanage.domain.device.DeviceRecord
import com.keymanage.domain.device.DeviceRecordStore
import com.keymanage.domain.device.encodeDeviceRecord
import com.keymanage.domain.encoding.fromBase64
import com.keymanage.domain.tabs.InMemoryTabHub
import com.keymanage.domain.tabs.SingleTab
import com.keymanage.domain.tabs.TabChannel
import com.keymanage.domain.tabs.TabMessage
import com.keymanage.domain.tabs.Tabs
import com.keymanage.domain.vault.RevisionMemory
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.activeItems
import com.keymanage.domain.vault.addItem
import com.keymanage.domain.vault.parseVaultData
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 같은 계정을 연 여러 탭이 기기 기록을 덮어쓰지 않고 맞춘다 (ADR-WEB-001)
// 원본: Apps/Web/src/domain/session/vaultSession.tabs.test.ts

private const val TABS_EMAIL = "user@example.com"

private fun tabsUnlockedData(session: VaultSession): VaultData {
    val state = session.state()
    check(state is SessionState.Unlocked) { "잠금 해제 상태가 아님" }
    return state.vault.data
}

private fun tabsItemIds(data: VaultData): List<String> = activeItems(data).map { it.id }.sorted()

private suspend fun tabsSaveItem(session: VaultSession, id: String) {
    session.save(addItem(tabsUnlockedData(session), fields.copy(serviceName = id), NOW, id))
}

/** 기기 기록의 볼트를 마스터 비밀번호로 열어 살아 있는 항목 id를 준다 */
private suspend fun tabsRecordItemIds(record: DeviceRecord?): List<String> {
    checkNotNull(record) { "기기 기록이 없음" }
    val keys = deriveKeys(PASSWORD, fromBase64(record.kdf.salt), record.kdf.iterations)
    keys.authHash.fill(0)
    return tabsItemIds(parseVaultData(openVault(keys.encryptionKey, record.payload)))
}

/**
 * 다른 탭이 보낸 메시지의 처리(복호화 등)가 끝나기를 잠깐 기다린다. 원본은 50ms 기다렸다.
 * 가상 시간의 대기 작업을 모두 돌리고, JS 대상의 실제 이벤트 루프 작업을 위해 실제 시간도 잠깐 기다린다
 */
private suspend fun TestScope.tabsSettle() {
    testScheduler.advanceUntilIdle()
    withContext(Dispatchers.Default) { delay(50) }
    testScheduler.advanceUntilIdle()
}

/** 원본의 `.catch(() => undefined)` */
private suspend fun <T> Deferred<T>.tabsAwaitIgnoringError() {
    try {
        await()
    } catch (_: Exception) {
        // 실패해도 이 테스트가 보려는 것은 아니다
    }
}

/** 같은 서버·기기 저장소·탭 허브를 쓰는 두 탭 */
private class TabsOfTwo(val hub: InMemoryTabHub, val a: SessionSetup, val b: SessionSetup) {
    val vaultApi get() = a.vaultApi
    val deviceStore get() = a.deviceStore
}

/** A가 로그인한 뒤 B를 열어 잠금을 푼다 */
private suspend fun TestScope.tabsTwoUnlocked(): TabsOfTwo {
    val hub = InMemoryTabHub()
    val a = setup(tabs = hub.connect())
    val b = setup(authApi = a.authApi, vaultApi = a.vaultApi, deviceStore = a.deviceStore, tabs = hub.connect())
    a.session.login(TABS_EMAIL, PASSWORD)
    b.session.restore()
    b.session.unlock(PASSWORD)
    // 원본은 Promise 순서로 자연히 정리된 뒤였다. 여기서는 열기 때문에 다른 탭이 띄운 기기 기록 갱신이 테스트 본문과 섞이지 않도록 끝내 둔다
    tabsSettle()
    return TabsOfTwo(hub, a, b)
}

class VaultSessionTabsDeviceWriteTest {
    @Test
    fun keepsPendingContentOfBothTabs() = sessionTest {
        val tabs = tabsTwoUnlocked()
        tabs.vaultApi.offline = true

        tabsSaveItem(tabs.a.session, "a")
        // 원본은 Promise 순서상 다른 탭의 기록 병합(복호화)이 다음 저장 전에 끝나 있었다. JS 대상은 복호화가 실제 이벤트 루프에서 끝나므로 병합이 반영되기를 기다린다
        waitFor { "a" in tabsItemIds(tabsUnlockedData(tabs.b.session)) }
        tabsSaveItem(tabs.b.session, "b")

        assertEquals(listOf("a", "b"), tabsRecordItemIds(tabs.deviceStore.record))
        assertEquals(true, tabs.deviceStore.record?.pendingSync)
    }

    @Test
    fun mergesOtherTabRecordIntoMemoryBeforeWriting() = sessionTest {
        val tabs = tabsTwoUnlocked()
        tabs.vaultApi.offline = true

        tabsSaveItem(tabs.b.session, "b")
        // 원본은 Promise 순서상 다른 탭의 기록 병합(복호화)이 다음 저장 전에 끝나 있었다. JS 대상은 복호화가 실제 이벤트 루프에서 끝나므로 병합이 반영되기를 기다린다
        waitFor { "b" in tabsItemIds(tabsUnlockedData(tabs.a.session)) }
        tabsSaveItem(tabs.a.session, "a")

        assertEquals(listOf("a", "b"), tabsItemIds(tabsUnlockedData(tabs.a.session)))
    }

    @Test
    fun keepsPendingAfterSendWhenOtherTabWroteDuringSend() = sessionTest {
        val tabs = tabsTwoUnlocked()
        val release = gateSaves(tabs.vaultApi)

        val saving = begin { tabsSaveItem(tabs.a.session, "a") }
        // 원본은 save 호출을 기다렸다. 저장 요청이 문에 닿을 때까지 기다린다
        waitFor { tabs.vaultApi.gate!!.waiting > 0 }
        tabs.vaultApi.offline = true
        tabsSaveItem(tabs.b.session, "b")
        tabs.vaultApi.offline = false
        release()
        saving.await()

        assertEquals(true, tabs.deviceStore.record?.pendingSync)
        assertEquals(listOf("a", "b"), tabsRecordItemIds(tabs.deviceStore.record))
    }

    @Test
    fun clearsPendingAfterSendWhenNothingChangedDuringSend() = sessionTest {
        val tabs = tabsTwoUnlocked()
        val release = gateSaves(tabs.vaultApi)

        val saving = begin { tabsSaveItem(tabs.a.session, "a") }
        waitFor { tabs.vaultApi.gate!!.waiting > 0 }
        release()
        saving.await()

        assertEquals(false, tabs.deviceStore.record?.pendingSync)
        assertEquals(false, tabs.a.unlockedState().pendingSync)
    }

    @Test
    fun doesNotReviveRecordClearedByOtherTabLogoutAndLogsOutThisTabToo() = sessionTest {
        val tabs = tabsTwoUnlocked()
        val data = tabsUnlockedData(tabs.a.session)

        tabs.b.session.logout()
        try {
            tabs.a.session.save(addItem(data, fields, NOW, "late"))
        } catch (_: Exception) {
            // 로그아웃된 탭의 저장은 실패해도 된다
        }

        assertNull(tabs.deviceStore.record)
        assertEquals(SessionState.SignedOut, tabs.a.session.state())
    }

    @Test
    fun doesNotWriteWhenSameEmailButKdfDiffersAndSendsToSignIn() = sessionTest {
        val tabs = tabsTwoUnlocked()
        tabs.vaultApi.offline = true
        val current = tabs.deviceStore.record!!
        val changed = current.copy(kdf = current.kdf.copy(salt = "AAAAAAAAAAAAAAAAAAAAAA=="))
        tabs.deviceStore.stored = encodeDeviceRecord(changed)

        try {
            tabsSaveItem(tabs.a.session, "a")
        } catch (_: Exception) {
            // 로그인 화면으로 보내며 실패해도 된다
        }

        assertEquals(SessionState.SignedOut, tabs.a.session.state())
        assertEquals(changed, tabs.deviceStore.record)
    }

    @Test
    fun lockedTabDoesNotWriteSendResultOverRecordOfOtherTab() = sessionTest {
        val tabs = tabsTwoUnlocked()
        val release = gateSaves(tabs.vaultApi)

        val saving = begin { tabsSaveItem(tabs.a.session, "a") }
        waitFor { tabs.vaultApi.gate!!.waiting > 0 }
        tabs.a.session.lock(LockScope.THIS_TAB)
        tabs.vaultApi.offline = true
        tabsSaveItem(tabs.b.session, "b")
        val written = tabs.deviceStore.record
        tabs.vaultApi.offline = false
        release()
        saving.tabsAwaitIgnoringError()

        assertEquals(written, tabs.deviceStore.record)
    }
}

/** 받는 메시지를 끊을 수 있는 탭. 동결돼 메시지를 놓친 탭을 흉내 낸다 */
private class TabsMutable(private val tabs: Tabs) {
    private var muted = false

    val wrapped: Tabs = object : Tabs {
        override val lock = tabs.lock
        override val channel = object : TabChannel {
            override fun post(message: TabMessage) = tabs.channel.post(message)

            override fun subscribe(listener: (TabMessage) -> Unit): () -> Unit =
                tabs.channel.subscribe { message -> if (!muted) listener(message) }
        }
    }

    fun mute() {
        muted = true
    }
}

class VaultSessionTabsNotifyTest {
    @Test
    fun lockingOneTabLocksAllTabs() = sessionTest {
        val tabs = tabsTwoUnlocked()

        tabs.a.session.lock()

        assertIs<SessionState.Locked>(tabs.b.session.state())
    }

    @Test
    fun lockingThisTabDoesNotNotifyOtherTabs() = sessionTest {
        val tabs = tabsTwoUnlocked()

        tabs.a.session.lock(LockScope.THIS_TAB)

        assertIs<SessionState.Locked>(tabs.a.session.state())
        assertIs<SessionState.Unlocked>(tabs.b.session.state())
    }

    @Test
    fun loggingOutOneTabLogsOutAllTabsAndLeavesRecordCleared() = sessionTest {
        val tabs = tabsTwoUnlocked()
        val writes = tabs.deviceStore.writes

        tabs.b.session.logout()
        tabsSettle()

        assertEquals(SessionState.SignedOut, tabs.a.session.state())
        assertNull(tabs.deviceStore.record)
        assertEquals(writes, tabs.deviceStore.writes)
        assertEquals(2, tabs.a.authApi.loggedOut.size)
    }

    @Test
    fun signingInToOtherAccountLogsOutOtherTabOfPreviousAccount() = sessionTest {
        val tabs = tabsTwoUnlocked()

        tabs.b.session.login("other@example.com", PASSWORD)
        tabsSettle()

        assertEquals(SessionState.SignedOut, tabs.a.session.state())
        assertEquals("other@example.com", tabs.deviceStore.record?.email)
    }

    @Test
    fun lockedTabReplacesEncryptedVaultWithRecordOfOtherTab() = sessionTest {
        val tabs = tabsTwoUnlocked()
        tabs.a.session.lock()
        tabs.b.session.unlock(PASSWORD)
        tabs.vaultApi.offline = true

        tabsSaveItem(tabs.b.session, "b")
        waitFor { tabs.a.session.state() == SessionState.Locked(TABS_EMAIL, pendingSync = true) }

        tabs.a.session.unlock(PASSWORD)
        assertEquals(listOf("b"), tabsItemIds(tabsUnlockedData(tabs.a.session)))
    }

    @Test
    fun unlockOpensWithLatestDeviceRecordEvenIfNotificationMissed() = sessionTest {
        val hub = InMemoryTabHub()
        val tabA = TabsMutable(hub.connect())
        val a = setup(tabs = tabA.wrapped)
        val b = setup(authApi = a.authApi, vaultApi = a.vaultApi, deviceStore = a.deviceStore, tabs = hub.connect())
        a.session.login(TABS_EMAIL, PASSWORD)
        b.session.restore()
        b.session.unlock(PASSWORD)
        tabsSettle()
        a.session.lock(LockScope.THIS_TAB)
        tabA.mute()
        a.vaultApi.offline = true

        tabsSaveItem(b.session, "b")
        assertEquals(SessionState.Locked(TABS_EMAIL, pendingSync = false), a.session.state())
        a.session.unlock(PASSWORD)

        assertEquals(listOf("b"), tabsItemIds(tabsUnlockedData(a.session)))
    }

    @Test
    fun reportsPendingContentLeftByOtherTabForLogoutConfirmation() = sessionTest {
        val hub = InMemoryTabHub()
        val tabA = TabsMutable(hub.connect())
        val a = setup(tabs = tabA.wrapped)
        val b = setup(authApi = a.authApi, vaultApi = a.vaultApi, deviceStore = a.deviceStore, tabs = hub.connect())
        a.session.login(TABS_EMAIL, PASSWORD)
        b.session.restore()
        b.session.unlock(PASSWORD)
        tabsSettle()
        tabA.mute()
        a.vaultApi.offline = true

        assertFalse(a.session.pendingOnDevice())
        tabsSaveItem(b.session, "b")

        assertEquals(false, a.unlockedState().pendingSync)
        assertTrue(a.session.pendingOnDevice())
    }

    @Test
    fun tabBeforeSignInIgnoresMessages() = sessionTest {
        val tabs = tabsTwoUnlocked()
        val c = setup(authApi = tabs.a.authApi, vaultApi = tabs.vaultApi, deviceStore = tabs.deviceStore, tabs = tabs.hub.connect())
        tabs.vaultApi.offline = true

        tabsSaveItem(tabs.a.session, "a")
        tabs.a.session.lock()
        tabs.a.session.logout()
        tabsSettle()

        assertEquals(SessionState.SignedOut, c.session.state())
        assertEquals(0, c.locks())
    }

    @Test
    fun notifiesWhenStateChangesBecauseOfOtherTab() = sessionTest {
        val tabs = tabsTwoUnlocked()
        var changes = 0
        tabs.a.session.onRemoteChange { changes++ }
        tabs.vaultApi.offline = true

        tabsSaveItem(tabs.b.session, "b")

        waitFor { activeIds(tabs.a.session.state()) == listOf("b") }
        assertTrue(changes > 0)
    }
}

/** 공유 기기 저장소 위에서 이 탭의 쓰기만 실패시킬 수 있는 저장소 (저장 공간 부족 등). FakeDeviceStore는 상속할 수 없어 감싼다 */
private class TabsFailingWriteStore(private val shared: FakeDeviceStore) : DeviceRecordStore {
    var failWrites = false

    override suspend fun load(): String? = shared.load()

    override suspend fun save(recordJson: String) {
        if (failWrites) throw IllegalStateException("저장 공간 부족")
        shared.save(recordJson)
    }

    override suspend fun clear() = shared.clear()
}

/** FakeDeviceStore 대신 임의의 기기 저장소를 쓰는 세션 (setup은 FakeDeviceStore만 받는다) */
private fun TestScope.tabsSessionWithStore(
    deviceStore: DeviceRecordStore,
    authApi: FakeAuthApi,
    vaultApi: FakeVaultApi,
    tabs: Tabs = SingleTab(),
): VaultSession {
    if (authApi.vaultApi == null) authApi.vaultApi = vaultApi
    return VaultSession(
        VaultSessionDeps(
            auth = AuthService(authApi),
            vaultSyncFor = vaultSyncFor(vaultApi),
            revisionMemory = RevisionMemory(),
            deviceStore = deviceStore,
            tabs = tabs,
            scope = backgroundScope,
        ),
    )
}

class VaultSessionTabsUnwrittenPendingTest {
    @Test
    fun doesNotLosePendingContentInMemoryWhenLockedAndUnlockedAfterWriteFailure() = sessionTest {
        val shared = FakeDeviceStore()
        val deviceStore = TabsFailingWriteStore(shared)
        val authApi = FakeAuthApi()
        val vaultApi = FakeVaultApi()
        val tab = tabsSessionWithStore(deviceStore, authApi, vaultApi)
        tab.login(TABS_EMAIL, PASSWORD)
        vaultApi.offline = true
        deviceStore.failWrites = true

        tabsSaveItem(tab, "x")
        tab.lock(LockScope.THIS_TAB)
        deviceStore.failWrites = false
        tab.unlock(PASSWORD)

        assertEquals(listOf("x"), tabsItemIds(tabsUnlockedData(tab)))
        assertEquals(listOf("x"), tabsRecordItemIds(shared.record))
    }

    @Test
    fun lockedTabThatFailedToWriteKeepsPendingContentAndMergesOnUnlock() = sessionTest {
        val hub = InMemoryTabHub()
        val shared = FakeDeviceStore()
        val storeB = TabsFailingWriteStore(shared)
        val a = setup(deviceStore = shared, tabs = hub.connect())
        val b = tabsSessionWithStore(storeB, a.authApi, a.vaultApi, hub.connect())
        a.session.login(TABS_EMAIL, PASSWORD)
        b.restore()
        b.unlock(PASSWORD)
        tabsSettle()
        a.vaultApi.offline = true
        storeB.failWrites = true

        tabsSaveItem(b, "y")
        b.lock(LockScope.THIS_TAB)
        tabsSaveItem(a.session, "a")
        tabsSettle()
        storeB.failWrites = false
        b.unlock(PASSWORD)

        assertEquals(listOf("a", "y"), tabsItemIds(tabsUnlockedData(b)))
        assertEquals(listOf("a", "y"), tabsRecordItemIds(shared.record))
    }
}

class VaultSessionTabsLogoutConfirmTest {
    @Test
    fun assumesPendingContentAndAsksConfirmationWhenDeviceRecordCannotBeRead() = sessionTest {
        val tabs = tabsTwoUnlocked()
        tabs.deviceStore.failLoads = 1

        assertTrue(tabs.a.session.pendingOnDevice())
    }
}
