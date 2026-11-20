package com.keymanage.feature.vaultlist

import com.carti.ribs.ViewableRouter
import com.keymanage.feature.generator.PasswordGeneratorBuildable
import com.keymanage.feature.generator.PasswordGeneratorRouting

class VaultListRouter(
    interactor: VaultListInteractor,
    view: VaultListViewControllable,
    private val generatorBuilder: PasswordGeneratorBuildable,
) : ViewableRouter<VaultListInteractor, VaultListViewControllable>(interactor, view), VaultListRouting {
    private var generator: PasswordGeneratorRouting? = null

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
    override fun refreshFromSession() = interactor.reloadFromSession()

    fun routeAwayFromGenerator() {
        val child = generator ?: return
        detachChild(child)
        viewController.hideGenerator(child.viewControllable)
        generator = null
    }
}
