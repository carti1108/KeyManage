package com.keymanage.back.domain.auth

/** 계정 저장소 포트. 구현체는 infrastructure 모듈에 둔다 */
interface AccountRepository {

    fun find(email: Email): Account?

    /**
     * 같은 이메일의 계정이 없을 때만 만든다. 확인과 생성은 원자적이어야 한다: 동시 가입 중 하나만 성공한다 (SEC-002)
     *
     * @return 만들었으면 true, 이미 있었으면 false
     */
    fun createIfAbsent(account: Account): Boolean
}
