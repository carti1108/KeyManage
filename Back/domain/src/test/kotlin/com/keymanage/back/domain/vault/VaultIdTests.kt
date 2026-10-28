package com.keymanage.back.domain.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VaultIdTests {

    @Test
    fun `UUID 형식이면 받아들인다`() {
        assertEquals("550e8400-e29b-41d4-a716-446655440000", VaultId.of("550e8400-e29b-41d4-a716-446655440000").value)
    }

    @Test
    fun `대문자 UUID는 소문자로 맞춰 같은 볼트로 본다`() {
        assertEquals(
            VaultId.of("550e8400-e29b-41d4-a716-446655440000"),
            VaultId.of("550E8400-E29B-41D4-A716-446655440000"),
        )
    }

    @Test
    fun `경로 조작 문자열은 거절한다`() {
        assertFailsWith<InvalidVaultIdException> { VaultId.of("../../etc/passwd") }
    }

    @Test
    fun `빈 값은 거절한다`() {
        assertFailsWith<InvalidVaultIdException> { VaultId.of("") }
    }

    @Test
    fun `자릿수가 맞지 않는 UUID는 거절한다`() {
        // java.util.UUID.fromString은 이런 값도 받아들이므로 형식을 직접 검사해야 한다
        assertFailsWith<InvalidVaultIdException> { VaultId.of("1-1-1-1-1") }
    }

    @Test
    fun `UUID 뒤에 다른 문자가 붙으면 거절한다`() {
        assertFailsWith<InvalidVaultIdException> { VaultId.of("550e8400-e29b-41d4-a716-446655440000.json") }
    }
}
