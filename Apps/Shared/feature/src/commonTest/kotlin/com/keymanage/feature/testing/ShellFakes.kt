package com.keymanage.feature.testing

import com.carti.ribs.ViewControllable
import com.carti.ribs.ViewHost
import com.keymanage.feature.KeyManageViewFactory
import com.keymanage.feature.root.RootViewControllable

// Root·KeyManageApp 테스트가 쓰는 가짜 뷰. 자식 RIB은 진짜를 쓰고 바깥만 가짜로 둔다.
// 세션·환경 같은 공용 가짜는 core/testing에, 피처의 가짜 뷰는 그 피처의 testing 모듈에 있다

typealias FakeAuthView = com.keymanage.feature.auth.testing.FakeAuthView
typealias FakeUnlockView = com.keymanage.feature.unlock.testing.FakeUnlockView
typealias FakeMainView = com.keymanage.feature.main.testing.FakeMainView
typealias FakeVaultListView = com.keymanage.feature.vaultlist.testing.FakeVaultListView
typealias FakeGeneratorView = com.keymanage.feature.generator.testing.FakeGeneratorView
typealias FakeSettingsView = com.keymanage.feature.settings.testing.FakeSettingsView

class FakeRootView : RootViewControllable {
    val shown = mutableListOf<ViewControllable>()
    val hidden = mutableListOf<ViewControllable>()
    val visible: List<ViewControllable> get() = shown.filter { view -> shown.count { it === view } > hidden.count { it === view } }.distinct()

    override fun showScreen(view: ViewControllable) {
        shown += view
    }

    override fun hideScreen(view: ViewControllable) {
        hidden += view
    }
}

class FakeHost : ViewHost<RootViewControllable> {
    val shown = mutableListOf<RootViewControllable>()
    val hidden = mutableListOf<RootViewControllable>()

    override fun show(view: RootViewControllable) {
        shown += view
    }

    override fun hide(view: RootViewControllable) {
        hidden += view
    }
}

/** 만든 뷰를 모두 기록한다. 몇 번 만들었는지로 RIB이 다시 붙었는지 알 수 있다 */
class FakeViewFactory : KeyManageViewFactory {
    val rootViews = mutableListOf<FakeRootView>()
    val authViews = mutableListOf<FakeAuthView>()
    val unlockViews = mutableListOf<FakeUnlockView>()
    val mainViews = mutableListOf<FakeMainView>()
    val vaultListViews = mutableListOf<FakeVaultListView>()
    val generatorViews = mutableListOf<FakeGeneratorView>()
    val settingsViews = mutableListOf<FakeSettingsView>()

    override fun makeRootView() = FakeRootView().also { rootViews += it }
    override fun makeAuthView() = FakeAuthView().also { authViews += it }
    override fun makeUnlockView() = FakeUnlockView().also { unlockViews += it }
    override fun makeMainView() = FakeMainView().also { mainViews += it }
    override fun makeVaultListView() = FakeVaultListView().also { vaultListViews += it }
    override fun makePasswordGeneratorView() = FakeGeneratorView().also { generatorViews += it }
    override fun makeSettingsView() = FakeSettingsView().also { settingsViews += it }
}
