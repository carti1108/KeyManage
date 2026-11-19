package com.keymanage.android.feature.vaultlist

import com.keymanage.android.domain.clipboard.ClipboardBurner
import com.keymanage.android.domain.sync.SaveOutcome
import com.keymanage.android.domain.sync.SyncError
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultItem
import com.keymanage.android.feature.vaultlist.impl.VaultListInteractor
import com.keymanage.android.feature.vaultlist.impl.VaultListPresenter
import com.keymanage.android.feature.vaultlist.impl.VaultListViewAction
import com.keymanage.android.feature.vaultlist.impl.VaultListViewState
import com.keymanage.android.feature.vaultlist.impl.VaultMessages
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** VLT-001 revision 역행 제목, VLT-002 오프라인 경고, COM-001 복사 직후 안내. */
class VaultStandardMessagesTest {
    private val t0 = Instant.parse("2026-11-01T00:00:00Z")
    private val github = VaultItem(id = "g", serviceName = "GitHub", username = "octo", password = "secret", createdAt = t0, updatedAt = t0)
    private val saver = RecordingVaultSaver()
    private val store = ViewStateStore(VaultListViewState())
    private val interactor = VaultListInteractor(
        VaultListPresenter(store, ViewActionStream()), store, ViewActionStream(), VaultData(1, t0, listOf(github)), saver,
        ClipboardBurner(FakeClipboard(), HeldScheduler(), now = { 0L }), RecordingVaultListListener(),
        UnconfinedTestDispatcher(), clock = { t0 },
    )

    @Test
    fun `저장이 대기로 남으면 오프라인 경고를 보이고 동기화되면 지운다`() = runTest {
        interactor.handle(VaultListViewAction.OpenItem("g"))
        saver.outcome = SaveOutcome.Pending
        interactor.delete()
        assertTrue(store.current.isPendingSync)

        interactor.handle(VaultListViewAction.StartAdd)
        saver.outcome = null
        interactor.handle(VaultListViewAction.FormChanged(com.keymanage.android.domain.vault.ItemFields("Bank", "me", "pw")))
        interactor.save()
        assertFalse(store.current.isPendingSync)
    }

    @Test
    fun `revision 역행이면 표준 제목과 본문을 보인다`() = runTest {
        interactor.handle(VaultListViewAction.OpenItem("g"))
        saver.failure = SyncError.RevisionRollback(serverRevision = 1, lastSeenRevision = 3)
        interactor.delete()
        assertEquals(VaultMessages.ROLLBACK_TITLE, store.current.errorTitle)
        assertEquals(VaultMessages.ROLLBACK, store.current.errorMessage)

        saver.failure = SyncError.ConflictRetriesExhausted(3)
        interactor.delete()
        assertNull(store.current.errorTitle)
    }

    @Test
    fun `표준 문구는 정책 원문과 같다`() {
        assertEquals("서버 볼트가 이전 상태로 돌아감", VaultMessages.ROLLBACK_TITLE)
        assertEquals("오프라인 상태입니다. 변경 사항이 서버에 동기화되지 않습니다.", VaultMessages.OFFLINE)
        assertEquals("클립보드 보안 소각", VaultMessages.CLIPBOARD_TITLE)
        assertEquals("보안을 위해 복사된 내용이 30초 후 클립보드에서 자동 삭제됩니다.", VaultMessages.CLIPBOARD_BODY)
    }
}
