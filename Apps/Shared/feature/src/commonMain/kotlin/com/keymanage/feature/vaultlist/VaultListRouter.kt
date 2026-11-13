package com.keymanage.feature.vaultlist

import com.carti.ribs.ViewableRouter
import com.keymanage.feature.generator.PasswordGeneratorBuilder
import com.keymanage.feature.generator.PasswordGeneratorRouter

class VaultListRouter(
    interactor: VaultListInteractor,
    view: VaultListViewControllable,
    private val generatorBuilder: PasswordGeneratorBuilder,
) : ViewableRouter<VaultListInteractor, VaultListViewControllable>(interactor, view) {
    private var generator: PasswordGeneratorRouter? = null

    init {
        interactor.router = this
    }

    /** 폼 안에서 쓰는 생성기라 사용·닫기만 두고 복사는 두지 않는다 */
    fun routeToGenerator() {
        if (generator != null) return
        val child = generatorBuilder.build(interactor, allowCopy = false)
        attachChild(child)
        viewController.showGenerator(child.viewControllable)
        generator = child
    }

    /** 세션이 알림 없이 볼트를 바꿨을 수 있을 때(복원·재연결·탭 전환) 부모가 부른다 */
    fun refreshFromSession() = interactor.reloadFromSession()

    fun routeAwayFromGenerator() {
        val child = generator ?: return
        detachChild(child)
        viewController.hideGenerator(child.viewControllable)
        generator = null
    }
}
