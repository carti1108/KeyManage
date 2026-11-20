package com.keymanage.feature.main.testing

import com.carti.ribs.ViewControllable
import com.keymanage.feature.main.MainPresentableListener
import com.keymanage.feature.main.MainViewContract
import com.keymanage.feature.main.MainViewState

/** 그린 상태를 모두 기록하는 가짜 뷰 */
class FakeMainView : MainViewContract {
    override var listener: MainPresentableListener? = null
    val states = mutableListOf<MainViewState>()
    val state: MainViewState get() = states.last()
    val shown = mutableListOf<ViewControllable>()
    val hidden = mutableListOf<ViewControllable>()

    /** 지금 보이는 자식 화면 */
    val visible: List<ViewControllable> get() = shown.filter { view -> shown.count { it === view } > hidden.count { it === view } }.distinct()

    override fun render(state: MainViewState) {
        states += state
    }

    override fun showScreen(view: ViewControllable) {
        shown += view
    }

    override fun hideScreen(view: ViewControllable) {
        hidden += view
    }
}
