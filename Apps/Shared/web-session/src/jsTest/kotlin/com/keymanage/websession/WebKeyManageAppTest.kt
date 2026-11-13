package com.keymanage.websession

import com.keymanage.domain.lock.DEFAULT_AUTO_LOCK
import com.keymanage.domain.lock.InvalidAutoLockTimeoutException
import com.keymanage.serverhttp.keyManageHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import com.carti.ribs.web.ScreenSlot
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

// 진입점 WebKeyManageApp이 TypeScript 포트(JS 객체) 위에서 RIB 트리를 돌리는지 본다. 서버는 메모리 FakeServer다

private const val EMAIL = "user@example.com"
private const val PASSWORD = "correct horse battery staple"
private const val ITEM_PASSWORD = "Zx9!pQ-77tLm#2"

/** 앱이 받는 JS 포트 전부. state로 기록을 들여다본다 */
private fun appPorts(): dynamic = js(
    """(function () {
  var state = {
    stored: null, memory: null, copied: [], saved: [], picked: null,
    offline: false, networkListeners: [],
    autoLock: 300000, autoLockWrites: [], autoLockListeners: [], autoLockWriteFails: false
  };
  return {
    state: state,
    deviceStore: {
      load: function () { return Promise.resolve(state.stored); },
      save: function (json) { state.stored = json; return Promise.resolve(); },
      clear: function () { state.stored = null; return Promise.resolve(); }
    },
    tabs: {
      lockRun: function (name, ifAvailable, fn) { return Promise.resolve().then(function () { return fn(); }); },
      post: function (kind) {},
      subscribe: function (listener) { return function () {}; }
    },
    tabMemory: {
      get: function () { return state.memory; },
      set: function (json) { state.memory = json; },
      clear: function () { state.memory = null; }
    },
    clipboard: {
      copy: function (text) { state.copied.push(text); return Promise.resolve(); }
    },
    backupFiles: {
      save: function (name, text) { state.saved.push({ name: name, text: text }); return Promise.resolve(); },
      pick: function () { return Promise.resolve(state.picked); }
    },
    environment: {
      serverAddress: '',
      isNetworkOffline: function () { return state.offline; },
      subscribeNetwork: function (listener) {
        state.networkListeners.push(listener);
        return function () { state.networkListeners = state.networkListeners.filter(function (l) { return l !== listener; }); };
      },
      readAutoLock: function () { return state.autoLock; },
      writeAutoLock: function (ms) {
        if (state.autoLockWriteFails) throw new Error('storage full');
        state.autoLock = ms; state.autoLockWrites.push(ms);
      },
      subscribeAutoLock: function (listener) {
        state.autoLockListeners.push(listener);
        return function () { state.autoLockListeners = state.autoLockListeners.filter(function (l) { return l !== listener; }); };
      }
    }
  };
})()""",
)

private fun newApp(ports: dynamic, server: FakeServer): WebKeyManageApp {
    httpClientFactory = { keyManageHttpClient(server.engine) }
    return WebKeyManageApp(
        TEST_BASE_URL,
        ports.deviceStore.unsafeCast<JsDeviceStore>(),
        ports.tabs.unsafeCast<JsTabs>(),
        ports.tabMemory.unsafeCast<JsTabMemory>(),
        ports.clipboard.unsafeCast<JsClipboard>(),
        ports.backupFiles.unsafeCast<JsBackupFiles>(),
        ports.environment.unsafeCast<JsEnvironment>(),
    )
}

private fun ScreenSlot.key(): String? = getSnapshot()?.screenKey

private inline fun <reified V> ScreenSlot.view(): V = getSnapshot()?.view as? V ?: fail("화면이 ${V::class.simpleName}이 아님: ${key()}")

/** RIB은 요청 결과를 비동기로 반영한다. 조건이 참이 될 때까지 실제 시간으로 기다린다 */
private suspend fun waitUntil(what: String, condition: () -> Boolean) {
    val start = TimeSource.Monotonic.markNow()
    while (!condition()) {
        if (start.elapsedNow() > 30.seconds) fail("시간 초과: $what")
        withContext(Dispatchers.Default) { delay(10) }
    }
}

