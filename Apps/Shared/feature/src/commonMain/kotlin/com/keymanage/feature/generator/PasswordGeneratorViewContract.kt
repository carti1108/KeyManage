package com.keymanage.feature.generator

import com.carti.ribs.Presentable
import com.carti.ribs.ViewControllable
import kotlin.native.ObjCName

interface PasswordGeneratorPresentableListener {
    @ObjCName("handlePasswordGenerator")
    fun handle(action: PasswordGeneratorViewAction)
}

/** Interactor가 쓰는 화면 계약 (RIBs-iOS Presentable, carti ADR-0010) */
interface PasswordGeneratorPresentable : Presentable {
    /** 활성 구간에만 설정된다. 비활성이면 null이라 입력이 무시된다 */
    var listener: PasswordGeneratorPresentableListener?
    @ObjCName("renderPasswordGenerator")
    fun render(state: PasswordGeneratorViewState)
}

/** Router가 쓰는 화면 계약 */
interface PasswordGeneratorViewControllable : ViewControllable

/** 플랫폼 뷰가 구현하는 둘의 합 */
interface PasswordGeneratorViewContract : PasswordGeneratorPresentable, PasswordGeneratorViewControllable

fun interface PasswordGeneratorViewFactory {
    fun makePasswordGeneratorView(): PasswordGeneratorViewContract
}
