package com.keymanage.back.domain.auth

/** 세션 저장소 포트. 키는 토큰 원문이 아니라 [SessionTokens.hash] 값이다 */
interface SessionRepository {

    fun save(session: Session)

    fun find(tokenHash: String): Session?

    /** 없는 세션을 지워도 실패하지 않는다 (로그아웃 멱등) */
    fun delete(tokenHash: String)

    /** 이 계정의 세션을 모두 지운다. 비밀번호 변경 뒤 다른 기기를 로그아웃시킬 때 쓴다 (SEC-004 규칙 7) */
    fun deleteAllOf(email: Email)
}