class WebKeyManageAppTest {
    @Test
    fun registerBrowseTabsLockAndUnlock() = runTest(timeout = 2.minutes) {
        val server = FakeServer()
        val ports = appPorts()
        val app = newApp(ports, server)
        try {
            // 시작: 세션이 없으니 로그인 화면
            assertNull(app.rootSlot.getSnapshot())
            app.start()
            waitUntil("로그인 화면") { app.rootSlot.key() == "auth" }
            val auth = app.rootSlot.view<WebAuthView>()
            assertEquals("login", auth.store.getSnapshot().mode)

            // 가입 입력 → 제출 → 볼트 화면
            auth.switchMode()
            auth.updateEmail(EMAIL)
            auth.updatePassword(PASSWORD)
            auth.updateConfirmation(PASSWORD)
            val typed = auth.store.getSnapshot()
            assertEquals("register", typed.mode)
            assertEquals(PASSWORD, typed.password)
            assertTrue(typed.showsStrengthGauge)
            auth.submit()
            waitUntil("볼트 화면") { app.rootSlot.key() == "main" }
            assertEquals("unlocked", app.session.state().status)
            assertEquals(EMAIL, app.session.state().email)

            // 볼트 탭: 항목 추가 → 목록 → 비밀번호 복사
            val main = app.rootSlot.view<WebMainView>()
            assertEquals(EMAIL, main.store.getSnapshot().email)
            assertEquals("vault", main.store.getSnapshot().selectedTab)
            waitUntil("볼트 목록 화면") { main.slot.key() == "vaultList" }
            val vaultList = main.slot.view<WebVaultListView>()
            assertEquals("list", vaultList.store.getSnapshot().screen)
            assertTrue(vaultList.store.getSnapshot().isVaultEmpty)
            vaultList.startAdd()
            vaultList.editForm("serviceName", "GitHub")
            vaultList.editForm("username", "octocat")
            vaultList.editForm("password", ITEM_PASSWORD)
            assertEquals("GitHub", vaultList.store.getSnapshot().form.serviceName)
            vaultList.submitForm()
            waitUntil("항목 저장") { vaultList.store.getSnapshot().items.size == 1 }
            val item = vaultList.store.getSnapshot().items[0]
            assertEquals("GitHub", item.serviceName)
            assertEquals(2, server.vaultRevision)
            vaultList.copyPassword(item.id)
            waitUntil("복사") { (ports.state.copied.length as Int) == 1 }
            assertEquals(ITEM_PASSWORD, ports.state.copied[0])

            // 탭 전환: 생성기, 설정
            main.selectTab("generator")
            waitUntil("생성기 화면") { main.slot.key() == "passwordGenerator" }
            assertEquals("generator", main.store.getSnapshot().selectedTab)
            val generator = main.slot.view<WebGeneratorView>()
            assertNotNull(generator.store.getSnapshot().generatedText)

            main.selectTab("settings")
            waitUntil("설정 화면") { main.slot.key() == "settings" }
            val settings = main.slot.view<WebSettingsView>()
            assertEquals(EMAIL, settings.store.getSnapshot().email)
            assertEquals(300_000, settings.store.getSnapshot().autoLockTimeoutMs)
            settings.selectAutoLock(60_000)
            waitUntil("자동 잠금 저장") { (ports.state.autoLock as Int) == 60_000 }
            assertEquals(60_000, settings.store.getSnapshot().autoLockTimeoutMs)

            // 잠그기 → 잠금 해제 화면 → 비밀번호 → 볼트 화면(항목 유지)
            main.requestLock()
            waitUntil("잠금 해제 화면") { app.rootSlot.key() == "unlock" }
            assertEquals("locked", app.session.state().status)
            val unlock = app.rootSlot.view<WebUnlockView>()
            assertEquals(EMAIL, unlock.store.getSnapshot().email)
            unlock.updatePassword(PASSWORD)
            unlock.unlock()
            waitUntil("다시 볼트 화면") { app.rootSlot.key() == "main" }
            val reopened = app.rootSlot.view<WebMainView>()
            waitUntil("볼트 목록 다시") { reopened.slot.key() == "vaultList" }
            val reopenedList = reopened.slot.view<WebVaultListView>()
            waitUntil("항목 유지") { reopenedList.store.getSnapshot().items.size == 1 }

            // 끝: 화면을 거둔다. 세션은 그대로다
            app.stop()
            assertNull(app.rootSlot.getSnapshot())
            assertEquals("unlocked", app.session.state().status)
        } finally {
            httpClientFactory = { keyManageHttpClient() }
        }
    }

    @Test
    fun wrongPasswordShowsErrorOnUnlockView() = runTest(timeout = 2.minutes) {
        val server = FakeServer()
        val ports = appPorts()
        val app = newApp(ports, server)
        try {
            app.start()
            waitUntil("로그인 화면") { app.rootSlot.key() == "auth" }
            val auth = app.rootSlot.view<WebAuthView>()
            auth.switchMode()
            auth.updateEmail(EMAIL)
            auth.updatePassword(PASSWORD)
            auth.updateConfirmation(PASSWORD)
            auth.submit()
            waitUntil("볼트 화면") { app.rootSlot.key() == "main" }

            app.session.lock("allTabs")
            waitUntil("잠금 해제 화면") { app.rootSlot.key() == "unlock" }
            val unlock = app.rootSlot.view<WebUnlockView>()
            unlock.updatePassword("wrong password entirely")
            unlock.unlock()
            waitUntil("오류") { unlock.store.getSnapshot().error != null }
            assertEquals("마스터 비밀번호가 올바르지 않습니다.", unlock.store.getSnapshot().error?.body)
            assertFalse(unlock.store.getSnapshot().isLoading)
            assertEquals("unlock", app.rootSlot.key())
        } finally {
            app.stop()
            httpClientFactory = { keyManageHttpClient() }
        }
    }

