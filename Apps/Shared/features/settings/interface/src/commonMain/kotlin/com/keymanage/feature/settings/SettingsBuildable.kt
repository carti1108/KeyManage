@file:OptIn(ExperimentalObjCName::class)

package com.keymanage.feature.settings

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.carti.ribs.Buildable
import com.carti.ribs.ViewableRouting

/** 부모 RIB이 Settings를 만들 때 쓰는 계약. 구현(SettingsBuilder)은 조립하는 곳이 넣는다 */
interface SettingsBuildable : Buildable {
    fun build(@ObjCName("settingsListener") listener: SettingsListener): SettingsRouting
}

/** 부모 RIB이 붙이고 떼는 Settings의 Router */
interface SettingsRouting : ViewableRouting
