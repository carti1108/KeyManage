package com.keymanage.android.feature.root.impl

import com.uber.rib.core.Bundle
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.BasicInteractor
import com.uber.rib.core.EmptyPresenter
import com.uber.rib.core.coroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class RootInteractor(
    presenter: EmptyPresenter,
    private val store: ViewStateStore<RootViewState>,
    private val actions: ViewActionStream<RootViewAction>,
) : BasicInteractor<EmptyPresenter, RootRouter>(presenter) {

    override fun didBecomeActive(savedInstanceState: Bundle?) {
        super.didBecomeActive(savedInstanceState)
        actions.actions.onEach(::handle).launchIn(coroutineScope)
    }

    /** View 동작 하나를 처리해 상태를 바꾼다. 테스트는 이 함수를 직접 부른다. */
    fun handle(action: RootViewAction) {
        when (action) {
            RootViewAction.ViewAppeared -> store.update { it.copy(hasAppeared = true) }
        }
    }
}
