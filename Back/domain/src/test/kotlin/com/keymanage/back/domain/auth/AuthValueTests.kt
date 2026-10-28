package com.keymanage.back.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class AuthValueTests {

    @Test
    fun `이메일은 앞뒤 공백을 지우고 소문자로 맞춘다`() {
        assertEquals("user@example.com", Email.of("  User@Example.COM ").value)
    }

    @Test
    fun `이메일 형식이 아니면 거절한다`() {
        listOf("", "user", "user@", "@example.com", "user@example", "a b@example.com").forEach { raw ->
            assertFailsWith<InvalidAuthInputException>(raw) { Email.of(raw) }
        }
    }

    @Test
    fun `이메일은 254자까지 허용한다`() {
        val local = "a".repeat(254 - "@example.com".length)
        assertEquals(254, Email.of("$local@example.com").value.length)
        assertFailsWith<InvalidAuthInputException> { Email.of("a$local@example.com") }
    }

    @Test
    fun `인증 해시는 32바이트여야 한다`() {
        AuthHash.of(ByteArray(32))
        assertFailsWith<InvalidAuthInputException> { AuthHash.of(ByteArray(31)) }
    }

    @Test
    fun `인증 해시는 넘겨받은 배열을 바꿔도 영향받지 않는다`() {
        val raw = ByteArray(32) { 1 }
        val hash = AuthHash.of(raw)
        raw[0] = 9

        assertEquals(1, hash.bytes()[0])
    }

    @Test
    fun `KDF salt는 16바이트, 반복 횟수는 600000 이상이어야 한다`() {
        KdfParams(ByteArray(16), 600_000)
        KdfParams(ByteArray(16), 1_000_000)
        assertFailsWith<InvalidAuthInputException> { KdfParams(ByteArray(15), 600_000) }
        assertFailsWith<InvalidAuthInputException> { KdfParams(ByteArray(16), 599_999) }
    }

    @Test
    fun `KDF 값은 내용으로 비교한다`() {
        assertEquals(kdfOf(1), kdfOf(1))
        assertEquals(kdfOf(1).hashCode(), kdfOf(1).hashCode())
        assertNotEquals(kdfOf(1), kdfOf(2))
    }

    @Test
    fun `비밀 값은 문자열로 바꿔도 내용이 드러나지 않는다`() {
        // 로그나 예외 메시지에 실수로 찍혀도 원문이 남지 않아야 한다 (SEC-002)
        assertEquals("AuthHash(****)", authHashOf(7).toString())
        assertEquals("ServerSecret(****)", ServerSecret(ByteArray(32) { 7 }).toString())
        assertEquals("KdfParams(iterations=600000)", kdfOf(7).toString())
    }

    @Test
    fun `서버 비밀키는 32바이트여야 한다`() {
        ServerSecret(ByteArray(32))
        assertFailsWith<IllegalArgumentException> { ServerSecret(ByteArray(16)) }
    }

    @Test
    fun `토큰 해시는 같은 토큰에 같고 원문을 담지 않는다`() {
        val hash = SessionTokens.hash("token-a")

        assertEquals(hash, SessionTokens.hash("token-a"))
        assertNotEquals(hash, SessionTokens.hash("token-b"))
        assertEquals(64, hash.length)
    }
}
