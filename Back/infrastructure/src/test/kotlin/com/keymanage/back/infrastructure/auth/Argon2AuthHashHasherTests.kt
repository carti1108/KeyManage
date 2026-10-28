package com.keymanage.back.infrastructure.auth

import com.keymanage.back.domain.auth.AuthHash
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Argon2AuthHashHasherTests {

    private val hasher = Argon2AuthHashHasher()
    private val authHash = AuthHash.of(ByteArray(AuthHash.BYTES) { 1 })

    @Test
    fun `SEC-002 파라미터의 Argon2id로 해시한다`() {
        val encoded = hasher.hash(authHash)

        assertTrue(encoded.startsWith("\$argon2id\$v=19\$m=19456,t=2,p=1\$"), encoded)
    }

    @Test
    fun `같은 인증 해시는 검증에 성공하고 다른 해시는 실패한다`() {
        val encoded = hasher.hash(authHash)

        assertTrue(hasher.verify(authHash, encoded))
        assertFalse(hasher.verify(AuthHash.of(ByteArray(AuthHash.BYTES) { 2 }), encoded))
    }

    @Test
    fun `해시할 때마다 salt가 달라 결과가 다르다`() {
        assertNotEquals(hasher.hash(authHash), hasher.hash(authHash))
    }

    @Test
    fun `깨진 해시 문자열은 예외 없이 검증 실패로 본다`() {
        assertFalse(hasher.verify(authHash, "not-a-hash"))
    }
}
