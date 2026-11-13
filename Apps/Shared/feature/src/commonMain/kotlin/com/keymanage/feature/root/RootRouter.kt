package com.keymanage.feature.root

import com.carti.ribs.LaunchRouter
import com.carti.ribs.Routing
import com.carti.ribs.ViewControllable
import com.keymanage.feature.auth.AuthBuilder
import com.keymanage.feature.auth.AuthRouter
import com.keymanage.feature.main.MainBuilder
import com.keymanage.feature.main.MainRouter
import com.keymanage.feature.unlock.UnlockBuilder
import com.keymanage.feature.unlock.UnlockRouter

class RootRouter(
    interactor: RootInteractor,
    view: RootViewControllable,
    private val authBuilder: AuthBuilder,
    private val unlockBuilder: UnlockBuilder,
    private val mainBuilder: MainBuilder,
) : LaunchRouter<RootInteractor, RootViewControllable>(interactor, view) {
    private var auth: AuthRouter? = null
    private var authNotice: String? = null
    private var unlock: UnlockRouter? = null
    private var main: MainRouter? = null

    init {
        interactor.router = this
    }

    val current: RootScreen?
        get() = when {
            main != null -> RootScreen.MAIN
            unlock != null -> RootScreen.UNLOCK
            auth != null -> RootScreen.AUTH
            else -> null
        }

    /** 볼트 화면이나 잠금 화면이 세션을 끝내는 작업(로그아웃, 계정 삭제)을 하는 중인지. 이 동안은 화면을 내리지 않는다 */
    val isFinishingSession: Boolean
        get() = (main?.interactor?.isFinishingSession ?: false) || (unlock?.interactor?.isFinishingSession ?: false)

    /** 끝낼 때 [RootInteractor]가 부른다. 붙어 있던 화면을 내리고 필드를 비운다. 자식 RIB은 트리를 끝내며 이어서 떼어진다 */
    fun clearScreens() {
        auth?.let { viewController.hideScreen(it.viewControllable) }
        unlock?.let { viewController.hideScreen(it.viewControllable) }
        main?.let { viewController.hideScreen(it.viewControllable) }
        auth = null
        authNotice = null
        unlock = null
        main = null
    }

    /**
     * @param notice 로그인 화면에 보일 안내. 이미 로그인 화면이 떠 있으면 화면을 다시 만들지 않는다(입력한 이메일이 사라지므로).
     * 안내가 있고 이미 보인 것과 다를 때만 떠 있는 화면에 안내를 갈아 끼운다
     * (안내가 화면을 만든 뒤에 도착하는 경우: 로그아웃 결과, 다른 곳의 비밀번호 변경)
     */
    fun routeToAuth(notice: String?) {
        auth?.let { shown ->
            if (notice != null && notice != authNotice) {
                shown.interactor.showNotice(notice)
                authNotice = notice
            }
            return
        }
        removeUnlock()
        removeMain()
        val child = authBuilder.build(interactor, notice)
        attachChild(child)
        viewController.showScreen(child.viewControllable)
        auth = child
        authNotice = notice
    }

    fun routeToUnlock() {
        if (unlock != null) return
        removeAuth()
        removeMain()
        val child = unlockBuilder.build(interactor)
        attachChild(child)
        viewController.showScreen(child.viewControllable)
        unlock = child
    }

    fun routeToMain(offline: Boolean) {
        if (main != null) return
        removeAuth()
        removeUnlock()
        val child = mainBuilder.build(interactor, offline)
        attachChild(child)
        viewController.showScreen(child.viewControllable)
        main = child
    }

    private fun removeAuth() {
        val child = auth ?: return
        remove(child, child.viewControllable)
        auth = null
        authNotice = null
    }

    private fun removeUnlock() {
        val child = unlock ?: return
        remove(child, child.viewControllable)
        unlock = null
    }

    private fun removeMain() {
        val child = main ?: return
        remove(child, child.viewControllable)
        main = null
    }

    private fun remove(child: Routing, screen: ViewControllable) {
        detachChild(child)
        viewController.hideScreen(screen)
    }
}
