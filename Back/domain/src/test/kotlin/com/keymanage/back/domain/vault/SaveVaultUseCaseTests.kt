package com.keymanage.back.domain.vault

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SaveVaultUseCaseTests {

    private val id = VaultId.of("550e8400-e29b-41d4-a716-446655440000")
    private val repository = InMemoryVaultRepository()
    private val useCase = SaveVaultUseCase(repository)

    @Test
    fun `볼트가 없을 때 기대 revision 0으로 저장하면 revision 1이 된다`() {
        val revision = useCase.execute(id, Revision.NONE, EncryptedVault("v1"))

        assertEquals(Revision(1), revision)
        assertEquals(StoredVault(id, Revision(1), EncryptedVault("v1")), repository.find(id))
    }

    @Test
    fun `볼트가 없을 때 기대 revision이 0이 아니면 충돌하고 현재 revision 0을 알려준다`() {
        val error = assertFailsWith<VaultConflictException> {
            useCase.execute(id, Revision(3), EncryptedVault("v1"))
        }

        assertEquals(Revision.NONE, error.currentRevision)
        assertNull(repository.find(id))
    }

    @Test
    fun `기대 revision이 현재와 같으면 저장하고 1 증가시킨다`() {
        useCase.execute(id, Revision.NONE, EncryptedVault("v1"))

        val revision = useCase.execute(id, Revision(1), EncryptedVault("v2"))

        assertEquals(Revision(2), revision)
        assertEquals(EncryptedVault("v2"), repository.find(id)?.vault)
    }

    @Test
    fun `기대 revision이 현재와 다르면 충돌하고 저장된 볼트는 그대로다`() {
        useCase.execute(id, Revision.NONE, EncryptedVault("v1"))
        useCase.execute(id, Revision(1), EncryptedVault("v2"))

        val error = assertFailsWith<VaultConflictException> {
            useCase.execute(id, Revision(1), EncryptedVault("stale"))
        }

        assertEquals(Revision(2), error.currentRevision)
        assertEquals(StoredVault(id, Revision(2), EncryptedVault("v2")), repository.find(id))
    }

    @Test
    fun `같은 revision으로 동시에 두 기기가 저장하면 한 건만 성공한다`() {
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val results = listOf("phone", "laptop").map { device ->
            executor.submit<Boolean> {
                start.await()
                runCatching { useCase.execute(id, Revision.NONE, EncryptedVault(device)) }.isSuccess
            }
        }

        start.countDown()
        val successes = results.count { it.get(5, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(1, successes)
        assertEquals(Revision(1), repository.find(id)?.revision)
    }

    @Test
    fun `페이로드가 정확히 5MB이면 저장한다`() {
        val revision = useCase.execute(id, Revision.NONE, payloadOf(SaveVaultUseCase.MAX_PAYLOAD_BYTES))

        assertEquals(Revision(1), revision)
    }

    @Test
    fun `페이로드가 5MB를 1바이트라도 넘으면 저장하지 않는다`() {
        val error = assertFailsWith<VaultTooLargeException> {
            useCase.execute(id, Revision.NONE, payloadOf(SaveVaultUseCase.MAX_PAYLOAD_BYTES + 1))
        }

        assertEquals(5_242_881, error.sizeInBytes)
        assertNull(repository.find(id))
    }

    private fun payloadOf(bytes: Int) = EncryptedVault("a".repeat(bytes))
}
