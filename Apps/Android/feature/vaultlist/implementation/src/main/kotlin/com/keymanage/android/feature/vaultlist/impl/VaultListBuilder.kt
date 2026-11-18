package com.keymanage.android.feature.vaultlist.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.vaultlist.VaultListBuildable
import com.keymanage.android.feature.vaultlist.VaultListListener
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.BasicComposeRouter
import com.uber.rib.core.ComposePresenter
import com.uber.rib.core.Router

class VaultListPresenter(
    private val store: ViewStateStore<VaultListViewState>,
    private val actions: ViewActionStream<VaultListViewAction>,
) : ComposePresenter() {
    override val composable: @Composable () -> Unit = {
        val state by store.state.collectAsState()
        VaultListView(state = state, onAction = { actions.send(it) })
    }
}

class VaultListBuilder : VaultListBuildable {
    override fun build(
        slot: MutableState<@Composable () -> Unit>,
        vault: VaultData,
        isOffline: Boolean,
        listener: VaultListListener,
    ): Router<*> {
        val store = ViewStateStore(VaultListViewState(isOffline = isOffline))
        val actions = ViewActionStream<VaultListViewAction>()
        val presenter = VaultListPresenter(store, actions)
        return BasicComposeRouter(presenter, VaultListInteractor(presenter, store, actions, vault, listener), slot)
    }
}
