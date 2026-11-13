package com.keymanage.feature.auth

import com.carti.ribs.Dependency
import com.keymanage.feature.SessionPort

interface AuthDependency : Dependency {
    val authViewFactory: AuthViewFactory
    val session: SessionPort
}

/** 자식 → 부모 */
interface AuthListener {
    /** 로그인 또는 가입에 성공했다. 부모가 세션 상태를 다시 읽고 화면을 바꾼다 */
    fun authDidComplete()
}
