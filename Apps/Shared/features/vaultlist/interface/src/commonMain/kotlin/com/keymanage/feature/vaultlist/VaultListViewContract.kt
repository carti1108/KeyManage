package com.keymanage.feature.vaultlist

import com.carti.ribs.Presentable
import com.carti.ribs.ViewControllable

interface VaultListPresentableListener {
    fun handle(action: VaultListViewAction)
}

/** Interactor가 쓰는 화면 계약 (RIBs-iOS Presentable, carti ADR-0010) */
interface VaultListPresentable : Presentable {
    /** 활성 구간에만 설정된다. 비활성이면 null이라 입력이 무시된다 */
    var listener: VaultListPresentableListener?
    fun render(state: VaultListViewState)
}

/** Router가 쓰는 화면 계약. 폼 안의 비밀번호 생성기(자식 RIB) 화면을 붙이고 뗀다 */
interface VaultListViewControllable : ViewControllable {
    fun showGenerator(view: ViewControllable)
    fun hideGenerator(view: ViewControllable)
}

/** 플랫폼 뷰가 구현하는 둘의 합 */
interface VaultListViewContract : VaultListPresentable, VaultListViewControllable

fun interface VaultListViewFactory {
    fun makeVaultListView(): VaultListViewContract
}
