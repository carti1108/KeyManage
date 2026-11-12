package com.keymanage.domain.session

import com.keymanage.domain.auth.MasterPasswordChangedException
import com.keymanage.domain.device.RememberedKdf
import com.keymanage.domain.device.TabMemory
import com.keymanage.domain.tabs.InMemoryTabHub
import com.keymanage.domain.tabs.TabChannel
import com.keymanage.domain.tabs.TabLock
import com.keymanage.domain.tabs.TabMessage
import com.keymanage.domain.tabs.Tabs
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

// 원본: Apps/Web/src/domain/session/vaultSession.tabMemory.test.ts, vaultSession.tabMemorySync.test.ts
// 탭이 마지막으로 연 계정의 KDF 값을 기억해, 다른 탭이 비밀번호를 바꾼 뒤 새로 열려도 비밀번호 바뀜을 안내한다 (SEC-003 규칙 11, SEC-004 규칙 8)
// 비밀번호 바뀜을 안내한 탭은 탭 기억을 기기 기록의 KDF 값으로 맞춰 안내를 한 번만 하고,
// 비밀번호를 바꾼 탭은 기기 기록을 쓴 뒤에만 새 KDF 값을 기억한다 (SEC-003 규칙 11, SEC-004)

private const val TAB_MEMORY_EMAIL = "user@example.com"
private const val TAB_MEMORY_NEW_PASSWORD = "a brand new master password"

/** 탭 A·B가 같은 브라우저(기기 기록·탭 허브)를 쓴다. B는 잠금을 풀어 기억을 남긴다 */
private class TabMemoryTwoTabs(
    val hub: InMemoryTabHub,
    val deviceStore: FakeDeviceStore,
    val a: SessionSetup,
    val aMemory: FakeTabMemory,
    val b: SessionSetup,
    val bMemory: FakeTabMemory,
)

private suspend fun TestScope.tabMemoryTwoTabs(): TabMemoryTwoTabs {
    val hub = InMemoryTabHub()
    val deviceStore = FakeDeviceStore()
    val aMemory = FakeTabMemory()
    val bMemory = FakeTabMemory()
    val a = setup(deviceStore = deviceStore, tabs = hub.connect(), tabMemory = aMemory)
    a.session.login(TAB_MEMORY_EMAIL, PASSWORD)
    val b = setup(authApi = a.authApi, vaultApi = a.vaultApi, deviceStore = deviceStore, tabs = hub.connect(), tabMemory = bMemory)
    b.session.restore()
    b.session.unlock(PASSWORD)
    // 열기 때문에 다른 탭이 띄운 기기 기록 갱신이 테스트 본문과 섞이지 않도록 대기 작업을 끝내 둔다(원본은 Promise 순서로 자연히 정리된 뒤였다)
    testScheduler.advanceUntilIdle()
    return TabMemoryTwoTabs(hub, deviceStore, a, aMemory, b, bMemory)
}

private fun TestScope.tabMemoryReload(base: SessionSetup, tabs: Tabs, tabMemory: TabMemory): SessionSetup =
    setup(authApi = base.authApi, vaultApi = base.vaultApi, deviceStore = base.deviceStore, tabs = tabs, tabMemory = tabMemory)

private fun tabMemoryRecordKdf(deviceStore: FakeDeviceStore): RememberedKdf {
    val record = checkNotNull(deviceStore.record) { "기기 기록이 없음" }
    return RememberedKdf(record.email, record.kdf.salt, record.kdf.iterations)
}

class VaultSessionTabMemoryTest {
    @Test
    fun remembersOnlyEmailAndKdfValuesOnUnlockOrSignIn() = sessionTest {
        val tabs = tabMemoryTwoTabs()

        assertEquals(RememberedKdf(TAB_MEMORY_EMAIL, tabs.a.authApi.kdf.salt, tabs.a.authApi.kdf.iterations), tabs.aMemory.value)
        assertEquals(tabs.aMemory.value, tabs.bMemory.value)
    }

