package com.keymanage.feature.auth

import com.carti.ribs.Builder

class AuthBuilder(dependency: AuthDependency) : Builder<AuthDependency>(dependency) {
    /**
     * @param notice 로그인 화면에 띄울 안내(세션 만료, 다른 기기 비밀번호 변경, 계정 삭제 완료 등).
     * 사용자가 다시 시도하거나 모드를 바꾸면 이 RIB이 화면에서 지운다. 부모는 넘긴 뒤 자기 값을 비운다
     */
    fun build(listener: AuthListener, notice: String? = null): AuthRouter {
        val view = dependency.authViewFactory.makeAuthView()
        val interactor = AuthInteractor(view, dependency.session, listener, notice)
        return AuthRouter(interactor, view)
    }
}
