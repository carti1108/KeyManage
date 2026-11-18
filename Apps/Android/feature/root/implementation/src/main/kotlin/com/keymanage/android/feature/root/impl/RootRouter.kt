package com.keymanage.android.feature.root.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.ComposeView
import com.keymanage.android.feature.auth.AuthBuildable
import com.keymanage.android.feature.auth.AuthListener
import com.uber.rib.core.BasicViewRouter
import com.uber.rib.core.Router

/**
 * Root RIB 라우터. 하위 RIB(BasicComposeRouter)은 [childSlot]에 화면을 채운다.
 * 자동 잠금(SEC-003) 때 LoggedIn 하위 트리를 detach하는 일도 이 라우터가 맡는다.
 */
class RootRouter(
    view: ComposeView,
    interactor: RootInteractor,
    val childSlot: MutableState<@Composable () -> Unit>,
    private val authBuildable: AuthBuildable,
) : BasicViewRouter<ComposeView, RootInteractor>(view, interactor) {
    private var auth: Router<*>? = null

    fun attachAuth(listener: AuthListener) {
        if (auth != null) return
        auth = authBuildable.build(childSlot, listener).also(::attachChild)
    }

    fun detachAuth() {
        auth?.let(::detachChild)
        auth = null
    }
}
