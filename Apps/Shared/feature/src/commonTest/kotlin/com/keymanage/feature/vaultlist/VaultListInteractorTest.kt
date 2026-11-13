package com.keymanage.feature.vaultlist

import com.carti.ribs.RibCoroutinesConfig
import com.carti.ribs.RibDispatchersProvider
import com.carti.ribs.ViewControllable
import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.backup.VaultBackup
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.domain.session.LockScope
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.SyncChangeListener
import com.keymanage.domain.vault.ReadOnlyVaultException
import com.keymanage.domain.vault.SyncedVault
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.VaultTooLargeException
import com.keymanage.domain.vault.deleteItem
import com.keymanage.domain.vault.parseVaultData
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.generator.PasswordGeneratorPresentable
import com.keymanage.feature.generator.PasswordGeneratorPresentableListener
import com.keymanage.feature.generator.PasswordGeneratorViewAction
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.generator.PasswordGeneratorViewState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

// 원본: Apps/Web/src/ui/vault/VaultScreen.test.tsx
@OptIn(ExperimentalCoroutinesApi::class)
class VaultListInteractorTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var view: FakeVaultListView
    private lateinit var session: FakeSession
    private lateinit var clipboard: FakeClipboard
    private val generatorViews = mutableListOf<FakeGeneratorView>()
    private var router: VaultListRouter? = null

    @BeforeTest
    fun setUp() {
        RibCoroutinesConfig.dispatchers = SingleDispatcher(dispatcher)
        view = FakeVaultListView()
        clipboard = FakeClipboard()
        generatorViews.clear()
    }

    @AfterTest
    fun tearDown() {
        router?.dispatchDetach()
        RibCoroutinesConfig.reset()
    }

    private fun open(data: VaultData = sampleVault(), readOnly: Boolean = false) {
        session = FakeSession(data, readOnly)
        val dependency = object : VaultListDependency {
            override val vaultListViewFactory = VaultListViewFactory { view }
            override val session: SessionPort = this@VaultListInteractorTest.session
            override val clipboard: ClipboardPort = this@VaultListInteractorTest.clipboard
            override val clock: Clock = FixedClock
            override val passwordGeneratorViewFactory = PasswordGeneratorViewFactory { FakeGeneratorView().also(generatorViews::add) }
            override val randomBytes: RandomBytes = Random(7).let { random -> RandomBytes { random.nextBytes(it) } }
        }
        router = VaultListBuilder(dependency).build(object : VaultListListener {}).also { it.dispatchAttach(null) }
    }

    private val state get() = checkNotNull(view.states.lastOrNull()) { "아직 그리지 않음" }

    private fun act(vararg actions: VaultListViewAction) {
        actions.forEach { checkNotNull(view.listener).handle(it) }
        dispatcher.scheduler.runCurrent()
    }

    private fun type(field: VaultItemFormField, value: String) = act(VaultListViewAction.EditForm(field, value))

    private fun item(vault: VaultData, id: String) = vault.items.first { it.id == id }

    // --- 목록 ---

    @Test
    fun listsItemsByName() {
        open()

        assertEquals(VaultListScreen.LIST, state.screen)
        assertEquals(listOf("Apple", "GitHub"), state.items.map { it.serviceName })
        assertNull(state.emptyMessage)
    }

    @Test
    fun searchFiltersTheList() {
        open()

        act(VaultListViewAction.Search("oct"))

        assertEquals("oct", state.query)
        assertEquals(listOf("GitHub"), state.items.map { it.serviceName })
    }

    @Test
    fun explainsAnEmptyVault() {
        open(emptyData())

        assertEquals(VaultListMessages.EMPTY_VAULT, state.emptyMessage)
        assertTrue(state.isVaultEmpty)
    }

    @Test
    fun explainsASearchWithoutResults() {
        open()

        act(VaultListViewAction.Search("zzz"))

        assertEquals(VaultListMessages.NO_SEARCH_RESULT, state.emptyMessage)
        assertFalse(state.isVaultEmpty)
    }

    // --- 상세 ---

    @Test
    fun openingAnItemShowsItsDetailAndBackRemembersIt() {
        open()

        act(VaultListViewAction.OpenItem("gh"))

        assertEquals(VaultListScreen.DETAIL, state.screen)
        assertEquals("GitHub", state.selected?.serviceName)

        act(VaultListViewAction.Back)

        assertEquals(VaultListScreen.LIST, state.screen)
        assertNull(state.selected)
        // 목록으로 돌아오면 보던 항목에 포커스를 돌려준다
        assertEquals("gh", state.focusItemId)
    }

    @Test
    fun openingAnItemThatDoesNotExistDoesNothing() {
        open()

        act(VaultListViewAction.OpenItem("none"))

        assertEquals(VaultListScreen.LIST, state.screen)
    }

    // --- 복사 (COM-001) ---

    @Test
    fun copiesUsernameAndPasswordFromTheDetail() {
        open()
        act(VaultListViewAction.OpenItem("gh"))

        act(VaultListViewAction.CopyUsername("gh"), VaultListViewAction.CopyPassword("gh"))

        assertEquals(listOf("octo", "gh-secret"), clipboard.copied)
    }

    @Test
    fun copiesAPasswordFromTheListWithoutOpeningTheItem() {
        open()

        act(VaultListViewAction.CopyPassword("ap"))

        assertEquals(listOf("apple-secret"), clipboard.copied)
        assertEquals(VaultListScreen.LIST, state.screen)
    }

    @Test
    fun ignoresCopyWhenThereIsNothingToCopy() {
        open(vaultOf(itemOf("wifi", "Wifi", "", "")))

        act(VaultListViewAction.CopyPassword("wifi"), VaultListViewAction.CopyUsername("wifi"), VaultListViewAction.CopyPassword("none"))

        assertEquals(emptyList(), clipboard.copied)
    }

    @Test
    fun showsThePolicyMessageWhenCopyFailsAndClearsItOnTheNextCopy() {
        open()
        clipboard.failure = IllegalStateException("문서에 포커스가 없음")

        act(VaultListViewAction.CopyPassword("ap"))

        assertEquals(VaultListMessages.COPY_FAILED, state.copyError)

        clipboard.failure = null
        act(VaultListViewAction.CopyPassword("ap"))

        assertNull(state.copyError)
        assertEquals(listOf("apple-secret"), clipboard.copied)
    }

    // --- 추가 ---

    @Test
    fun addsTheTypedItemAndGoesToItsDetail() {
        open()

        act(VaultListViewAction.StartAdd)
        type(VaultItemFormField.SERVICE_NAME, "  Naver  ")
        type(VaultItemFormField.USERNAME, "me")
        type(VaultItemFormField.PASSWORD, "naver-secret")
        act(VaultListViewAction.SubmitForm)

        assertEquals(1, session.saved.size)
        val added = session.saved.single().items.first { it.serviceName == "Naver" }
        assertEquals("me", added.username)
        assertEquals("naver-secret", added.password)
        // 새 항목에는 비어 있는 선택 필드를 넣지 않는다
        assertFalse("url" in added)
        assertFalse("notes" in added)
        assertEquals(VaultListScreen.DETAIL, state.screen)
        assertEquals(added.id, state.selected?.id)
        assertEquals(added.id, state.focusItemId)
        assertEquals(VaultItemForm(), state.form)
    }

    @Test
    fun keepsOptionalFieldsThatWereTyped() {
        open()

        act(VaultListViewAction.StartAdd)
        type(VaultItemFormField.SERVICE_NAME, "Naver")
        type(VaultItemFormField.URL, "https://naver.com")
        type(VaultItemFormField.NOTES, "메모")
        act(VaultListViewAction.SubmitForm)

        val added = session.saved.single().items.first { it.serviceName == "Naver" }
        assertEquals("https://naver.com", added.url)
        assertEquals("메모", added.notes)
    }

    @Test
    fun doesNotSaveWithoutAServiceName() {
        open()

        act(VaultListViewAction.StartAdd)
        type(VaultItemFormField.SERVICE_NAME, "   ")
        act(VaultListViewAction.SubmitForm)

        assertEquals(VaultListMessages.SERVICE_NAME_REQUIRED, state.saveError?.body)
        assertEquals(0, session.saveCalls)
        assertEquals(VaultListScreen.ADD, state.screen)
    }

    @Test
    fun keepsTheInputAndShowsThePolicyMessageWhenSavingFails() {
        open()
        session.failNext = VaultTooLargeException(6_000_000)

        act(VaultListViewAction.StartAdd)
        type(VaultItemFormField.SERVICE_NAME, "Naver")
        act(VaultListViewAction.SubmitForm)

        assertEquals("저장 실패", state.saveError?.title)
        assertTrue(state.saveError?.body?.contains("볼트 허용 용량(5MB)을 초과했거나") == true)
        assertEquals("Naver", state.form.serviceName)
        assertEquals(VaultListScreen.ADD, state.screen)
        assertFalse(state.isSaving)
    }

    @Test
    fun editingTheFormClearsNothingButStartingOverClearsTheError() {
        open()
        act(VaultListViewAction.StartAdd)
        act(VaultListViewAction.SubmitForm)
        assertNotNull(state.saveError)

        act(VaultListViewAction.CancelForm)
        act(VaultListViewAction.StartAdd)

        assertNull(state.saveError)
        assertEquals(VaultItemForm(), state.form)
    }

    @Test
    fun cancellingAnAddReturnsToTheListWithoutSaving() {
        open()

        act(VaultListViewAction.StartAdd, VaultListViewAction.CancelForm)

        assertEquals(VaultListScreen.LIST, state.screen)
        assertEquals(0, session.saveCalls)
    }

    @Test
    fun sendsOnlyOneSaveWhileOneIsInFlight() {
        open()
        val gate = CompletableDeferred<Unit>()
        session.gate = gate

        act(VaultListViewAction.StartAdd)
        type(VaultItemFormField.SERVICE_NAME, "Naver")
        act(VaultListViewAction.SubmitForm, VaultListViewAction.SubmitForm)

        assertTrue(state.isSaving)
        gate.complete(Unit)
        dispatcher.scheduler.runCurrent()

        assertEquals(1, session.saveCalls)
        assertFalse(state.isSaving)
    }

    @Test
    fun showsStrengthAndWarningForAWeakPasswordButStillSaves() {
        open()

        act(VaultListViewAction.StartAdd)
        type(VaultItemFormField.SERVICE_NAME, "Naver")
        type(VaultItemFormField.PASSWORD, "123456")

        assertTrue(state.showsFormStrength)
        assertEquals(19, state.formPasswordScore)
        assertTrue(state.showsFormWeakWarning)
        assertTrue(state.showsFormSequentialDigitsNotice)

        act(VaultListViewAction.SubmitForm)

        assertEquals(1, session.saveCalls)
    }

    @Test
    fun sequentialDigitsAreNoticedEvenWhenTheScoreIsHigh() {
        open()

        act(VaultListViewAction.StartAdd)
        type(VaultItemFormField.PASSWORD, "Zq!x9Lp#Rt2\$Vw@m123")

        assertFalse(state.showsFormWeakWarning)
        assertTrue(state.showsFormSequentialDigitsNotice)
    }

    @Test
    fun showsNoStrengthForAnEmptyPassword() {
        open()

        act(VaultListViewAction.StartAdd)

        assertFalse(state.showsFormStrength)
        assertFalse(state.showsFormWeakWarning)
        assertFalse(state.showsFormSequentialDigitsNotice)
    }

    // --- 폼 안의 비밀번호 생성기 ---

    @Test
    fun usingAGeneratedPasswordFillsThePasswordFieldAndClosesTheGenerator() {
        open()
        act(VaultListViewAction.StartAdd)

        act(VaultListViewAction.OpenGenerator)

        assertTrue(state.isGeneratorOpen)
        assertSame(generatorViews.single(), view.shownGenerators.single())
        val shown = assertNotNull(generatorViews.single().states.last().generated).text

        checkNotNull(generatorViews.single().listener).handle(PasswordGeneratorViewAction.Use)

        assertEquals(shown, state.form.password)
        assertFalse(state.isGeneratorOpen)
        assertEquals(view.shownGenerators, view.hiddenGenerators)
    }

    @Test
    fun closingTheGeneratorKeepsThePasswordField() {
        open()
        act(VaultListViewAction.StartAdd)
        type(VaultItemFormField.PASSWORD, "mine")
        act(VaultListViewAction.OpenGenerator)

        checkNotNull(generatorViews.single().listener).handle(PasswordGeneratorViewAction.Close)

        assertEquals("mine", state.form.password)
        assertFalse(state.isGeneratorOpen)
    }

    @Test
    fun opensOnlyOneGeneratorAndOnlyInsideAForm() {
        open()
        act(VaultListViewAction.OpenGenerator)
        assertEquals(0, generatorViews.size)

        act(VaultListViewAction.StartAdd, VaultListViewAction.OpenGenerator, VaultListViewAction.OpenGenerator)

        assertEquals(1, generatorViews.size)
    }

    @Test
    fun leavingTheFormClosesTheGenerator() {
        open()
        act(VaultListViewAction.StartAdd, VaultListViewAction.OpenGenerator)

        act(VaultListViewAction.CancelForm)

        assertFalse(state.isGeneratorOpen)
        assertEquals(1, view.hiddenGenerators.size)
    }

    // --- 수정 ---

    @Test
    fun savesOnlyWhatWasChangedAndKeepsUnknownFields() {
        open()
        act(VaultListViewAction.OpenItem("gh"), VaultListViewAction.StartEdit)

        assertEquals("octo", state.form.username)
        type(VaultItemFormField.USERNAME, "octocat")
        act(VaultListViewAction.SubmitForm)

        val edited = item(session.saved.single(), "gh")
        assertEquals("GitHub", edited.serviceName)
        assertEquals("octocat", edited.username)
        assertEquals("gh-secret", edited.password)
        assertEquals("회사 계정", edited.notes)
        assertTrue("totp" in edited)
        assertEquals(VaultListScreen.DETAIL, state.screen)
        assertEquals("octocat", state.selected?.username)
    }

    @Test
    fun doesNotTurnMissingOptionalFieldsIntoEmptyStrings() {
        open()
        act(VaultListViewAction.OpenItem("ap"), VaultListViewAction.StartEdit)

        type(VaultItemFormField.USERNAME, "timcook")
        act(VaultListViewAction.SubmitForm)

        val edited = item(session.saved.single(), "ap")
        assertFalse("url" in edited)
        assertFalse("notes" in edited)
    }

    @Test
    fun removesAClearedOptionalFieldEntirely() {
        open()
        act(VaultListViewAction.OpenItem("gh"), VaultListViewAction.StartEdit)

        type(VaultItemFormField.URL, "")
        act(VaultListViewAction.SubmitForm)

        assertFalse("url" in item(session.saved.single(), "gh"))
    }

    @Test
    fun doesNotSaveWhenNothingChanged() {
        open()
        act(VaultListViewAction.OpenItem("gh"), VaultListViewAction.StartEdit)

        act(VaultListViewAction.SubmitForm)

        assertEquals(0, session.saveCalls)
        assertEquals(VaultListScreen.DETAIL, state.screen)
    }

    @Test
    fun cancellingAnEditReturnsToTheDetail() {
        open()
        act(VaultListViewAction.OpenItem("gh"), VaultListViewAction.StartEdit)
        type(VaultItemFormField.USERNAME, "changed")

        act(VaultListViewAction.CancelForm)

        assertEquals(VaultListScreen.DETAIL, state.screen)
        assertEquals("gh", state.selected?.id)
        assertEquals(0, session.saveCalls)
    }

    // --- 삭제 ---

    @Test
    fun deletesOnlyAfterConfirmationAndReturnsToTheList() {
        open()
        act(VaultListViewAction.OpenItem("gh"), VaultListViewAction.RequestDelete)

        assertTrue(state.isConfirmingDelete)
        assertEquals(0, session.saveCalls)

        act(VaultListViewAction.ConfirmDelete)

        assertEquals(VaultListScreen.LIST, state.screen)
        assertFalse(state.isConfirmingDelete)
        assertNotNull(item(session.saved.single(), "gh").deletedAt)
        assertEquals(listOf("Apple"), state.items.map { it.serviceName })
    }

    @Test
    fun cancellingTheConfirmationDeletesNothing() {
        open()
        act(VaultListViewAction.OpenItem("gh"), VaultListViewAction.RequestDelete)

        act(VaultListViewAction.CancelDelete)

        assertFalse(state.isConfirmingDelete)
        assertEquals(0, session.saveCalls)
        assertEquals(VaultListScreen.DETAIL, state.screen)
    }

    @Test
    fun confirmingWithoutAskingDeletesNothing() {
        open()
        act(VaultListViewAction.OpenItem("gh"), VaultListViewAction.ConfirmDelete)

        assertEquals(0, session.saveCalls)
    }

    @Test
    fun staysOnTheItemWhileDeletingAndShowsWhyWhenItFails() {
        open()
        val gate = CompletableDeferred<Unit>()
        session.gate = gate
        session.failNext = VaultTooLargeException(6_000_000)
        act(VaultListViewAction.OpenItem("gh"), VaultListViewAction.RequestDelete, VaultListViewAction.ConfirmDelete)

        assertTrue(state.isSaving)
        // 삭제하는 동안에는 다른 화면으로 가지 못한다
        act(VaultListViewAction.Back, VaultListViewAction.StartEdit, VaultListViewAction.OpenItem("ap"), VaultListViewAction.StartAdd)
        assertEquals(VaultListScreen.DETAIL, state.screen)
        assertEquals("gh", state.selected?.id)

        gate.complete(Unit)
        dispatcher.scheduler.runCurrent()

        assertEquals("저장 실패", state.saveError?.title)
        assertEquals("gh", state.selected?.id)
        assertFalse(state.isConfirmingDelete)
        assertFalse(state.isSaving)
    }

    // --- 읽기 전용 ---

    @Test
    fun aReadOnlyVaultCannotBeChanged() {
        open(readOnly = true)

        assertTrue(state.readOnly)
        assertFalse(state.canEdit)
        act(VaultListViewAction.StartAdd)
        assertEquals(VaultListScreen.LIST, state.screen)

        act(VaultListViewAction.OpenItem("gh"), VaultListViewAction.StartEdit, VaultListViewAction.RequestDelete)

        assertEquals(VaultListScreen.DETAIL, state.screen)
        assertFalse(state.isConfirmingDelete)
        assertEquals(0, session.saveCalls)
    }

    @Test
    fun theSessionRefusingASaveShowsItsMessage() {
        open()
        session.failNext = ReadOnlyVaultException()

        act(VaultListViewAction.StartAdd)
        type(VaultItemFormField.SERVICE_NAME, "Naver")
        act(VaultListViewAction.SubmitForm)

        assertNotNull(state.saveError)
        assertEquals(VaultListScreen.ADD, state.screen)
    }

    // --- 세션의 변경 ---

    @Test
    fun goesBackToTheListWhenTheViewedItemIsDeletedElsewhere() {
        open()
        act(VaultListViewAction.OpenItem("gh"), VaultListViewAction.RequestDelete)

        // 동기화는 항목을 빼지 않고 삭제 표시만 남긴다
        session.replace(deleteItem(session.data, "gh", NOW))
        session.fireRemoteChange()
        dispatcher.scheduler.runCurrent()

        assertEquals(VaultListScreen.LIST, state.screen)
        assertNull(state.selected)
        assertFalse(state.isConfirmingDelete)
        assertEquals(listOf("Apple"), state.items.map { it.serviceName })
    }

    @Test
    fun picksUpItemsChangedElsewhereAndKeepsWhatIsBeingTyped() {
        open()
        act(VaultListViewAction.OpenItem("gh"), VaultListViewAction.StartEdit)
        type(VaultItemFormField.USERNAME, "typing")

        session.replace(vaultOf(itemOf("gh", "GitHub", "renamed", "gh-secret"), itemOf("ap", "Apple", "tim", "apple-secret")))
        session.fireSyncChange(null)
        dispatcher.scheduler.runCurrent()

        assertEquals("renamed", state.selected?.username)
        assertEquals("typing", state.form.username)
        assertEquals(VaultListScreen.EDIT, state.screen)
    }

    @Test
    fun readOnlyFollowsTheSession() {
        open()
        assertFalse(state.readOnly)

        session.replace(session.data, readOnly = true)
        session.fireRemoteChange()
        dispatcher.scheduler.runCurrent()

        assertTrue(state.readOnly)
    }

    @Test
    fun keepsTheLastVaultWhileTheSessionIsLocked() {
        open()

        session.current = SessionState.Locked(email = "a@b.c", pendingSync = false)
        session.fireRemoteChange()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf("Apple", "GitHub"), state.items.map { it.serviceName })
    }

    @Test
    fun stopsListeningToTheSessionWhenDetached() {
        open()
        assertEquals(1, session.remoteListeners.size)
        assertEquals(1, session.syncListeners.size)

        router?.dispatchDetach()
        router = null

        assertEquals(0, session.remoteListeners.size)
        assertEquals(0, session.syncListeners.size)
        assertNull(view.listener)
    }

    // 세션은 알림을 보낼 때 목록을 복사해 돈다. 앞선 구독자가 이 RIB을 내리면(부모가 잠금 알림에 화면을 바꿈) 이미 해지한 콜백이
    // 같은 알림에서 한 번 더 불린다. 이때 던지면 세션의 알림 반복과 그 안에서 도는 세션 코드가 깨진다
    @Test
    fun staleSessionNotificationAfterDetachIsIgnored() {
        open()
        val staleRemote = session.remoteListeners.toList()
        val staleSync = session.syncListeners.toList()
        router?.dispatchDetach()
        router = null

        staleRemote.forEach { it() }
        staleSync.forEach { it(null) }
        dispatcher.scheduler.runCurrent()
    }
}

