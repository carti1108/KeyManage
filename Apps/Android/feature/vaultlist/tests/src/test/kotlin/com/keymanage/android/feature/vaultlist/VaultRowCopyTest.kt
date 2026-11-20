package com.keymanage.android.feature.vaultlist

import com.keymanage.android.domain.clipboard.ClipboardBurner
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
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 목록 줄의 "복사"(Android 목업 4단계). 상세를 열지 않고 비밀번호를 복사하고 30초 뒤 비운다(COM-001). */
class VaultRowCopyTest {
    private val t0 = Instant.parse("2026-11-01T00:00:00Z")
    private val github = VaultItem(id = "g", serviceName = "GitHub", username = "octo", password = "secret", createdAt = t0, updatedAt = t0)
    private val clipboard = FakeClipboard()
    private val store = ViewStateStore(VaultListViewState())
    private val interactor = VaultListInteractor(
        VaultListPresenter(store, ViewActionStream()), store, ViewActionStream(), VaultData(1, t0, listOf(github)), RecordingVaultSaver(),
        ClipboardBurner(clipboard, HeldScheduler(), now = { 0L }), RecordingVaultListListener(),
        UnconfinedTestDispatcher(), clock = { t0 },
    )

    @Test
    fun `목록에서 복사하면 그 항목의 비밀번호를 복사하고 목록에 남는다`() {
        interactor.handle(VaultListViewAction.CopyItemPassword("g"))

        assertEquals("secret", clipboard.content)
        assertNotNull(store.current.burnSeconds)
        assertEquals(VaultScreen.List, store.current.screen)
    }

    @Test
    fun `없는 항목이면 아무것도 복사하지 않는다`() {
        interactor.handle(VaultListViewAction.CopyItemPassword("missing"))

        assertEquals("", clipboard.content)
        assertNull(store.current.burnSeconds)
    }

    @Test
    fun `복사에 실패하면 문구로 알린다`() {
        clipboard.failCopy = true
        interactor.handle(VaultListViewAction.CopyItemPassword("g"))

        assertEquals(VaultMessages.COPY_FAILED, store.current.errorMessage)
    }
}
