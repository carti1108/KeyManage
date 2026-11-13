package com.keymanage.feature.settings

import com.carti.ribs.Presentable
import com.carti.ribs.ViewControllable
import kotlin.native.ObjCName

interface SettingsPresentableListener {
    @ObjCName("handleSettings")
    fun handle(action: SettingsViewAction)
}

/** Interactor가 쓰는 화면 계약 (RIBs-iOS Presentable, carti ADR-0010) */
interface SettingsPresentable : Presentable {
    /** 활성 구간에만 설정된다. 비활성이면 null이라 입력이 무시된다 */
    var listener: SettingsPresentableListener?
    @ObjCName("renderSettings")
    fun render(state: SettingsViewState)
}

/** Router가 쓰는 화면 계약. 자식 RIB이 없어 붙일 메서드는 없다 */
interface SettingsViewControllable : ViewControllable

/** 플랫폼 뷰가 구현하는 둘의 합 */
interface SettingsViewContract : SettingsPresentable, SettingsViewControllable

fun interface SettingsViewFactory {
    fun makeSettingsView(): SettingsViewContract
}
