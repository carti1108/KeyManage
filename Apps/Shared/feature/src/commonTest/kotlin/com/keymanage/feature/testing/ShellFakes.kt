package com.keymanage.feature.testing

import com.carti.ribs.RibCoroutinesConfig
import com.carti.ribs.RibDispatchersProvider
import com.carti.ribs.ViewControllable
import com.carti.ribs.ViewHost
import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.BackupFileSource
import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.backup.LocalDay
import com.keymanage.domain.backup.VaultBackup
import com.keymanage.domain.lock.AutoLockSettingStore
import com.keymanage.domain.lock.DEFAULT_AUTO_LOCK
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.domain.session.LockScope
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.SyncChangeListener
import com.keymanage.domain.vault.ItemFields
import com.keymanage.domain.vault.SyncedVault
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.addItem
import com.keymanage.domain.vault.emptyVault
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.KeyManageViewFactory
import com.keymanage.feature.SessionPort
import com.keymanage.feature.auth.AuthPresentable
import com.keymanage.feature.auth.AuthPresentableListener
import com.keymanage.feature.auth.AuthViewState
import com.keymanage.feature.generator.PasswordGeneratorPresentable
import com.keymanage.feature.generator.PasswordGeneratorPresentableListener
import com.keymanage.feature.generator.PasswordGeneratorViewState
import com.keymanage.feature.main.MainPresentable
import com.keymanage.feature.main.MainPresentableListener
import com.keymanage.feature.main.MainViewState
import com.keymanage.feature.root.RootPresentable
import com.keymanage.feature.settings.SettingsPresentable
import com.keymanage.feature.settings.SettingsPresentableListener
import com.keymanage.feature.settings.SettingsViewState
import com.keymanage.feature.unlock.UnlockPresentable
import com.keymanage.feature.unlock.UnlockPresentableListener
import com.keymanage.feature.unlock.UnlockViewState
import com.keymanage.feature.vaultlist.VaultListPresentable
import com.keymanage.feature.vaultlist.VaultListPresentableListener
import com.keymanage.feature.vaultlist.VaultListViewState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

// Root·Main·KeyManageApp 테스트가 함께 쓰는 가짜. 자식 RIB(Auth·Unlock·VaultList·Generator·Settings)는 진짜를 쓰고 바깥만 가짜로 둔다

val TEST_NOW: Instant = Instant.fromEpochSeconds(1_700_000_000)
const val TEST_EMAIL = "user@test.com"

/** 서비스 이름이 [names]인 항목만 든 볼트. 항목 id는 "id-이름" */
fun testVault(vararg names: String): VaultData =
    names.fold(emptyVault(TEST_NOW)) { vault, name -> addItem(vault, ItemFields(name, "", ""), TEST_NOW, "id-$name") }

class TestDispatchers(dispatcher: CoroutineDispatcher) : RibDispatchersProvider {
    override val Main: CoroutineDispatcher = dispatcher
    override val MainImmediate: CoroutineDispatcher = dispatcher
    override val Default: CoroutineDispatcher = dispatcher
    override val Unconfined: CoroutineDispatcher = dispatcher
}

@OptIn(ExperimentalCoroutinesApi::class)
fun installTestDispatcher(): TestDispatcher {
    val dispatcher = StandardTestDispatcher()
    RibCoroutinesConfig.dispatchers = TestDispatchers(dispatcher)
    return dispatcher
}

/**
 * 상태를 테스트가 정하는 세션. 실제 세션처럼 열 때 [onUnlock]을, 잠글 때 [onLock]을 부르는 쪽에서 동기로 알린다.
 * 서버 응답은 *Handler로 정한다
 */
class FakeSession : SessionPort {
    var email = TEST_EMAIL
    var pendingSync = false
    var signedIn = true
    var storedOnDevice = true
    var readOnly = false
    var current: SessionState = SessionState.SignedOut

    /** 열린 볼트의 내용. 저장하면 바뀌고, [replaceVault]로 알림 없이 바꿀 수도 있다 */
    var vault: VaultData = emptyVault(TEST_NOW)

