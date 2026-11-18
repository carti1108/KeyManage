package com.keymanage.android.feature.vaultlist.impl

import com.keymanage.android.domain.vault.ItemList
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.vaultlist.VaultListListener
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.BasicComposeRouter
import com.uber.rib.core.BasicInteractor
import com.uber.rib.core.Bundle
import com.uber.rib.core.coroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** 목록 한 줄. 비밀번호·메모는 목록 상태에 담지 않는다. */
data class VaultRow(val id: String, val serviceName: String, val username: String?)

/** 볼트 목록 상태. */
data class VaultListViewState(
    val query: String = "",
    val rows: List<VaultRow> = emptyList(),
    val totalCount: Int = 0,
    val isOffline: Boolean = false,
)

sealed interface VaultListViewAction {
    data class QueryChanged(val value: String) : VaultListViewAction
    data object Lock : VaultListViewAction
    data object Resync : VaultListViewAction
}

/** 볼트 목록과 검색. 항목 추가·상세는 다음 단계에서 붙인다. */
class VaultListInteractor(
    presenter: VaultListPresenter,
    private val store: ViewStateStore<VaultListViewState>,
    private val actions: ViewActionStream<VaultListViewAction>,
    private val vault: VaultData,
    private val listener: VaultListListener,
) : BasicInteractor<VaultListPresenter, BasicComposeRouter<VaultListInteractor>>(presenter) {
    init {
        store.update { it.copy(rows = rowsFor(it.query), totalCount = vault.visibleItems.size) }
    }

    override fun didBecomeActive(savedInstanceState: Bundle?) {
        super.didBecomeActive(savedInstanceState)
        actions.actions.onEach(::handle).launchIn(coroutineScope)
    }

    fun handle(action: VaultListViewAction) {
        when (action) {
            is VaultListViewAction.QueryChanged -> store.update { it.copy(query = action.value, rows = rowsFor(action.value)) }
            VaultListViewAction.Lock -> listener.onLockRequested()
            VaultListViewAction.Resync -> listener.onResyncRequested()
        }
    }

    private fun rowsFor(query: String) = ItemList.list(vault, query).map { VaultRow(it.id, it.serviceName.orEmpty(), it.username) }
}
