package com.keymanage.back.domain.vault

import com.keymanage.back.domain.auth.Account
import com.keymanage.back.domain.auth.AccountRepository
import com.keymanage.back.domain.auth.Email
import com.keymanage.back.domain.auth.InMemoryAccountRepository
import com.keymanage.back.domain.auth.kdfOf
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

/** 서버 시작 때 어느 계정도 가리키지 않는 볼트를 지운다 (ADR-BE-001 결정 5) */
class CleanUpOrphanVaultsUseCaseTests {

    private val accounts = InMemoryAccountRepository()
    private val vaults = InMemoryVaultRepository()
    private val useCase = CleanUpOrphanVaultsUseCase(accounts, vaults)

    private fun vaultId(n: Int) = VaultId.of("00000000-0000-0000-0000-%012d".format(n))

    private fun account(email: String, vaultId: VaultId) {
        accounts.createIfAbsent(Account(Email.of(email), kdfOf(1), "hash", vaultId))
    }

    private fun vault(id: VaultId) {
        vaults.create(StoredVault(id, Revision(1), EncryptedVault("v")))
    }

    @Test
    fun `계정이 가리키지 않는 볼트만 지운다`() {
        account("a@example.com", vaultId(1))
        vault(vaultId(1))
        vault(vaultId(2))
        vault(vaultId(3))

        assertEquals(2, useCase.execute())

        assertEquals(setOf(vaultId(1)), vaults.allIds())
    }

    @Test
    fun `볼트를 아직 저장하지 않은 계정이 있어도 다른 계정의 볼트는 그대로다`() {
        account("fresh@example.com", vaultId(1))
        account("b@example.com", vaultId(2))
        vault(vaultId(2))

        assertEquals(0, useCase.execute())

        assertEquals(setOf(vaultId(2)), vaults.allIds())
    }

    @Test
    fun `계정이 하나도 없으면 저장 폴더 설정 오류일 수 있어 아무것도 지우지 않는다`() {
        vault(vaultId(1))

        assertEquals(0, useCase.execute())

        assertEquals(setOf(vaultId(1)), vaults.allIds())
    }

    @Test
    fun `계정 목록을 읽지 못하면 아무것도 지우지 않는다`() {
        account("a@example.com", vaultId(1))
        vault(vaultId(2))
        val broken = object : AccountRepository by accounts {
            override fun allVaultIds(): Set<VaultId> = throw IOException("계정 파일 손상")
        }

        assertEquals(0, CleanUpOrphanVaultsUseCase(broken, vaults).execute())

        assertEquals(setOf(vaultId(2)), vaults.allIds())
    }

    @Test
    fun `지우지 못한 볼트는 건너뛰고 나머지를 지운다`() {
        account("a@example.com", vaultId(1))
        vault(vaultId(2))
        vault(vaultId(3))
        val flaky = object : VaultRepository by vaults {
            override fun delete(target: VaultId) {
                if (target == vaultId(2)) throw IOException("삭제 실패")
                vaults.delete(target)
            }
        }

        assertEquals(1, CleanUpOrphanVaultsUseCase(accounts, flaky).execute())

        assertEquals(setOf(vaultId(2)), vaults.allIds())
    }
}