    var loginHandler: suspend () -> Unit = {}
    var unlockHandler: suspend (String, Boolean) -> Boolean = { _, _ -> false }
    var reconnectHandler: suspend (String, Boolean) -> Unit = { _, _ -> }
    var logoutHandler: suspend () -> Unit = {}
    var deleteAccountHandler: suspend (String) -> Unit = {}
    var pendingOnDeviceHandler: suspend () -> Boolean = { false }

    /** 저장을 반영한 뒤 부른다. 던지면 실제 세션처럼 "반영한 뒤 거절"이 된다 */
    var saveHandler: suspend (VaultData) -> Unit = {}

    val logins = mutableListOf<Triple<String, String, Boolean>>()
    val unlocks = mutableListOf<Pair<String, Boolean>>()
    val reconnects = mutableListOf<Pair<String, Boolean>>()
    val deletes = mutableListOf<String>()
    val saved = mutableListOf<VaultData>()
    val lockScopes = mutableListOf<LockScope>()
    var logoutCalls = 0
    var pendingOnDeviceCalls = 0

    val lockListeners = mutableSetOf<() -> Unit>()
    val unlockListeners = mutableSetOf<() -> Unit>()
    val remoteListeners = mutableSetOf<() -> Unit>()
    val syncListeners = mutableSetOf<SyncChangeListener>()
    val listenerCount: Int get() = lockListeners.size + unlockListeners.size + remoteListeners.size + syncListeners.size

    fun unlockedState(): SessionState.Unlocked = SessionState.Unlocked(
        email = email,
        vault = SyncedVault(revision = 1, data = vault, readOnly = readOnly, payload = null),
        pendingSync = pendingSync,
        signedIn = signedIn,
        storedOnDevice = storedOnDevice,
    )

    fun startUnlocked(): FakeSession = apply { current = unlockedState() }
    fun startLocked(): FakeSession = apply { current = SessionState.Locked(email, pendingSync) }
    fun startSignedOut(): FakeSession = apply { current = SessionState.SignedOut }

    /** 상태 값(pendingSync 등)을 바꾼 뒤 열린 상태를 다시 만든다. 알리지 않는다 */
    fun refreshUnlocked() {
        current = unlockedState()
    }

    /** 복원·재연결처럼 알림 없이 세션의 볼트가 바뀐다 */
    fun replaceVault(next: VaultData) {
        vault = next
        if (current is SessionState.Unlocked) refreshUnlocked()
    }

    // --- 실제 세션처럼 상태를 바꾸고 알린다 ---

    /** 볼트를 연다. 실제 세션은 값을 돌려주기 전에 잠금 해제 알림을 보낸다 */
    fun open() {
        current = unlockedState()
        unlockListeners.toList().forEach { it() }
    }

    fun fireLock() = lockListeners.toList().forEach { it() }
    fun fireRemote() = remoteListeners.toList().forEach { it() }
    fun fireSync(error: Throwable?) = syncListeners.toList().forEach { it(error) }

    /** 이 탭이 잠긴다 */
    fun lockLocally() {
        if (current !is SessionState.Unlocked) return
        current = SessionState.Locked(email, pendingSync)
        fireLock()
    }

    /** 다른 탭이 잠근 것을 이 탭이 따른다 */
    fun remoteLock() {
        lockLocally()
        fireRemote()
    }

    /** 로그인 전 상태로 돌아간다. 열려 있었으면 잠금 알림을 보낸다 */
    fun signOutLocally() {
        val wasUnlocked = current is SessionState.Unlocked
        current = SessionState.SignedOut
        if (wasUnlocked) fireLock()
    }

    fun remoteLogout() {
        signOutLocally()
        fireRemote()
    }

    override fun state(): SessionState = current

    override suspend fun register(email: String, password: String) {
        this.email = email
        loginHandler()
        open()
    }

    override suspend fun login(email: String, password: String, acceptRollback: Boolean) {
        logins += Triple(email, password, acceptRollback)
        this.email = email
        loginHandler()
        open()
    }

