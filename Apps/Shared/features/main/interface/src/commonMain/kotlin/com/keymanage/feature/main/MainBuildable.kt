@file:OptIn(ExperimentalObjCName::class)

package com.keymanage.feature.main

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.carti.ribs.Buildable
import com.carti.ribs.ViewableRouting

/** 부모 RIB이 Main을 만들 때 쓰는 계약. 구현(MainBuilder)은 조립하는 곳이 넣는다 */
interface MainBuildable : Buildable {
    /**
     * @param offline 잠금 해제를 서버에 닿지 못해 기기에 저장한 볼트로 열었다. 동기화에 성공하면 이 RIB이 걷는다 (VLT-002)
     * @param offerBiometricUnlock 가입 직후라 생체 해제를 켤지 묻는다. 켤 수 없는 기기거나 이미 켰으면 묻지 않는다 (SEC-003 값 표)
     */
    fun build(@ObjCName("mainListener") listener: MainListener, offline: Boolean = false, offerBiometricUnlock: Boolean = false): MainRouting
}

/** 부모 RIB이 붙이고 떼는 Main의 Router */
interface MainRouting : ViewableRouting {
    /** 로그아웃·계정 삭제처럼 세션을 끝내는 작업이 진행 중이다. 이 동안 부모는 이 화면을 내리지 않는다 */
    val isFinishingSession: Boolean
}
