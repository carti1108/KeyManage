package com.keymanage.feature.vaultlist.testing

import com.carti.ribs.ViewControllable
import com.keymanage.feature.vaultlist.VaultListPresentableListener
import com.keymanage.feature.vaultlist.VaultListViewContract
import com.keymanage.feature.vaultlist.VaultListViewState

/** 그린 상태를 모두 기록하는 가짜 뷰 */
class FakeVaultListView : VaultListViewContract {
    override var listener: VaultListPresentableListener? = null
    val states = mutableListOf<VaultListViewState>()

    override fun render(state: VaultListViewState) {
        states += state
    }

    override fun showGenerator(view: ViewControllable) = Unit
    override fun hideGenerator(view: ViewControllable) = Unit
}
