package com.keymanage.back.domain.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** 세션 토큰 생성과 저장용 해시 (SEC-002). 서버에는 토큰 원문이 아니라 SHA-256 해시만 둔다 */
object SessionTokens {

    private const val TOKEN_BYTES = 32

    /** 256비트 난수를 URL에 안전한 Base64로 만든다 */
    fun generate(random: SecureRandom): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** 저장·조회 키로 쓰는 SHA-256 16진수 문자열 */
    fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
