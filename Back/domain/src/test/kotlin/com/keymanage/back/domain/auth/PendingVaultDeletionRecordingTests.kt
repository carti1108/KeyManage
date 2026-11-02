package com.keymanage.back.domain.auth

import com.keymanage.back.domain.vault.CleanUpPendingVaultDeletionsUseCase
import com.keymanage.back.domain.vault.EncryptedVault
import com.keymanage.back.domain.vault.InMemoryPendingVaultDeletions
import com.keymanage.back.domain.vault.InMemoryVaultRepository
import com.keymanage.back.domain.vault.Revision
import com.keymanage.back.domain.vault.SaveVaultUseCase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 확정 뒤 지우지 못한 볼트를 삭제 예정 목록에 남겨 시작 때 지운다 (ADR-BE-002 결정 1·2, SEC-005 규칙 4) */
class PendingVaultDeletionRecordingTests {

    private val accounts = InMemoryAccountRepository()
    private val sessions = InMemorySessionRepository()
    private val vaults = InMemoryVaultRepository()
    private val failingVaults = FailingVaultRepository(vaults)
    private val pending = InMemoryPendingVaultDeletions()
    private val hasher = FakePasswordHasher()
    private val clock = MutableClock()
    private val issuer = SessionIssuer(sessions, clock)
    private val limiter = LoginAttemptLimiter(clock)
    private val email = Email.of("user@example.com")

    private lateinit var account: Account

    @BeforeTest
    fun registerAndSaveVault() {
        RegisterUseCase(accounts, hasher, issuer, registrationEnabled = true).execute(email, authHashOf(1), kdfOf(2))
        account = accounts.find(email)!!
        SaveVaultUseCase(vaults).execute(account.vaultId, Revision.NONE, EncryptedVault("v1"))
    }

    private fun deleteAccount() =
        DeleteAccountUseCase(accounts, failingVaults, sessions, hasher, limiter, pendingDeletions = pending).execute(email, authHashOf(1))

    private fun changePassword() =
        ChangePasswordUseCase(accounts, failingVaults, sessions, hasher, issuer, limiter, pendingDeletions = pending)
            .execute(email, PasswordChange(authHashOf(1), authHashOf(3), kdfOf(4), Revision(1), EncryptedVault("re-encrypted")))

    private fun restartCleanUp() = CleanUpPendingVaultDeletionsUseCase(accounts, vaults, pending).execute()

    @Test
    fun `마지막 계정을 지우다 볼트 삭제가 실패하면 목록에 남기고 다음 시작 때 지운다`() {
        failingVaults.failDelete = true

        deleteAccount()

        assertEquals(setOf(account.vaultId), pending.all())
        assertNotNull(vaults.find(account.vaultId))
        assertEquals(emptySet(), accounts.allVaultIds())
        restartCleanUp()
        assertNull(vaults.find(account.vaultId))
        assertEquals(emptySet(), pending.all())
    }

    @Test
    fun `볼트를 지웠으면 목록에 남기지 않는다`() {
        deleteAccount()

        assertEquals(emptySet(), pending.all())
    }

    @Test
    fun `목록에도 쓰지 못해도 계정 삭제는 확정된다`() {
        failingVaults.failDelete = true
        pending.failAdd = true

        deleteAccount()

        assertNull(accounts.find(email))
        assertEquals(emptySet(), pending.all())
    }

    @Test
    fun `비밀번호 변경 뒤 옛 볼트를 지우지 못하면 목록에 남긴다`() {
        failingVaults.failDelete = true

        changePassword()

        assertEquals(setOf(account.vaultId), pending.all())
        restartCleanUp()
        assertNull(vaults.find(account.vaultId))
        assertNotNull(vaults.find(accounts.find(email)!!.vaultId))
    }

    @Test
    fun `비밀번호 변경 뒤 목록에도 쓰지 못해도 변경은 확정된다`() {
        failingVaults.failDelete = true
        pending.failAdd = true

        val changed = changePassword()

        assertEquals(Revision(2), changed.revision)
        assertEquals(emptySet(), pending.all())
    }
}
