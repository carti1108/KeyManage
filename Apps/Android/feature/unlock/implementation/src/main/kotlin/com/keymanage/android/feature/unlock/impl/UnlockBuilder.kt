package com.keymanage.android.feature.unlock.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.feature.unlock.UnlockBuildable
import com.keymanage.android.feature.unlock.UnlockDependency
import com.keymanage.android.feature.unlock.UnlockListener
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.BasicComposeRouter
import com.uber.rib.core.ComposePresenter
import com.uber.rib.core.Router

/** 부모 slot에 붙는 잠금 화면. */
class UnlockPresenter(
    private val store: ViewStateStore<UnlockViewState>,
    private val actions: ViewActionStream<UnlockViewAction>,
) : ComposePresenter() {
    override val composable: @Composable () -> Unit = {
        val state by store.state.collectAsState()
        UnlockView(state = state, onAction = { actions.send(it) })
    }
}

class UnlockBuilder(private val dependency: UnlockDependency) : UnlockBuildable {
    override fun build(
        slot: MutableState<@Composable () -> Unit>,
        email: String,
        session: Session?,
        listener: UnlockListener,
    ): Router<*> {
        val store = ViewStateStore(UnlockViewState(email = email))
        val actions = ViewActionStream<UnlockViewAction>()
        val presenter = UnlockPresenter(store, actions)
        val interactor = UnlockInteractor(presenter, store, actions, session, dependency.unlocker, listener, dependency.workDispatcher, dependency.biometricUnlocker)
        return BasicComposeRouter(presenter, interactor, slot)
    }
}
