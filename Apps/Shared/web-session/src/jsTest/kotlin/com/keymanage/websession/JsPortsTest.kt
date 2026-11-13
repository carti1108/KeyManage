package com.keymanage.websession

import com.keymanage.domain.tabs.TabLockName
import com.keymanage.domain.tabs.TabMessage
import com.keymanage.domain.device.RememberedKdf
import com.keymanage.serverhttp.keyManageHttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

// TypeScript 객체 리터럴로 포트를 만들어 external interface 어댑터가 실제 JS 값과 맞물리는지 본다

/** peek()으로 저장된 기록·보낸 알림·불린 잠금 이름·탭 기억을 들여다본다. lockMode가 'unavailable'이면 fn을 부르지 않고 null로 끝난다 */
private fun jsPorts(): dynamic = js(
    """(function () {
  var state = { stored: null, posted: [], lockCalls: [], memory: null, listeners: [], lockMode: 'run', storeFails: false };
  // IndexedDB가 실패하면 JS Error로 reject한다 (Kotlin 예외가 아니다)
  function storeFailure() { return Promise.reject(new Error('IndexedDB failed')); }
  return {
    state: state,
    deviceStore: {
      load: function () { return state.storeFails ? storeFailure() : Promise.resolve(state.stored); },
      save: function (json) { if (state.storeFails) return storeFailure(); state.stored = json; return Promise.resolve(); },
      clear: function () { if (state.storeFails) return storeFailure(); state.stored = null; return Promise.resolve(); }
    },
    tabs: {
      lockRun: function (name, ifAvailable, fn) {
        state.lockCalls.push(name + ':' + ifAvailable);
        if (state.lockMode === 'unavailable') return Promise.resolve(null);
        if (state.lockMode === 'reject') return Promise.reject(new TypeError('navigator.locks unavailable'));
        return Promise.resolve().then(function () { return fn(); });
      },
      post: function (kind) { state.posted.push(kind); },
      subscribe: function (listener) {
        state.listeners.push(listener);
        return function () { state.listeners = state.listeners.filter(function (l) { return l !== listener; }); };
      }
    },
    tabMemory: {
      get: function () { return state.memory; },
      set: function (json) { state.memory = json; },
      clear: function () { state.memory = null; }
    }
  };
})()""",
)

private fun noopPorts(): dynamic = jsPorts()

internal fun noopStore(): JsDeviceStore = noopPorts().deviceStore.unsafeCast<JsDeviceStore>()

internal fun noopTabs(): JsTabs = noopPorts().tabs.unsafeCast<JsTabs>()

private fun sessionOver(ports: dynamic, server: FakeServer, withMemory: Boolean = true): KotlinVaultSession = createKotlinSession(
    TEST_BASE_URL,
    ports.deviceStore.unsafeCast<JsDeviceStore>(),
    ports.tabs.unsafeCast<JsTabs>(),
    if (withMemory) ports.tabMemory.unsafeCast<JsTabMemory>() else null,
    keyManageHttpClient(server.engine),
)

class JsPortsTest {
    @Test
    fun sessionRunsOverTypeScriptPorts() = runTest(timeout = 2.minutes) {
        val ports = jsPorts()
        val session = sessionOver(ports, FakeServer())

        session.register("user@example.com", "correct horse battery staple").await()
        val state = ports.state
        // 기기 기록은 Kotlin이 만든 JSON 문자열로 저장소 객체에 들어간다
        val record = kotlinx.serialization.json.Json.parseToJsonElement(state.stored as String)
        assertTrue(record.toString().contains("\"email\":\"user@example.com\""))
        // 기록은 탭 간 'device' 잠금 안에서 쓴다
        assertTrue((state.lockCalls as Array<String>).any { it.startsWith("device:") })
        // 탭 기억은 {"email","salt","iterations"} JSON이다
        val remembered = assertNotNull(parseRemembered(state.memory as String))
        assertEquals("user@example.com", remembered.email)
        assertEquals(600_000, remembered.iterations)

        session.lock("allTabs")
        assertEquals(listOf("locked"), (state.posted as Array<String>).filter { it == "locked" })
        session.lock("thisTab")
        assertEquals(1, (state.posted as Array<String>).count { it == "locked" })
        assertTrue(session.unlock("correct horse battery staple", false).await().not())
    }

