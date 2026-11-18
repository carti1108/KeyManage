package com.keymanage.android.feature.auth.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.ComposePresenter

/** 부모 slot에 붙는 Auth 화면. 상태를 그리고 동작을 [actions]로 보낸다. */
class AuthPresenter(
    private val store: ViewStateStore<AuthViewState>,
    private val actions: ViewActionStream<AuthViewAction>,
) : ComposePresenter() {
    override val composable: @Composable () -> Unit = {
        val state by store.state.collectAsState()
        AuthView(state = state, onAction = { actions.send(it) })
    }
}