    @Test
    fun tabThatChangedPasswordRemembersNewKdfValues() = sessionTest {
        val tabs = tabMemoryTwoTabs()

        tabs.a.session.changeMasterPassword(PASSWORD, TAB_MEMORY_NEW_PASSWORD)

        assertEquals(RememberedKdf(TAB_MEMORY_EMAIL, tabs.a.authApi.kdf.salt, tabs.a.authApi.kdf.iterations), tabs.aMemory.value)
    }

    @Test
    fun tabOpenedAfterOtherTabChangedPasswordKeepsRecordAndNotifiesPasswordChangeSignedOut() = sessionTest {
        val tabs = tabMemoryTwoTabs()
        // 이 탭(B)이 알림을 받기 전에 새로고침(탭 폐기 등)된 상황: 새로 열린 B의 기억은 옛 KDF 값 그대로다.
        // 살아 있는 B는 알림을 받아 안내한 뒤 기억을 새 값으로 맞추므로, 새로 열린 B의 기억은 따로 둔다
        val bMemory = FakeTabMemory()
        bMemory.set(tabs.bMemory.value!!)
        tabs.a.session.changeMasterPassword(PASSWORD, TAB_MEMORY_NEW_PASSWORD)
        val recordAfterChange = tabs.deviceStore.record

        val reloaded = tabMemoryReload(tabs.b, tabs.hub.connect(), bMemory)
        assertFailsWith<MasterPasswordChangedException> { reloaded.session.restore() }

        assertEquals(SessionState.SignedOut, reloaded.session.state())
        assertEquals(recordAfterChange, tabs.deviceStore.record)
        reloaded.session.login(TAB_MEMORY_EMAIL, TAB_MEMORY_NEW_PASSWORD)
        assertEquals(tabs.a.authApi.kdf.salt, bMemory.value?.salt)
    }

    @Test
    fun freshTabWithoutMemoryStartsLockedWithKdfValuesOfDeviceRecord() = sessionTest {
        val tabs = tabMemoryTwoTabs()
        tabs.a.session.changeMasterPassword(PASSWORD, TAB_MEMORY_NEW_PASSWORD)

        val fresh = tabMemoryReload(tabs.b, tabs.hub.connect(), FakeTabMemory())
        fresh.session.restore()

        assertIs<SessionState.Locked>(fresh.session.state())
        fresh.session.unlock(TAB_MEMORY_NEW_PASSWORD)
    }

    @Test
    fun ignoresMemoryOfOtherEmail() = sessionTest {
        val tabs = tabMemoryTwoTabs()
        tabs.a.session.changeMasterPassword(PASSWORD, TAB_MEMORY_NEW_PASSWORD)
        val memory = FakeTabMemory()
        memory.set(RememberedKdf("other@example.com", "AAAAAAAAAAAAAAAAAAAAAA==", 600_000))

        val other = tabMemoryReload(tabs.b, tabs.hub.connect(), memory)
        other.session.restore()

        assertIs<SessionState.Locked>(other.session.state())
    }

    @Test
    fun logoutClearsMemory() = sessionTest {
        val tabs = tabMemoryTwoTabs()

        tabs.a.session.logout()

        assertNull(tabs.aMemory.value)
    }

    @Test
    fun lockedTabThatMissedNotificationReportsPasswordChangeNotSessionState() = sessionTest {
        val hub = InMemoryTabHub()
        val deviceStore = FakeDeviceStore()
        val a = setup(deviceStore = deviceStore, tabs = hub.connect())
        val bTabs = hub.connect()
        // 동결 등으로 알림을 받지 못하는 탭
        val deaf = object : Tabs {
            override val lock: TabLock = bTabs.lock
            override val channel: TabChannel = object : TabChannel {
                override fun post(message: TabMessage) = bTabs.channel.post(message)

                override fun subscribe(listener: (TabMessage) -> Unit): () -> Unit = {}
            }
        }
        val b = setup(authApi = a.authApi, vaultApi = a.vaultApi, deviceStore = deviceStore, tabs = deaf)
        a.session.login(TAB_MEMORY_EMAIL, PASSWORD)
        b.session.restore()
        b.session.unlock(PASSWORD)
        b.session.lock(LockScope.THIS_TAB)
        a.session.changeMasterPassword(PASSWORD, TAB_MEMORY_NEW_PASSWORD)

        assertFailsWith<MasterPasswordChangedException> { b.session.unlock(PASSWORD) }
        assertEquals(SessionState.SignedOut, b.session.state())
    }
}

