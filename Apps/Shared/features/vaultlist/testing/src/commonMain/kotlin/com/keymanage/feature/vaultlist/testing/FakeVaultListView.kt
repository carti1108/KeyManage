package com.keymanage.feature.vaultlist.testing

import com.carti.ribs.ViewControllable
import com.keymanage.feature.vaultlist.VaultListPresentableListener
import com.keymanage.feature.vaultlist.VaultListViewContract
import com.keymanage.feature.vaultlist.VaultListViewState

/** 그린 상태와, 붙이고 뗀 생성기 화면을 모두 기록하는 가짜 뷰 */
class FakeVaultListView : VaultListViewContract {
    override var listener: VaultListPresentableListener? = null
    val states = mutableListOf<VaultListViewState>()
    val shownGenerators = mutableListOf<ViewControllable>()
    val hiddenGenerators = mutableListOf<ViewControllable>()

    override fun render(state: VaultListViewState) {
        states += state
    }

    override fun showGenerator(view: ViewControllable) {
        shownGenerators += view
    }

    override fun hideGenerator(view: ViewControllable) {
        hiddenGenerators += view
    }
}
