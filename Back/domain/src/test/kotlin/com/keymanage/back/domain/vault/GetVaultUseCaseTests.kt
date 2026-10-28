package com.keymanage.back.domain.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetVaultUseCaseTests {

    private val id = VaultId.of("550e8400-e29b-41d4-a716-446655440000")
    private val repository = InMemoryVaultRepository()
    private val useCase = GetVaultUseCase(repository)

    @Test
    fun `저장된 볼트는 페이로드와 revision을 그대로 돌려준다`() {
        val stored = StoredVault(id, Revision(4), EncryptedVault("""{"ciphertext":"k7Z9X"}"""))
        repository.replaceIfRevisionMatches(Revision.NONE, stored)

        assertEquals(stored, useCase.execute(id))
    }

    @Test
    fun `볼트가 없으면 null을 돌려준다`() {
        assertNull(useCase.execute(id))
    }
}
