package com.keymanage.android.feature.main.impl

import com.keymanage.android.domain.session.SavedVault
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.main.MainListener
import com.keymanage.android.feature.settings.BackupPort
import com.keymanage.android.feature.settings.RestoreOutcome
import com.keymanage.android.feature.vaultlist.VaultSaver
import java.time.Instant
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.BasicInteractor
import com.uber.rib.core.Bundle
import com.uber.rib.core.coroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** 하단 탭. */
enum class MainTab(val label: String) { VAULT("볼트"), GENERATOR("생성기"), SETTINGS("설정") }

data class MainViewState(val tab: MainTab = MainTab.VAULT)

sealed interface MainViewAction {
    data class SelectTab(val tab: MainTab) : MainViewAction
}

/** 탭에 붙일 하위 RIB. */
interface MainRouting {
    fun showVault(vault: VaultData)
    fun showGenerator()
    fun showSettings()
}

/**
 * 탭 전환. 탭을 바꾸면 이전 탭 RIB을 떼므로, 볼트 탭을 다시 붙일 때 쓰도록 마지막으로 저장한 볼트를 들고 있는다.
 * 저장은 키를 쥔 부모의 [parentSaver]에 맡긴다.
 */
class MainInteractor(
    presenter: MainPresenter,
    private val store: ViewStateStore<MainViewState>,
    private val actions: ViewActionStream<MainViewAction>,
    initialVault: VaultData,
    private val parentSaver: VaultSaver,
    private val listener: MainListener,
    private val routing: MainRouting? = null,
    private val parentBackup: BackupPort? = null,
) : BasicInteractor<MainPresenter, MainRouter>(presenter), VaultSaver, BackupPort {
    var vault: VaultData = initialVault
        private set

    private val nav: MainRouting get() = routing ?: router

    override fun didBecomeActive(savedInstanceState: Bundle?) {
        super.didBecomeActive(savedInstanceState)
        actions.actions.onEach(::handle).launchIn(coroutineScope)
        show(store.current.tab)
    }

    fun handle(action: MainViewAction) {
        when (action) {
            is MainViewAction.SelectTab -> {
                if (action.tab == store.current.tab) return
                store.update { it.copy(tab = action.tab) }
                show(action.tab)
            }
        }
    }

    override fun save(vault: VaultData): SavedVault = parentSaver.save(vault).also { this.vault = it.vault }

    override fun export(now: Instant): ByteArray = checkNotNull(parentBackup).export(now)

    /** 복원한 볼트를 들고 있다가 볼트 탭을 다시 붙일 때 쓴다. */
    override fun restore(bytes: ByteArray, password: String, now: Instant): RestoreOutcome =
        checkNotNull(parentBackup).restore(bytes, password, now).also { outcome -> outcome.vault?.let { vault = it } }

    val mainListener: MainListener get() = listener

    private fun show(tab: MainTab) = when (tab) {
        MainTab.VAULT -> nav.showVault(vault)
        MainTab.GENERATOR -> nav.showGenerator()
        MainTab.SETTINGS -> nav.showSettings()
    }
}