    override fun lock(scope: LockScope) {
        lockScopes += scope
        lockLocally()
    }

    override suspend fun unlock(password: String, acceptRollback: Boolean): Boolean {
        unlocks += password to acceptRollback
        val offline = unlockHandler(password, acceptRollback)
        open()
        return offline
    }

    override suspend fun reconnect(password: String, acceptRollback: Boolean) {
        reconnects += password to acceptRollback
        reconnectHandler(password, acceptRollback)
        signedIn = true
        refreshUnlocked()
    }

    override suspend fun save(data: VaultData) {
        saved += data
        replaceVault(data)
        saveHandler(data)
    }

    override suspend fun exportBackup(now: Instant, calendar: LocalCalendar): BackupFile = error("이 테스트에서 쓰지 않는 기능")

    override suspend fun restoreBackup(backup: VaultBackup, password: String, now: Instant): Int = error("이 테스트에서 쓰지 않는 기능")

    override suspend fun changeMasterPassword(currentPassword: String, newPassword: String) = error("이 테스트에서 쓰지 않는 기능")

    override suspend fun deleteAccount(password: String) {
        deletes += password
        deleteAccountHandler(password)
    }

    override suspend fun logout() {
        logoutCalls += 1
        // 실제 세션은 이 기기를 먼저 정리하고(열려 있었으면 잠금 알림) 서버에 토큰 폐기를 요청한다
        signOutLocally()
        logoutHandler()
    }

    override suspend fun pendingOnDevice(): Boolean {
        pendingOnDeviceCalls += 1
        return pendingOnDeviceHandler()
    }

    override fun onRemoteChange(listener: () -> Unit): () -> Unit {
        remoteListeners += listener
        return { remoteListeners -= listener }
    }

    override fun onLock(listener: () -> Unit): () -> Unit {
        lockListeners += listener
        return { lockListeners -= listener }
    }

    override fun onUnlock(listener: () -> Unit): () -> Unit {
        unlockListeners += listener
        return { unlockListeners -= listener }
    }

    override fun onSyncChange(listener: SyncChangeListener): () -> Unit {
        syncListeners += listener
        return { syncListeners -= listener }
    }
}

class FakeAutoLockStore : AutoLockSettingStore {
    var value: Duration = DEFAULT_AUTO_LOCK
    val listeners = mutableListOf<(Duration) -> Unit>()

    override fun read() = value

    override fun write(timeout: Duration) {
        value = timeout
    }

    override fun subscribe(listener: (Duration) -> Unit): () -> Unit {
        listeners += listener
        return { listeners.remove(listener) }
    }
}

class FakeEnvironment : AppEnvironment {
    override val clock: Clock = object : Clock {
        override fun now(): Instant = TEST_NOW
    }
    override val calendar = LocalCalendar { LocalDay(2026, 1, 2) }
    val autoLock = FakeAutoLockStore()
    override val autoLockSetting: AutoLockSettingStore get() = autoLock
    override var serverAddress: String = ""
    var offline = false
    val networkListeners = mutableListOf<() -> Unit>()

    override fun isNetworkOffline() = offline

    override fun subscribeNetwork(listener: () -> Unit): () -> Unit {
        networkListeners += listener
        return { networkListeners.remove(listener) }
    }

    fun changeOffline(value: Boolean) {
        offline = value
        networkListeners.toList().forEach { it() }
    }
}

class FakeBackupFiles : BackupFilesPort {
    override suspend fun save(file: BackupFile) = Unit
    override suspend fun pick(): BackupFileSource? = null
}

class FakeClipboard : ClipboardPort {
    val copied = mutableListOf<String>()
    override suspend fun copy(text: String) {
        copied += text
    }
}

class FakeRandomBytes : RandomBytes {
    private val random = kotlin.random.Random(42)

    override fun nextBytes(size: Int): ByteArray = random.nextBytes(size)
}

// --- 뷰 ---