    @Test
    fun registrationMismatchShowsErrorWithoutLeavingAuth() = runTest(timeout = 2.minutes) {
        val ports = appPorts()
        val app = newApp(ports, FakeServer())
        try {
            app.start()
            waitUntil("로그인 화면") { app.rootSlot.key() == "auth" }
            val auth = app.rootSlot.view<WebAuthView>()
            auth.switchMode()
            auth.updateEmail(EMAIL)
            auth.updatePassword(PASSWORD)
            auth.updateConfirmation("다른 비밀번호입니다 다른 비밀번호")
            auth.submit()
            assertEquals("비밀번호 확인이 일치하지 않습니다.", auth.store.getSnapshot().error?.body)
            assertEquals("auth", app.rootSlot.key())
        } finally {
            app.stop()
            httpClientFactory = { keyManageHttpClient() }
        }
    }
}

class JsAppPortsTest {
    @Test
    fun clipboardFailureBecomesKotlinException() = runTest {
        val js = js("({ copy: function () { return Promise.reject(new Error('denied')); } })").unsafeCast<JsClipboard>()
        assertFailsWith<JsPortException> { JsClipboardAdapter(js).copy("secret") }
    }

    @Test
    fun backupPickReturnsNullWhenCancelledAndFileWhenChosen() = runTest {
        val ports = appPorts()
        val files = JsBackupFilesAdapter(ports.backupFiles.unsafeCast<JsBackupFiles>())
        assertNull(files.pick())

        ports.state.picked = js("({ size: 5, text: function () { return Promise.resolve('hello'); } })")
        val picked = assertNotNull(files.pick())
        assertEquals(5, picked.size)
        assertEquals("hello", picked.text())
    }

    @Test
    fun backupSavePassesNameAndText() = runTest {
        val ports = appPorts()
        JsBackupFilesAdapter(ports.backupFiles.unsafeCast<JsBackupFiles>())
            .save(com.keymanage.domain.backup.BackupFile("keymanage-backup.json", "{}"))
        assertEquals("keymanage-backup.json", ports.state.saved[0].name)
        assertEquals("{}", ports.state.saved[0].text)
    }

    @Test
    fun autoLockStoreReadsWritesAndSubscribesInMilliseconds() {
        val ports = appPorts()
        val store = JsAutoLockSettingStore(ports.environment.unsafeCast<JsEnvironment>())
        assertEquals(DEFAULT_AUTO_LOCK, store.read())

        ports.state.autoLock = 123 // 선택지가 아닌 값은 기본값
        assertEquals(DEFAULT_AUTO_LOCK, store.read())
        ports.state.autoLock = 900_000
        assertEquals(15.minutes, store.read())

        store.write(1.minutes)
        assertEquals(60_000, ports.state.autoLock)
        assertFailsWith<InvalidAutoLockTimeoutException> { store.write(2.minutes) }

        val received = mutableListOf<kotlin.time.Duration>()
        val unsubscribe = store.subscribe { received += it }
        ports.state.autoLockListeners[0](0)
        ports.state.autoLockListeners[0](777)
        assertContentEquals(listOf(kotlin.time.Duration.ZERO, DEFAULT_AUTO_LOCK), received)
        unsubscribe()
        assertEquals(0, ports.state.autoLockListeners.length as Int)
    }

    @Test
    fun autoLockWriteFailureKeepsValueForThisRunOnly() {
        val ports = appPorts()
        val store = JsAutoLockSettingStore(ports.environment.unsafeCast<JsEnvironment>())
        ports.state.autoLockWriteFails = true
        store.write(15.minutes)
        assertEquals(15.minutes, store.read())
    }

    @Test
    fun environmentReadsNetworkAndServerAddress() {
        val ports = appPorts()
        val env = JsAppEnvironment(ports.environment.unsafeCast<JsEnvironment>())
        assertEquals("", env.serverAddress)
        assertFalse(env.isNetworkOffline())
        ports.state.offline = true
        assertTrue(env.isNetworkOffline())

        var notified = 0
        val unsubscribe = env.subscribeNetwork { notified += 1 }
        ports.state.networkListeners[0]()
        assertEquals(1, notified)
        unsubscribe()
        assertEquals(0, ports.state.networkListeners.length as Int)
    }
}
