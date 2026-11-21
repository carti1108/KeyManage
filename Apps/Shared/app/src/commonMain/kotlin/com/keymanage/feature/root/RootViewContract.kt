package com.keymanage.feature.root

import com.carti.ribs.ViewControllable

/** 루트 뷰 계약. 자식 RIB 화면 하나를 보여 준다. 플랫폼이 구현한다. Interactor가 그릴 상태가 없어 Presentable은 없다 */
interface RootViewControllable : ViewControllable {
    fun showScreen(view: ViewControllable)
    fun hideScreen(view: ViewControllable)
}

fun interface RootViewFactory {
    fun makeRootView(): RootViewControllable
}
