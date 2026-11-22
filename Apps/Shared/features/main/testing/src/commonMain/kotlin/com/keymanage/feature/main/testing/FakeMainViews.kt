package com.keymanage.feature.main.testing

import com.keymanage.feature.generator.testing.FakeGeneratorView
import com.keymanage.feature.settings.testing.FakeSettingsView
import com.keymanage.feature.vaultlist.testing.FakeVaultListView

/** Main과 그 자식(볼트 목록·생성기·설정)의 가짜 뷰를 만들고 모두 기록한다. 몇 번 만들었는지로 RIB이 다시 붙었는지 알 수 있다 */
class FakeMainViews {
    val mainViews = mutableListOf<FakeMainView>()
    val vaultListViews = mutableListOf<FakeVaultListView>()
    val generatorViews = mutableListOf<FakeGeneratorView>()
    val settingsViews = mutableListOf<FakeSettingsView>()

    /** 이후 만드는 Main 가짜 뷰가 사이드바에 설정을 두는지 */
    var usesSidebarSettings = false

    fun makeMainView() = FakeMainView(usesSidebarSettings).also { mainViews += it }
    fun makeVaultListView() = FakeVaultListView().also { vaultListViews += it }
    fun makePasswordGeneratorView() = FakeGeneratorView().also { generatorViews += it }
    fun makeSettingsView() = FakeSettingsView().also { settingsViews += it }
}
