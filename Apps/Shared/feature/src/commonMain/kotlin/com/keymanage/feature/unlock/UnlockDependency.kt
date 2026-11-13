package com.keymanage.feature.unlock

import com.carti.ribs.Dependency
import com.keymanage.feature.SessionPort

interface UnlockDependency : Dependency {
    val unlockViewFactory: UnlockViewFactory
    val session: SessionPort
}

/** 자식 → 부모 */
interface UnlockListener {
    /** 볼트를 열었다. [offline]이 true면 서버에 닿지 못해 기기에 저장한 볼트로 열었다 (VLT-002) */
    fun didUnlock(offline: Boolean)

    /**
     * 로그아웃했다. 이 기기의 볼트 기록을 지우지 못했으면 로그인 화면에 보일 [notice]를 담는다 (ADR-0019).
     * 서버 토큰 폐기만 실패한 경우는 이 기기가 이미 로그아웃됐으므로 알리지 않는다 (SEC-002)
     */
    fun didLogout(notice: String?)

    /** 세션이 만료됐거나 다른 기기에서 마스터 비밀번호를 바꿨다. 로그인 화면에 [notice]를 보인다 (SEC-002, SEC-003) */
    fun didRequireSignIn(notice: String)
}
