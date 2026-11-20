package com.keymanage.feature.unlock

import com.carti.ribs.Buildable
import com.carti.ribs.ViewableRouting

/** 부모 RIB이 Unlock을 만들 때 쓰는 계약. 구현(UnlockBuilder)은 조립하는 곳이 넣는다 */
interface UnlockBuildable : Buildable {
    fun build(listener: UnlockListener): UnlockRouting
}

/** 부모 RIB이 붙이고 떼는 Unlock의 Router */
interface UnlockRouting : ViewableRouting {
    /**
     * 로그아웃이 진행 중이다. 세션이 먼저 로그인 전 상태로 알려도 부모는 이 작업이 끝나 결과를 알릴 때까지 이 화면을 내리지 않는다.
     * 내리면 기기 기록 정리가 취소된다 (ADR-0019)
     */
    val isFinishingSession: Boolean
}