private val NOW: Instant = Instant.parse("2026-10-31T00:00:00Z")

private object FixedClock : Clock {
    override fun now(): Instant = NOW
}

private const val STAMP = "2026-10-31T00:00:00Z"

private fun itemJson(id: String, name: String, username: String, password: String, extra: String = "") =
    """{"id":"$id","serviceName":"$name","username":"$username","password":"$password"$extra,"createdAt":"$STAMP","updatedAt":"$STAMP"}"""

private fun itemOf(id: String, name: String, username: String, password: String) = itemJson(id, name, username, password)

private fun vaultOf(vararg itemJsons: String): VaultData =
    parseVaultData("""{"version":1,"updatedAt":"$STAMP","items":[${itemJsons.joinToString(",")}]}""")

private fun emptyData(): VaultData = vaultOf()

// 새 버전 앱이 넣은 필드(totp)는 화면에서 고쳐도 남아야 한다
private fun sampleVault(): VaultData = vaultOf(
    itemJson("gh", "GitHub", "octo", "gh-secret", extra = ",\"url\":\"javascript:alert(1)\",\"notes\":\"회사 계정\",\"totp\":\"FUTURE\""),
    itemJson("ap", "Apple", "tim", "apple-secret"),
)

private class SingleDispatcher(dispatcher: CoroutineDispatcher) : RibDispatchersProvider {
    override val Main: CoroutineDispatcher = dispatcher
    override val MainImmediate: CoroutineDispatcher = dispatcher
    override val Default: CoroutineDispatcher = dispatcher
    override val Unconfined: CoroutineDispatcher = dispatcher
}

