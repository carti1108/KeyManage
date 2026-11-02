package com.keymanage.back.domain.auth

import com.keymanage.back.domain.vault.VaultId

/** 세션 저장소 포트. 키는 토큰 원문이 아니라 [SessionTokens.hash] 값이다 */
interface SessionRepository {

    fun save(session: Session)

    /** @return 없거나 읽을 수 없는(형식 오류·예전 형식) 세션이면 null. 읽을 수 없는 세션은 지운다 */
    fun find(tokenHash: String): Session?

    /** 없는 세션을 지워도 실패하지 않는다 (로그아웃 멱등) */
    fun delete(tokenHash: String)

    /**
     * 이 계정의 세션 중 볼트 ID가 [except]가 아닌 것을 모두 지운다. 비밀번호 변경 뒤 다른 기기를 로그아웃시킬 때 쓴다 (SEC-004 규칙 7).
     * 확정 직후 새 비밀번호로 발급된 세션(새 볼트 ID)은 남긴다
     */
    fun deleteAllOf(email: Email, except: VaultId)

    /** 이 계정의 세션을 볼트 ID와 관계없이 모두 지운다. 계정 삭제 뒤에 쓴다 (SEC-005 규칙 4) */
    fun deleteAllOf(email: Email)
}
