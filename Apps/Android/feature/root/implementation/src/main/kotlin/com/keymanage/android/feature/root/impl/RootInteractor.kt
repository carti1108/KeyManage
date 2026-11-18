package com.keymanage.android.feature.root.impl

import com.keymanage.android.domain.auth.AuthResult
import com.keymanage.android.feature.auth.AuthListener
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.BasicInteractor
import com.uber.rib.core.Bundle
import com.uber.rib.core.EmptyPresenter
import com.uber.rib.core.coroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** 앱 최상위 흐름. 처음에는 Auth를 붙이고, 인증이 끝나면 떼어 낸다. */
class RootInteractor(
    presenter: EmptyPresenter,
    private val store: ViewStateStore<RootViewState>,
    private val actions: ViewActionStream<RootViewAction>,
) : BasicInteractor<EmptyPresenter, RootRouter>(presenter), AuthListener {
    override fun didBecomeActive(savedInstanceState: Bundle?) {
        super.didBecomeActive(savedInstanceState)
        actions.actions.onEach(::handle).launchIn(coroutineScope)
        router.attachAuth(this)
    }

    /** View 동작 하나를 처리해 상태를 바꾼다. 테스트는 이 함수를 직접 부른다. */
    fun handle(action: RootViewAction) {
        when (action) {
            RootViewAction.ViewAppeared -> store.update { it.copy(hasAppeared = true) }
        }
    }

    /**
     * 인증 성공. Auth를 떼고 계정을 표시한다.
     * 볼트를 여는 LoggedIn RIB이 붙기 전이라 암호화 키는 쓰지 않고 바로 0으로 덮어쓴다(SEC-003).
     */
    override fun onAuthenticated(result: AuthResult) {
        result.encryptionKey.fill(0)
        if (isAttached()) router.detachAuth()
        store.update { it.copy(signedInEmail = result.account.email) }
    }
}
