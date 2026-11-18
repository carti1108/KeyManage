package com.keymanage.android.feature.vaultlist

import com.keymanage.android.domain.clipboard.ClipboardBurner
import com.keymanage.android.domain.generator.PasswordGenerator
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.vaultlist.impl.VaultListInteractor
import com.keymanage.android.feature.vaultlist.impl.VaultListPresenter
import com.keymanage.android.feature.vaultlist.impl.VaultListViewAction
import com.keymanage.android.feature.vaultlist.impl.VaultListViewState
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
import kotlin.test.assertTrue

class VaultGenerateTest {
    private val store = ViewStateStore(VaultListViewState())
    private val interactor = VaultListInteractor(
        VaultListPresenter(store, ViewActionStream()), store, ViewActionStream(), VaultData(1, Instant.EPOCH, emptyList()),
        RecordingVaultSaver(), ClipboardBurner(FakeClipboard(), HeldScheduler()), RecordingVaultListListener(),
        UnconfinedTestDispatcher(), clock = { Instant.EPOCH }, generator = PasswordGenerator(),
    )

    @Test
    fun `입력 폼에서 생성하면 기본 옵션 16자 비밀번호를 넣는다`() {
        interactor.handle(VaultListViewAction.StartAdd)
        interactor.handle(VaultListViewAction.GeneratePassword)
        val password = (store.current.screen as VaultScreen.Edit).fields.password
        assertEquals(16, password.length)
        assertTrue(password.any { it in PasswordGenerator.SYMBOLS })
    }

    @Test
    fun `폼이 아니면 생성하지 않는다`() {
        interactor.handle(VaultListViewAction.GeneratePassword)
        assertEquals(VaultScreen.List, store.current.screen)
    }
}
