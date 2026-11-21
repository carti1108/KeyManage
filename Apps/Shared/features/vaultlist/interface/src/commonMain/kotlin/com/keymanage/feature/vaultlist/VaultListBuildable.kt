@file:OptIn(ExperimentalObjCName::class)

package com.keymanage.feature.vaultlist

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.carti.ribs.Buildable
import com.carti.ribs.ViewableRouting

/** 부모 RIB이 VaultList를 만들 때 쓰는 계약. 구현(VaultListBuilder)은 조립하는 곳이 넣는다 */
interface VaultListBuildable : Buildable {
    fun build(@ObjCName("vaultListListener") listener: VaultListListener): VaultListRouting
}

/** 부모 RIB이 붙이고 떼는 VaultList의 Router */
interface VaultListRouting : ViewableRouting {
    /** 세션이 알림 없이 볼트를 바꿨을 수 있을 때(복원·재연결·탭 전환) 부모가 부른다 */
    fun refreshFromSession()
}
