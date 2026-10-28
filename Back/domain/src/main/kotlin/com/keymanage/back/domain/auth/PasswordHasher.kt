package com.keymanage.back.domain.auth

/** 인증 해시를 서버 저장용으로 다시 해시하는 포트. 구현은 Argon2id다 (SEC-002) */
interface PasswordHasher {

    /** 매번 새 salt로 해시해 알고리즘·파라미터·salt가 담긴 문자열로 돌려준다 */
    fun hash(authHash: AuthHash): String

    fun verify(authHash: AuthHash, encoded: String): Boolean
}
