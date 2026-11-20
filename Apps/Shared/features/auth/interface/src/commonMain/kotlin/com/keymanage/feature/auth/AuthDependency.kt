package com.keymanage.feature.auth

import com.carti.ribs.Dependency
import com.keymanage.feature.SessionPort

interface AuthDependency : Dependency {
    val authViewFactory: AuthViewFactory
    val session: SessionPort
}

/** 자식 → 부모 */
interface AuthListener {
    /**
     * 로그인 또는 가입에 성공했다. 부모가 세션 상태를 다시 읽고 화면을 바꾼다
     * @param registered 가입으로 끝났다. 가입 직후에만 하는 안내(생체 해제 제안)에 쓴다 (SEC-003 값 표)
     */
    fun authDidComplete(registered: Boolean)
}
