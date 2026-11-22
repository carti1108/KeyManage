package com.keymanage.feature.main

import com.carti.ribs.ViewControllable
import com.carti.ribs.ViewableRouter
import com.keymanage.feature.generator.PasswordGeneratorBuildable
import com.keymanage.feature.settings.SettingsBuildable
import com.keymanage.feature.vaultlist.VaultListBuildable
import com.keymanage.feature.vaultlist.VaultListRouting

class MainRouter(
    interactor: MainInteractor,
    view: MainViewControllable,
    private val vaultListBuilder: VaultListBuildable,
    private val passwordGeneratorBuilder: PasswordGeneratorBuildable,
    private val settingsBuilder: SettingsBuildable,
) : ViewableRouter<MainInteractor, MainViewControllable>(interactor, view), MainRouting {
    override val isFinishingSession: Boolean get() = interactor.isFinishingSession

    private var vaultList: VaultListRouting? = null
    private var generator: ViewControllable? = null
    private var settings: ViewControllable? = null
    private var shown: ViewControllable? = null

    init {
        interactor.router = this
    }

    /** 고른 탭의 화면만 보인다. 처음 고른 탭은 이때 붙이고, 이후에는 붙어 있는 화면을 다시 보인다 */
    fun routeTo(tab: MainTab) {
        val screen = when (tab) {
            MainTab.VAULT -> (vaultList ?: attachVaultList().also { vaultList = it }).viewControllable
            MainTab.GENERATOR -> generator ?: attachGenerator().also { generator = it }
            MainTab.SETTINGS -> settings ?: attachSettings().also { settings = it }
        }
        if (shown === screen) return
        shown?.let(viewController::hideScreen)
        viewController.showScreen(screen)
        shown = screen
    }

    /** 사이드바에 설정을 두는 화면이면 설정 RIB을 붙여 사이드바에 넘긴다. 설정 탭은 이 RIB 화면을 다시 쓴다 */
    fun showSidebarSettingsIfNeeded() {
        if (!viewController.usesSidebarSettings || settings != null) return
        val screen = attachSettings().also { settings = it }
        viewController.showSidebarSettings(screen)
    }

    /** 세션이 알림 없이 볼트를 바꿨을 수 있을 때 볼트 목록이 붙어 있으면 세션에서 다시 읽게 한다 (복원·재연결·탭 전환) */
    fun refreshVaultList() {
        vaultList?.refreshFromSession()
    }

    private fun attachVaultList(): VaultListRouting {
        val child = vaultListBuilder.build(interactor)
        attachChild(child)
        return child
    }

    private fun attachGenerator(): ViewControllable {
        // 생성기 탭은 혼자 쓰므로 부모가 받을 결과가 없고 복사 버튼을 둔다
        val child = passwordGeneratorBuilder.build(listener = null, allowCopy = true)
        attachChild(child)
        return child.viewControllable
    }

    private fun attachSettings(): ViewControllable {
        val child = settingsBuilder.build(interactor)
        attachChild(child)
        return child.viewControllable
    }
}
