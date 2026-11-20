package com.keymanage.android.feature.vaultlist

import com.keymanage.android.domain.clipboard.ClipboardBurner
import com.keymanage.android.domain.sync.SyncError
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** VLT-001 규칙 9: 사용자가 확인한 경우에만 되돌아간 서버 볼트를 받아들인다. */
class VaultRollbackAcceptTest {
    private val t0 = Instant.parse("2026-11-01T00:00:00Z")
    private val github = VaultItem(id = "g", serviceName = "GitHub", username = "octo", password = "secret", createdAt = t0, updatedAt = t0)
    private val saver = RecordingVaultSaver()
    private val store = ViewStateStore(VaultListViewState())
    private val interactor = VaultListInteractor(
        VaultListPresenter(store, ViewActionStream()), store, ViewActionStream(), VaultData(1, t0, listOf(github)), saver,
        ClipboardBurner(FakeClipboard(), HeldScheduler(), now = { 0L }), RecordingVaultListListener(),
        UnconfinedTestDispatcher(), clock = { t0 },
    )

    private suspend fun deleteIntoRollback() {
        interactor.handle(VaultListViewAction.OpenItem("g"))
        saver.failure = SyncError.RevisionRollback(serverRevision = 1, lastSeenRevision = 3)
        interactor.delete()
        saver.failure = null
    }

    @Test
    fun `되돌림 경고에서 받아들이면 그 revision을 받아들이고 같은 저장을 다시 한다`() = runTest {
        // Given
        deleteIntoRollback()
        assertTrue(store.current.canAcceptRollback)
        assertTrue(saver.acceptedRollbacks.isEmpty())

        // When
        interactor.acceptRollback()

        // Then
        assertEquals(listOf(1), saver.acceptedRollbacks)
        assertEquals(1, saver.saved.size)
        assertTrue(saver.saved.single().visibleItems.isEmpty())
        assertEquals(VaultScreen.List, store.current.screen)
        assertFalse(store.current.canAcceptRollback)
        assertNull(store.current.errorMessage)
        assertNull(store.current.errorTitle)
    }

    @Test
    fun `취소하면 받아들이지 않고 다시 저장하지도 않는다`() = runTest {
        // Given
        deleteIntoRollback()

        // When
        interactor.handle(VaultListViewAction.DismissError)
        interactor.acceptRollback()

        // Then
        assertFalse(store.current.canAcceptRollback)
        assertTrue(saver.acceptedRollbacks.isEmpty())
        assertTrue(saver.saved.isEmpty())
    }

    @Test
    fun `되돌림이 아닌 오류에는 받아들이기를 보이지 않는다`() = runTest {
        interactor.handle(VaultListViewAction.OpenItem("g"))
        saver.failure = SyncError.ConflictRetriesExhausted(3)
        interactor.delete()
        assertFalse(store.current.canAcceptRollback)
    }

    @Test
    fun `버튼 문구는 정책 원문과 같다`() {
        assertEquals("서버 볼트 받아들이기", VaultMessages.ROLLBACK_ACCEPT)
    }
}
