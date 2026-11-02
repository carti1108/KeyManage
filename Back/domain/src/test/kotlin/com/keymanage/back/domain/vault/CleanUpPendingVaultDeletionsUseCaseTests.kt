package com.keymanage.back.domain.vault

import com.keymanage.back.domain.auth.Account
import com.keymanage.back.domain.auth.AccountRepository
import com.keymanage.back.domain.auth.Email
import com.keymanage.back.domain.auth.InMemoryAccountRepository
import com.keymanage.back.domain.auth.kdfOf
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 시작 때 삭제 예정 목록의 볼트를 계정 수와 관계없이 지운다 (ADR-BE-002 결정 1·2) */
class CleanUpPendingVaultDeletionsUseCaseTests {

    private val accounts = InMemoryAccountRepository()
    private val vaults = InMemoryVaultRepository()
    private val pending = InMemoryPendingVaultDeletions()
    private val listed = VaultId.of("550e8400-e29b-41d4-a716-446655440000")
    private val inUse = VaultId.of("650e8400-e29b-41d4-a716-446655440000")
    private val unlisted = VaultId.of("750e8400-e29b-41d4-a716-446655440000")

    private fun vault(id: VaultId) = StoredVault(id, Revision(1), EncryptedVault("{}"))

    private fun cleanUp(accountRepository: AccountRepository = accounts, vaultRepository: VaultRepository = vaults) =
        CleanUpPendingVaultDeletionsUseCase(accountRepository, vaultRepository, pending).execute()

    @Test
    fun `계정이 하나도 없어도 목록의 볼트를 지우고 목록에서 뺀다`() {
        vaults.create(vault(listed))
        pending.add(listed)

        assertEquals(1, cleanUp())

        assertNull(vaults.find(listed))
        assertEquals(emptySet(), pending.all())
    }

    @Test
    fun `목록에 없는 볼트는 계정이 없어도 지우지 않는다`() {
        vaults.create(vault(listed))
        vaults.create(vault(unlisted))
        pending.add(listed)

        cleanUp()

        assertNotNull(vaults.find(unlisted))
    }

    @Test
    fun `지금 계정이 가리키는 볼트 ID는 목록에 있어도 지우지 않는다`() {
        accounts.createIfAbsent(Account(Email.of("owner@example.com"), kdfOf(1), "hash", inUse))
        vaults.create(vault(inUse))
        pending.add(inUse)

        assertEquals(0, cleanUp())

        assertNotNull(vaults.find(inUse))
    }

    @Test
    fun `이미 없는 볼트는 목록에서만 뺀다`() {
        pending.add(listed)

        cleanUp()

        assertEquals(emptySet(), pending.all())
    }

    @Test
    fun `지우지 못한 볼트는 목록에 남기고 나머지는 지운다`() {
        val other = VaultId.of("850e8400-e29b-41d4-a716-446655440000")
        vaults.create(vault(listed))
        vaults.create(vault(other))
        pending.add(listed)
        pending.add(other)
        val flaky = object : VaultRepository by vaults {
            override fun delete(id: VaultId) {
                if (id == listed) throw IOException("삭제 실패")
                vaults.delete(id)
            }
        }

        assertEquals(1, cleanUp(vaultRepository = flaky))

        assertEquals(setOf(listed), pending.all())
        assertNull(vaults.find(other))
    }

    @Test
    fun `계정을 읽지 못하면 아무것도 지우지 않는다`() {
        vaults.create(vault(listed))
        pending.add(listed)
        val broken = object : AccountRepository by accounts {
            override fun allVaultIds(): Set<VaultId> = throw IOException("계정 읽기 실패")
        }

        assertEquals(0, cleanUp(accountRepository = broken))

        assertNotNull(vaults.find(listed))
        assertEquals(setOf(listed), pending.all())
    }

    @Test
    fun `목록을 읽지 못하면 아무것도 지우지 않고 실패하지 않는다`() {
        vaults.create(vault(listed))
        val brokenList = object : PendingVaultDeletions by pending {
            override fun all(): Set<VaultId> = throw IOException("목록 읽기 실패")
        }

        assertEquals(0, CleanUpPendingVaultDeletionsUseCase(accounts, vaults, brokenList).execute())

        assertNotNull(vaults.find(listed))
    }
}