class VaultSessionTabMemoryNoticeOnceTest {
    @Test
    fun tabThatNotifiedPasswordChangeStartsLockedFromNextReloadAndUnlocksOfflineWithNewPassword() = sessionTest {
        val tabs = tabMemoryTwoTabs()
        // 이 탭(B)이 알림을 받기 전에 새로고침된 상황: 기억은 옛 KDF 값 그대로다
        val staleMemory = FakeTabMemory()
        staleMemory.set(tabs.bMemory.value!!)
        tabs.a.session.changeMasterPassword(PASSWORD, TAB_MEMORY_NEW_PASSWORD)

        val first = tabMemoryReload(tabs.b, tabs.hub.connect(), staleMemory)
        assertFailsWith<MasterPasswordChangedException> { first.session.restore() }
        assertEquals(tabMemoryRecordKdf(tabs.deviceStore), staleMemory.value)

        val second = tabMemoryReload(tabs.b, tabs.hub.connect(), staleMemory)
        second.session.restore()
        assertIs<SessionState.Locked>(second.session.state())
        tabs.a.authApi.offline = true
        assertEquals(VaultSession.UnlockResult(offline = true), second.session.unlock(TAB_MEMORY_NEW_PASSWORD))
    }

    @Test
    fun openTabThatLearnsOfOtherTabPasswordChangeAlignsMemoryToNewKdfValues() = sessionTest {
        val tabs = tabMemoryTwoTabs()

        tabs.a.session.changeMasterPassword(PASSWORD, TAB_MEMORY_NEW_PASSWORD)
        // 원본은 한 틱만 기다렸다. 다른 탭의 알림 처리가 끝나 로그아웃 상태와 기억이 맞춰질 때까지 기다린다
        waitFor { tabs.b.session.state() == SessionState.SignedOut && tabs.bMemory.value == tabMemoryRecordKdf(tabs.deviceStore) }

        assertEquals(SessionState.SignedOut, tabs.b.session.state())
        assertEquals(tabMemoryRecordKdf(tabs.deviceStore), tabs.bMemory.value)
    }

    @Test
    fun learningPasswordChangeFromServerOnUnlockCleansUpLikeOtherTabDetectionAndTellsScreenToReread() = sessionTest {
        val mine = setup(tabMemory = FakeTabMemory())
        mine.session.login(TAB_MEMORY_EMAIL, PASSWORD)
        val other = setup(authApi = mine.authApi, vaultApi = mine.vaultApi)
        other.session.login(TAB_MEMORY_EMAIL, PASSWORD)
        other.session.changeMasterPassword(PASSWORD, TAB_MEMORY_NEW_PASSWORD)
        mine.session.lock()
        var remoteChanges = 0
        mine.session.onRemoteChange { remoteChanges++ }

        assertFailsWith<MasterPasswordChangedException> { mine.session.unlock(TAB_MEMORY_NEW_PASSWORD) }

        assertEquals(SessionState.SignedOut, mine.session.state())
        assertEquals(1, remoteChanges)
    }
}

class VaultSessionTabMemoryPasswordChangerTest {
    @Test
    fun doesNotRememberNewKdfValuesWhenDeviceRecordCannotBeWritten() = sessionTest {
        val tabs = tabMemoryTwoTabs()
        val before = tabs.aMemory.value
        tabs.deviceStore.failing = true

        tabs.a.session.changeMasterPassword(PASSWORD, TAB_MEMORY_NEW_PASSWORD)

        assertEquals(before, tabs.aMemory.value)
        assertNotEquals(before?.salt, tabs.a.authApi.kdf.salt)
    }

    @Test
    fun remembersNewKdfValuesWhenDeviceRecordIsWritten() = sessionTest {
        val tabs = tabMemoryTwoTabs()

        tabs.a.session.changeMasterPassword(PASSWORD, TAB_MEMORY_NEW_PASSWORD)

        assertEquals(tabMemoryRecordKdf(tabs.deviceStore), tabs.aMemory.value)
        assertEquals(tabs.a.authApi.kdf.salt, tabs.aMemory.value?.salt)
    }
}
