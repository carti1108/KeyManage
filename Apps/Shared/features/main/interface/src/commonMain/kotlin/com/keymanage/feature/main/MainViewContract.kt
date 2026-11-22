@file:OptIn(ExperimentalObjCName::class)

package com.keymanage.feature.main

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.carti.ribs.Presentable
import com.carti.ribs.ViewControllable

interface MainPresentableListener {
    fun handle(@ObjCName("mainAction") action: MainViewAction)
}

/** Interactor가 쓰는 화면 계약 (RIBs-iOS Presentable, carti ADR-0010) */
interface MainPresentable : Presentable {
    /** 활성 구간에만 설정된다. 비활성이면 null이라 입력이 무시된다 */
    var listener: MainPresentableListener?
    fun render(@ObjCName("mainState") state: MainViewState)
}

/** Router가 쓰는 화면 계약 */
interface MainViewControllable : ViewControllable {
    /** 고른 탭의 자식 화면을 보인다. 이전 탭의 화면은 먼저 [hideScreen]으로 내린다 */
    fun showScreen(view: ViewControllable)
    fun hideScreen(view: ViewControllable)

    /**
     * 사이드바 아래 상자에 설정 화면(자동 잠금·백업·비밀번호 변경·계정 삭제)을 늘 두는 화면이면 true다(macOS 데스크톱 시안).
     * 그러면 Router가 설정 RIB을 처음부터 붙여 [showSidebarSettings]로 넘기고, 설정 탭도 같은 RIB 화면을 쓴다
     */
    val usesSidebarSettings: Boolean get() = false

    fun showSidebarSettings(view: ViewControllable) {}
}

/** 플랫폼 뷰가 구현하는 둘의 합 */
interface MainViewContract : MainPresentable, MainViewControllable

fun interface MainViewFactory {
    fun makeMainView(): MainViewContract
}
