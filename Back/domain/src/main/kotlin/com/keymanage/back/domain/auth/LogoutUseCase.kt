package com.keymanage.back.domain.auth

/** 이 토큰의 세션만 폐기한다. 이미 없는 토큰이어도 실패하지 않는다 (api-spec 2.5.4) */
class LogoutUseCase(private val sessions: SessionRepository) {

    fun execute(token: String) {
        sessions.delete(SessionTokens.hash(token))
    }
}
