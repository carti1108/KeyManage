package com.keymanage.back.domain.auth

import com.keymanage.back.domain.vault.VaultId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PreloginUseCaseTests {

    private val accounts = InMemoryAccountRepository()
    private val secret = ServerSecret(ByteArray(32) { 7 })
    private val useCase = PreloginUseCase(accounts, secret)

    @Test
    fun `가입된 이메일은 등록된 KDF 값을 돌려준다`() {
        val email = Email.of("user@example.com")
        accounts.createIfAbsent(Account(email, kdfOf(3), "hash", VaultId.of("550e8400-e29b-41d4-a716-446655440000")))

        assertEquals(kdfOf(3), useCase.execute(email))
    }

    @Test
    fun `없는 이메일도 같은 이메일에는 항상 같은 가짜 값을 돌려준다`() {
        val email = Email.of("ghost@example.com")

        val first = useCase.execute(email)

        assertEquals(first, useCase.execute(email))
        assertEquals(KdfParams.MIN_ITERATIONS, first.iterations)
        assertEquals(KdfParams.SALT_BYTES, first.salt.size)
    }

    @Test
    fun `없는 이메일마다 가짜 salt가 다르다`() {
        assertNotEquals(useCase.execute(Email.of("a@example.com")), useCase.execute(Email.of("b@example.com")))
    }

    @Test
    fun `서버 비밀키가 다르면 같은 이메일의 가짜 salt도 다르다`() {
        val other = PreloginUseCase(accounts, ServerSecret(ByteArray(32) { 8 }))
        val email = Email.of("ghost@example.com")

        assertNotEquals(useCase.execute(email), other.execute(email))
    }
}
