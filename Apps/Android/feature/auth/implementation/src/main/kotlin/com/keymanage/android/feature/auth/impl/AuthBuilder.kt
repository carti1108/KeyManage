package com.keymanage.android.feature.auth.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.keymanage.android.feature.auth.AuthBuildable
import com.keymanage.android.feature.auth.AuthDependency
import com.keymanage.android.feature.auth.AuthListener
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.BasicComposeRouter
import com.uber.rib.core.Router

/** Auth RIB 조립. 붙으면 부모 [slot]에 화면을 채우고, 떨어지면 비운다(BasicComposeRouter). */
class AuthBuilder(private val dependency: AuthDependency) : AuthBuildable {
    override fun build(slot: MutableState<@Composable () -> Unit>, listener: AuthListener): Router<*> {
        val store = ViewStateStore(AuthViewState())
        val actions = ViewActionStream<AuthViewAction>()
        val presenter = AuthPresenter(store, actions)
        val interactor = AuthInteractor(presenter, store, actions, dependency.authUseCase, listener, dependency.workDispatcher)
        return BasicComposeRouter(presenter, interactor, slot)
    }
}