private class FakeVaultListView : VaultListPresentable {
    override var listener: VaultListPresentableListener? = null
    val states = mutableListOf<VaultListViewState>()
    val shownGenerators = mutableListOf<ViewControllable>()
    val hiddenGenerators = mutableListOf<ViewControllable>()

    override fun render(state: VaultListViewState) {
        states += state
    }

    override fun showGenerator(view: ViewControllable) {
        shownGenerators += view
    }

    override fun hideGenerator(view: ViewControllable) {
        hiddenGenerators += view
    }
}

private class FakeGeneratorView : PasswordGeneratorPresentable {
    override var listener: PasswordGeneratorPresentableListener? = null
    val states = mutableListOf<PasswordGeneratorViewState>()
    override fun render(state: PasswordGeneratorViewState) {
        states += state
    }
}

private class FakeClipboard : ClipboardPort {
    val copied = mutableListOf<String>()
    var failure: Throwable? = null
    override suspend fun copy(text: String) {
        failure?.let { throw it }
        copied += text
    }
}

/** 저장이 끝나는 시점과 결과를 테스트가 정하는 세션. 쓰지 않는 기능은 부르면 실패한다 */
private class FakeSession(initial: VaultData, private var readOnly: Boolean) : SessionPort {
    var data: VaultData = initial
        private set
    var current: SessionState = unlocked()
    val saved = mutableListOf<VaultData>()
    var saveCalls = 0
        private set
    var failNext: Throwable? = null
    var gate: CompletableDeferred<Unit>? = null
    val remoteListeners = mutableSetOf<() -> Unit>()
    val syncListeners = mutableSetOf<SyncChangeListener>()

