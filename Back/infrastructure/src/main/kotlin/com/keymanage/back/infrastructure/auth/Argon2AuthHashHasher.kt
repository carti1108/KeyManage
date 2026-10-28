package com.keymanage.back.infrastructure.auth

import com.keymanage.back.domain.auth.AuthHash
import com.keymanage.back.domain.auth.PasswordHasher
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import java.util.Base64

/**
 * 인증 해시를 Argon2id로 다시 해시한다 (SEC-002: 메모리 19MiB, 반복 2, 병렬도 1).
 * salt 생성, 해시 문자열 형식, 상수 시간 비교는 Spring Security 구현을 그대로 쓴다.
 */
class Argon2AuthHashHasher : PasswordHasher {

    private val encoder = Argon2PasswordEncoder(SALT_BYTES, HASH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS)

    override fun hash(authHash: AuthHash): String = encoder.encode(encode(authHash))

    // 저장된 값이 깨져 있으면 Spring이 경고만 남기고 false를 돌려준다
    override fun verify(authHash: AuthHash, encoded: String): Boolean = encoder.matches(encode(authHash), encoded)

    private fun encode(authHash: AuthHash): String = Base64.getEncoder().encodeToString(authHash.bytes())

    private companion object {
        const val SALT_BYTES = 16
        const val HASH_BYTES = 32
        const val PARALLELISM = 1
        const val MEMORY_KIB = 19 * 1024
        const val ITERATIONS = 2
    }
}