class FakeRootView : RootPresentable {
    val shown = mutableListOf<ViewControllable>()
    val hidden = mutableListOf<ViewControllable>()
    val visible: List<ViewControllable> get() = shown.filter { view -> shown.count { it === view } > hidden.count { it === view } }.distinct()

    override fun showScreen(view: ViewControllable) {
        shown += view
    }

    override fun hideScreen(view: ViewControllable) {
        hidden += view
    }
}

class FakeHost : ViewHost<RootPresentable> {
    val shown = mutableListOf<RootPresentable>()
    val hidden = mutableListOf<RootPresentable>()

    override fun show(view: RootPresentable) {
        shown += view
    }

    override fun hide(view: RootPresentable) {
        hidden += view
    }
}

class FakeAuthView : AuthPresentable {
    override var listener: AuthPresentableListener? = null
    val states = mutableListOf<AuthViewState>()
    val state: AuthViewState get() = states.last()

    override fun render(state: AuthViewState) {
        states += state
    }
}

class FakeUnlockView : UnlockPresentable {
    override var listener: UnlockPresentableListener? = null
    val states = mutableListOf<UnlockViewState>()
    val state: UnlockViewState get() = states.last()

    override fun render(state: UnlockViewState) {
        states += state
    }
}

class FakeMainView : MainPresentable {
    override var listener: MainPresentableListener? = null
    val states = mutableListOf<MainViewState>()
    val state: MainViewState get() = states.last()
    val shown = mutableListOf<ViewControllable>()
    val hidden = mutableListOf<ViewControllable>()

    /** 지금 보이는 자식 화면 */
    val visible: List<ViewControllable> get() = shown.filter { view -> shown.count { it === view } > hidden.count { it === view } }.distinct()

    override fun render(state: MainViewState) {
        states += state
    }

    override fun showScreen(view: ViewControllable) {
        shown += view
    }

    override fun hideScreen(view: ViewControllable) {
        hidden += view
    }
}

class FakeVaultListView : VaultListPresentable {
    override var listener: VaultListPresentableListener? = null
    val states = mutableListOf<VaultListViewState>()

    override fun render(state: VaultListViewState) {
        states += state
    }

    override fun showGenerator(view: ViewControllable) = Unit
    override fun hideGenerator(view: ViewControllable) = Unit
}

class FakeGeneratorView : PasswordGeneratorPresentable {
    override var listener: PasswordGeneratorPresentableListener? = null
    val states = mutableListOf<PasswordGeneratorViewState>()

    override fun render(state: PasswordGeneratorViewState) {
        states += state
    }
}

class FakeSettingsView : SettingsPresentable {
    override var listener: SettingsPresentableListener? = null
    val states = mutableListOf<SettingsViewState>()
    val state: SettingsViewState get() = states.last()

    override fun render(state: SettingsViewState) {
        states += state
    }
}

/** 만든 뷰를 모두 기록한다. 몇 번 만들었는지로 RIB이 다시 붙었는지 알 수 있다 */
class FakeViewFactory : KeyManageViewFactory {
    val rootViews = mutableListOf<FakeRootView>()
    val authViews = mutableListOf<FakeAuthView>()
    val unlockViews = mutableListOf<FakeUnlockView>()
    val mainViews = mutableListOf<FakeMainView>()
    val vaultListViews = mutableListOf<FakeVaultListView>()
    val generatorViews = mutableListOf<FakeGeneratorView>()
    val settingsViews = mutableListOf<FakeSettingsView>()

    override fun makeRootView() = FakeRootView().also { rootViews += it }
    override fun makeAuthView() = FakeAuthView().also { authViews += it }
    override fun makeUnlockView() = FakeUnlockView().also { unlockViews += it }
    override fun makeMainView() = FakeMainView().also { mainViews += it }
    override fun makeVaultListView() = FakeVaultListView().also { vaultListViews += it }
    override fun makePasswordGeneratorView() = FakeGeneratorView().also { generatorViews += it }
    override fun makeSettingsView() = FakeSettingsView().also { settingsViews += it }
}