    @Test
    fun anotherTabsNotificationReachesRemoteListener() = runTest(timeout = 2.minutes) {
        val ports = jsPorts()
        val session = sessionOver(ports, FakeServer(), withMemory = false)
        session.register("user@example.com", "correct horse battery staple").await()

        var locks = 0
        session.onLock { locks++ }
        // 다른 탭이 잠갔다는 알림. 모르는 알림은 버린다
        val listeners = ports.state.listeners.unsafeCast<Array<(String) -> Unit>>()
        listeners.forEach { it("nonsense") }
        assertEquals("unlocked", session.state().status)
        listeners.forEach { it("locked") }
        assertEquals("locked", session.state().status)
        assertEquals(1, locks)
    }

    @Test
    fun lockRunReturnsNullWhenLockUnavailable() = runTest(timeout = 2.minutes) {
        val ports = jsPorts()
        ports.state.lockMode = "unavailable"
        val lock = JsTabLock(ports.tabs.unsafeCast<JsTabs>())
        var ran = false
        val result = lock.run(TabLockName.SYNC, ifAvailable = true) {
            ran = true
            1
        }
        assertNull(result)
        assertFalse(ran)
        assertEquals("sync:true", (ports.state.lockCalls as Array<String>).single())
    }

    @Test
    fun lockRunPassesResultAndErrors() = runTest(timeout = 2.minutes) {
        val lock = JsTabLock(jsPorts().tabs.unsafeCast<JsTabs>())
        assertEquals(5, lock.run(TabLockName.DEVICE) { 5 })
        val failure = runCatching { lock.run(TabLockName.DEVICE) { throw IllegalStateException("fail") } }.exceptionOrNull()
        assertEquals("IllegalStateException", errorInfo(failure)?.kind)
    }

    @Test
    fun channelPostsWireNamesAndUnsubscribes() {
        val ports = jsPorts()
        val channel = JsTabChannel(ports.tabs.unsafeCast<JsTabs>())
        channel.post(TabMessage.RECORD_CHANGED)
        assertEquals(listOf("record-changed"), (ports.state.posted as Array<String>).toList())

        val received = mutableListOf<TabMessage>()
        val off = channel.subscribe { received += it }
        val listener = ports.state.listeners[0].unsafeCast<(String?) -> Unit>()
        listener("activity")
        listener(null)
        listener("garbage")
        assertEquals(listOf(TabMessage.ACTIVITY), received)
        off()
        assertEquals(0, (ports.state.listeners as Array<*>).size)
    }

    @Test
    fun tabMemoryRoundTripAndBadValues() {
        val ports = jsPorts()
        val memory = JsTabMemoryAdapter(ports.tabMemory.unsafeCast<JsTabMemory>())
        assertNull(memory.get())

        memory.set(RememberedKdf("a@b.com", "c2FsdA==", 600_000))
        assertEquals(RememberedKdf("a@b.com", "c2FsdA==", 600_000), memory.get())

        memory.clear()
        assertNull(memory.get())

        ports.state.memory = "not json"
        assertNull(memory.get())
        ports.state.memory = """{"email":"a@b.com","salt":"x","iterations":"6"}"""
        assertNull(memory.get())
    }

    @Test
    fun missingConnectionIsNetworkError() = runTest(timeout = 2.minutes) {
        val ports = jsPorts()
        val offline = MockEngine { throw IllegalStateException("offline") }
        val session = createKotlinSession(
            TEST_BASE_URL,
            ports.deviceStore.unsafeCast<JsDeviceStore>(),
            ports.tabs.unsafeCast<JsTabs>(),
            null,
            keyManageHttpClient(offline),
        )
        val info = assertNotNull(errorInfo(session.login("user@example.com", "correct horse battery staple", false).rejection()))
        assertEquals("NetworkError", info.kind)
    }

