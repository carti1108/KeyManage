package com.keymanage.feature.auth.testing

import com.keymanage.feature.auth.AuthPresentableListener
import com.keymanage.feature.auth.AuthViewContract
import com.keymanage.feature.auth.AuthViewState

/** 그린 상태를 모두 기록하는 가짜 뷰 */
class FakeAuthView : AuthViewContract {
    override var listener: AuthPresentableListener? = null
    val states = mutableListOf<AuthViewState>()
    val state: AuthViewState get() = states.last()

    override fun render(state: AuthViewState) {
        states += state
    }
}
