package com.keymanage.android.feature.main

import com.keymanage.android.domain.lock.AutoLockOption
import com.keymanage.android.domain.session.SavedVault
import com.keymanage.android.domain.sync.SaveOutcome
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.main.impl.MainInteractor
import com.keymanage.android.feature.main.impl.MainPresenter
import com.keymanage.android.feature.main.impl.MainRouting
import com.keymanage.android.feature.main.impl.MainTab
import com.keymanage.android.feature.main.impl.MainViewAction
import com.keymanage.android.feature.main.impl.MainViewState
import com.keymanage.android.feature.vaultlist.VaultSaver
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MainInteractorTest {
    private val initial = VaultData(1, Instant.EPOCH, emptyList())
    private val saved = VaultData(1, Instant.parse("2026-11-18T00:00:00Z"), emptyList())
    private val shown = mutableListOf<String>()
    private var shownVault: VaultData? = null

    private val routing = object : MainRouting {
        override fun showVault(vault: VaultData) {
            shown += "vault"
            shownVault = vault
        }

        override fun showGenerator() {
            shown += "generator"
        }

        override fun showSettings() {
            shown += "settings"
        }
    }

    private val listener = object : MainListener {
        override fun onLockRequested() = Unit
        override fun onResyncRequested() = Unit
        override fun onAutoLockChanged(option: AutoLockOption) = Unit
        override fun onLogoutRequested() = Unit
        override fun onAccountDeleted() = Unit
        override fun onSessionExpired() = Unit
    }

    private val store = ViewStateStore(MainViewState())
    private val interactor = MainInteractor(
        MainPresenter(store, ViewActionStream()), store, ViewActionStream(), initial,
        VaultSaver { SavedVault(saved, SaveOutcome.Synced(2)) }, listener, routing,
    )

    @Test
    fun `탭을 고르면 그 탭 RIB을 붙이고 같은 탭은 다시 붙이지 않는다`() {
        interactor.handle(MainViewAction.SelectTab(MainTab.GENERATOR))
        interactor.handle(MainViewAction.SelectTab(MainTab.GENERATOR))
        interactor.handle(MainViewAction.SelectTab(MainTab.SETTINGS))
        assertEquals(listOf("generator", "settings"), shown)
        assertEquals(MainTab.SETTINGS, store.current.tab)
    }

    @Test
    fun `볼트 탭으로 돌아오면 마지막으로 저장한 볼트를 보인다`() {
        interactor.save(initial)
        interactor.handle(MainViewAction.SelectTab(MainTab.GENERATOR))
        interactor.handle(MainViewAction.SelectTab(MainTab.VAULT))
        assertSame(saved, shownVault)
    }
}