    @Test
    fun rejectedJsFetchBecomesNetworkErrorThroughRealEngine() = runTest(timeout = 2.minutes) {
        val ports = jsPorts()
        val store = ports.deviceStore.unsafeCast<JsDeviceStore>()
        val tabs = ports.tabs.unsafeCast<JsTabs>()
        sessionOver(ports, FakeServer(), withMemory = false).register("user@example.com", "correct horse battery staple").await()

        val global = js("globalThis")
        val originalFetch = global.fetch
        // 오프라인 브라우저의 fetch는 TypeError로 reject한다. Ktor JS가 이를 kotlin.Error("Fail to fetch")로 바꿔 던진다
        global.fetch = js("(function () { return Promise.reject(new TypeError('Failed to fetch')); })")
        try {
            val login = createKotlinSession(TEST_BASE_URL, store, tabs, null)
            val info = assertNotNull(errorInfo(login.login("user@example.com", "correct horse battery staple", false).rejection()))
            assertEquals("NetworkError", info.kind)

            val offline = createKotlinSession(TEST_BASE_URL, store, tabs, null)
            offline.restore().await()
            assertTrue(offline.unlock("correct horse battery staple", false).await())
            assertEquals("unlocked", offline.state().status)
        } finally {
            global.fetch = originalFetch
        }
    }

    @Test
    fun registerSucceedsWhenDeviceStoreRejectsAndReportsIt() = runTest(timeout = 2.minutes) {
        val ports = jsPorts()
        ports.state.storeFails = true
        val session = sessionOver(ports, FakeServer(), withMemory = false)
        val notified = mutableListOf<JsErrorInfo?>()
        session.onSyncChange { notified += it }

        session.register("user@example.com", "correct horse battery staple").await()

        assertEquals("unlocked", session.state().status)
        assertFalse(session.state().storedOnDevice)
        assertTrue(notified.any { it?.kind == "DeviceStorageException" })
    }

    @Test
    fun logoutRevokesOnServerEvenWhenDeviceStoreRejects() = runTest(timeout = 2.minutes) {
        val ports = jsPorts()
        val server = FakeServer()
        val session = sessionOver(ports, server, withMemory = false)
        session.register("user@example.com", "correct horse battery staple").await()
        ports.state.storeFails = true

        val info = assertNotNull(errorInfo(session.logout().rejection()))

        assertEquals("DeviceStorageException", info.kind)
        assertTrue(server.calls.contains("POST /api/v1/auth/logout"))
        assertEquals("signedOut", session.state().status)
    }

    @Test
    fun pendingOnDeviceIsTrueWhenDeviceStoreRejects() = runTest(timeout = 2.minutes) {
        val ports = jsPorts()
        val session = sessionOver(ports, FakeServer(), withMemory = false)
        session.register("user@example.com", "correct horse battery staple").await()
        ports.state.storeFails = true

        assertTrue(session.pendingOnDevice().await())
    }

    @Test
    fun tabLockJsFailureBecomesException() = runTest(timeout = 2.minutes) {
        val ports = jsPorts()
        ports.state.lockMode = "reject"
        val lock = JsTabLock(ports.tabs.unsafeCast<JsTabs>())

        val failure = runCatching { lock.run(TabLockName.DEVICE) { 1 } }.exceptionOrNull()

        assertTrue(failure is Exception)
        assertEquals("TabLockException", errorInfo(failure)?.kind)
    }

    @Test
    fun pendingOnDeviceIsTrueWhenTabLockRejects() = runTest(timeout = 2.minutes) {
        val ports = jsPorts()
        val session = sessionOver(ports, FakeServer(), withMemory = false)
        session.register("user@example.com", "correct horse battery staple").await()
        ports.state.lockMode = "reject"

        assertTrue(session.pendingOnDevice().await())
    }

    @Test
    fun remoteLockClearsStateCache() = runTest(timeout = 2.minutes) {
        val ports = jsPorts()
        val session = sessionOver(ports, FakeServer(), withMemory = false)
        session.register("user@example.com", "correct horse battery staple").await()
        assertNotNull(session.state().vaultJson)
        assertNotNull(session.cachedJs)

        ports.state.listeners.unsafeCast<Array<(String) -> Unit>>().forEach { it("locked") }

        // 잠긴 뒤에는 평문 볼트 JSON을 든 캐시가 남지 않는다 (state()를 다시 부르기 전에 본다)
        assertNull(session.cachedJs)
        assertEquals("locked", session.state().status)
    }
}