    private fun unlocked() = SessionState.Unlocked(
        email = "a@b.c",
        vault = SyncedVault(revision = 1, data = data, readOnly = readOnly, payload = null),
        pendingSync = false,
        signedIn = true,
        storedOnDevice = true,
    )

    /** 다른 기기의 변경이 병합된 것처럼 세션의 볼트를 바꾼다 */
    fun replace(next: VaultData, readOnly: Boolean = this.readOnly) {
        data = next
        this.readOnly = readOnly
        current = unlocked()
    }

    fun fireRemoteChange() = remoteListeners.toList().forEach { it() }
    fun fireSyncChange(error: Throwable?) = syncListeners.toList().forEach { it(error) }

    override fun state(): SessionState = current

    override suspend fun save(data: VaultData) {
        saveCalls++
        gate?.await()
        failNext?.let {
            failNext = null
            throw it
        }
        saved += data
        replace(data)
    }

    override fun onRemoteChange(listener: () -> Unit): () -> Unit {
        remoteListeners += listener
        return { remoteListeners -= listener }
    }

    override fun onSyncChange(listener: SyncChangeListener): () -> Unit {
        syncListeners += listener
        return { syncListeners -= listener }
    }

    override suspend fun register(email: String, password: String): Unit = unused()
    override suspend fun login(email: String, password: String, acceptRollback: Boolean): Unit = unused()
    override fun lock(scope: LockScope): Unit = unused()
    override suspend fun unlock(password: String, acceptRollback: Boolean): Boolean = unused()
    override suspend fun reconnect(password: String, acceptRollback: Boolean): Unit = unused()
    override suspend fun exportBackup(now: Instant, calendar: LocalCalendar): BackupFile = unused()
    override suspend fun restoreBackup(backup: VaultBackup, password: String, now: Instant): Int = unused()
    override suspend fun changeMasterPassword(currentPassword: String, newPassword: String): Unit = unused()
    override suspend fun deleteAccount(password: String): Unit = unused()
    override suspend fun logout(): Unit = unused()
    override suspend fun pendingOnDevice(): Boolean = unused()
    override fun onLock(listener: () -> Unit): () -> Unit = unused()
    override fun onUnlock(listener: () -> Unit): () -> Unit = unused()

    private fun unused(): Nothing = error("이 테스트에서 쓰지 않는 기능")
}
