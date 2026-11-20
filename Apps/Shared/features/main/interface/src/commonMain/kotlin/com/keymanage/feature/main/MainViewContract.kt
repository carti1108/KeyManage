package com.keymanage.feature.main

import com.carti.ribs.Presentable
import com.carti.ribs.ViewControllable

interface MainPresentableListener {
    fun handle(action: MainViewAction)
}

/** Interactor가 쓰는 화면 계약 (RIBs-iOS Presentable, carti ADR-0010) */
interface MainPresentable : Presentable {
    /** 활성 구간에만 설정된다. 비활성이면 null이라 입력이 무시된다 */
    var listener: MainPresentableListener?
    fun render(state: MainViewState)
}

/** Router가 쓰는 화면 계약 */
interface MainViewControllable : ViewControllable {
    /** 고른 탭의 자식 화면을 보인다. 이전 탭의 화면은 먼저 [hideScreen]으로 내린다 */
    fun showScreen(view: ViewControllable)
    fun hideScreen(view: ViewControllable)
}

/** 플랫폼 뷰가 구현하는 둘의 합 */
interface MainViewContract : MainPresentable, MainViewControllable

fun interface MainViewFactory {
    fun makeMainView(): MainViewContract
}
