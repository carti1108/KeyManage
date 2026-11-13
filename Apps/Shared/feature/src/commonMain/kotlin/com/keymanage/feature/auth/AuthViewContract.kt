package com.keymanage.feature.auth

import com.carti.ribs.Presentable
import com.carti.ribs.ViewControllable

interface AuthPresentableListener {
    fun handle(action: AuthViewAction)
}

/** Interactor가 쓰는 화면 계약 (RIBs-iOS Presentable, carti ADR-0010) */
interface AuthPresentable : Presentable {
    /** 활성 구간에만 설정된다. 비활성이면 null이라 입력이 무시된다 */
    var listener: AuthPresentableListener?
    fun render(state: AuthViewState)
}

/** Router가 쓰는 화면 계약 */
interface AuthViewControllable : ViewControllable

/** 플랫폼 뷰가 구현하는 둘의 합 */
interface AuthViewContract : AuthPresentable, AuthViewControllable

fun interface AuthViewFactory {
    fun makeAuthView(): AuthViewContract
}
