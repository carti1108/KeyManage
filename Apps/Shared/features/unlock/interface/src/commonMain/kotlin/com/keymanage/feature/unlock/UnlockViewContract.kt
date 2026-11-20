package com.keymanage.feature.unlock

import com.carti.ribs.Presentable
import com.carti.ribs.ViewControllable

interface UnlockPresentableListener {
    fun handle(action: UnlockViewAction)
}

/** Interactor가 쓰는 화면 계약 (RIBs-iOS Presentable, carti ADR-0010) */
interface UnlockPresentable : Presentable {
    /** 활성 구간에만 설정된다. 비활성이면 null이라 입력이 무시된다 */
    var listener: UnlockPresentableListener?
    fun render(state: UnlockViewState)
}

/** Router가 쓰는 화면 계약 */
interface UnlockViewControllable : ViewControllable

/** 플랫폼 뷰가 구현하는 둘의 합 */
interface UnlockViewContract : UnlockPresentable, UnlockViewControllable

fun interface UnlockViewFactory {
    fun makeUnlockView(): UnlockViewContract
}
