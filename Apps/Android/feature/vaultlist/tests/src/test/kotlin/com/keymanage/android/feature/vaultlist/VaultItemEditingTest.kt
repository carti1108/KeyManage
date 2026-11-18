package com.keymanage.android.feature.vaultlist

import com.keymanage.android.domain.clipboard.ClipboardBurner
import com.keymanage.android.domain.remote.ApiError
import com.keymanage.android.domain.remote.ServerErrorCode
import com.keymanage.android.domain.sync.SyncError
import com.keymanage.android.domain.vault.ItemFields
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultItem
import com.keymanage.android.feature.vaultlist.impl.VaultListInteractor
import com.keymanage.android.feature.vaultlist.impl.VaultListPresenter
import com.keymanage.android.feature.vaultlist.impl.VaultListViewAction
import com.keymanage.android.feature.vaultlist.impl.VaultListViewState
import com.keymanage.android.feature.vaultlist.impl.VaultMessages
import com.keymanage.android.feature.vaultlist.impl.VaultScreen
import com.keymanage.android.feature.vaultlist.testing.FakeClipboard
import com.keymanage.android.feature.vaultlist.testing.HeldScheduler
import com.keymanage.android.feature.vaultlist.testing.RecordingVaultListListener
import com.keymanage.android.feature.vaultlist.testing.RecordingVaultSaver
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VaultItemEditingTest {
    private val t0 = Instant.parse("2026-11-01T00:00:00Z")
    private val now = Instant.parse("2026-11-18T09:00:00Z")
    private val github = VaultItem(id = "g", serviceName = "GitHub", username = "octo", password = "secret", createdAt = t0, updatedAt = t0)
    private val saver = RecordingVaultSaver()
    private val listener = RecordingVaultListListener()
    private val clipboard = FakeClipboard()
    private val scheduler = HeldScheduler()
    private val store = ViewStateStore(VaultListViewState())

    private fun interactor(vault: VaultData = VaultData(1, t0, listOf(github))) = VaultListInteractor(
        VaultListPresenter(store, ViewActionStream()), store, ViewActionStream(), vault, saver,
        ClipboardBurner(clipboard, scheduler, now = { 0L }), listener, UnconfinedTestDispatcher(), clock = { now },
    )

    @Test
    fun `항목을 열면 상세에 필드를 보이고 뒤로 가면 목록으로 돌아간다`() {
        val interactor = interactor()
        interactor.handle(VaultListViewAction.OpenItem("g"))
        assertEquals(VaultScreen.Detail("g", ItemFields("GitHub", "octo", "secret")), store.current.screen)
        interactor.handle(VaultListViewAction.Back)
        assertEquals(VaultScreen.List, store.current.screen)
    }

    @Test
    fun `비밀번호를 복사하면 30초 배지를 보이고 타이머를 건다`() {
        val interactor = interactor()
        interactor.handle(VaultListViewAction.OpenItem("g"))
        interactor.handle(VaultListViewAction.CopyPassword)
        assertEquals("secret", clipboard.content)
        assertEquals(30, store.current.burnSeconds)
        assertEquals(1, scheduler.scheduled)
    }

    @Test
    fun `복사에 실패하면 COM-001 문구를 보인다`() {
        val interactor = interactor()
        clipboard.failCopy = true
        interactor.handle(VaultListViewAction.OpenItem("g"))
        interactor.handle(VaultListViewAction.CopyUsername)
        assertEquals(VaultMessages.COPY_FAILED, store.current.errorMessage)
    }

    @Test
    fun `서비스 이름 없이 추가하면 저장하지 않고 문구를 보인다`() = runTest {
        val interactor = interactor()
        interactor.handle(VaultListViewAction.StartAdd)
        interactor.save()
        assertEquals(VaultMessages.MISSING_SERVICE_NAME, store.current.errorMessage)
        assertTrue(saver.saved.isEmpty())
    }

    @Test
    fun `항목을 추가하면 저장하고 그 항목 상세로 간다`() = runTest {
        // Given
        val interactor = interactor()
        interactor.handle(VaultListViewAction.StartAdd)
        interactor.handle(VaultListViewAction.FormChanged(ItemFields("Notion", "dev", "pw")))

        // When
        interactor.save()

        // Then
        assertEquals(2, saver.saved.single().visibleItems.size)
        assertEquals("Notion", (store.current.screen as VaultScreen.Detail).fields.serviceName)
        assertEquals(2, store.current.totalCount)
    }

    @Test
    fun `바뀐 것 없이 저장하면 서버에 보내지 않고 상세로 돌아간다`() = runTest {
        val interactor = interactor()
        interactor.handle(VaultListViewAction.OpenItem("g"))
        interactor.handle(VaultListViewAction.StartEdit)
        interactor.save()
        assertTrue(saver.saved.isEmpty())
        assertIs<VaultScreen.Detail>(store.current.screen)
    }

    @Test
    fun `수정하면 바뀐 내용을 저장한다`() = runTest {
        val interactor = interactor()
        interactor.handle(VaultListViewAction.OpenItem("g"))
        interactor.handle(VaultListViewAction.StartEdit)
        interactor.handle(VaultListViewAction.FormChanged(ItemFields("GitHub", "octo", "new")))
        interactor.save()
        assertEquals("new", saver.saved.single().items.single().password)
        assertEquals("new", (store.current.screen as VaultScreen.Detail).fields.password)
    }

    @Test
    fun `삭제는 확인 뒤 저장하고 목록으로 돌아간다`() = runTest {
        // Given
        val interactor = interactor()
        interactor.handle(VaultListViewAction.OpenItem("g"))
        interactor.handle(VaultListViewAction.RequestDelete)
        assertTrue(store.current.isConfirmingDelete)

        // When
        interactor.delete()

        // Then
        assertTrue(saver.saved.single().items.single().isDeleted)
        assertEquals(VaultScreen.List, store.current.screen)
        assertEquals(0, store.current.totalCount)
    }

    @Test
    fun `저장 실패는 VLT-001 문구로 보이고 폼에 남는다`() = runTest {
        val interactor = interactor()
        saver.failure = SyncError.VaultTooLarge(6_000_000)
        interactor.handle(VaultListViewAction.StartAdd)
        interactor.handle(VaultListViewAction.FormChanged(ItemFields("Notion", "", "")))
        interactor.save()
        assertEquals(VaultMessages.TOO_LARGE, store.current.errorMessage)
        assertIs<VaultScreen.Edit>(store.current.screen)
        assertFalse(store.current.isSaving)
    }

    @Test
    fun `저장 중 세션이 만료되면 다시 입력을 요청한다`() = runTest {
        val interactor = interactor()
        saver.failure = ApiError.Server(401, ServerErrorCode.UNAUTHORIZED, null)
        interactor.handle(VaultListViewAction.StartAdd)
        interactor.handle(VaultListViewAction.FormChanged(ItemFields("Notion", "", "")))
        interactor.save()
        assertEquals(VaultMessages.SESSION_EXPIRED, store.current.errorMessage)
        assertEquals(1, listener.resyncRequests)
    }

    @Test
    fun `읽기 전용 볼트는 추가·수정·삭제를 막는다`() {
        val interactor = interactor(VaultData(2, t0, listOf(github)))
        assertTrue(store.current.isReadOnly)
        interactor.handle(VaultListViewAction.StartAdd)
        assertEquals(VaultScreen.List, store.current.screen)
        interactor.handle(VaultListViewAction.OpenItem("g"))
        interactor.handle(VaultListViewAction.StartEdit)
        interactor.handle(VaultListViewAction.RequestDelete)
        assertIs<VaultScreen.Detail>(store.current.screen)
        assertFalse(store.current.isConfirmingDelete)
    }
}
