package com.keymanage.feature.settings.testing

import com.keymanage.feature.settings.SettingsPresentableListener
import com.keymanage.feature.settings.SettingsViewContract
import com.keymanage.feature.settings.SettingsViewState

/** 그린 상태를 모두 기록하는 가짜 뷰 */
class FakeSettingsView : SettingsViewContract {
    override var listener: SettingsPresentableListener? = null
    val states = mutableListOf<SettingsViewState>()
    val state: SettingsViewState get() = states.last()

    override fun render(state: SettingsViewState) {
        states += state
    }
}
