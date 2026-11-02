package com.keymanage.back.infrastructure

import com.keymanage.back.domain.auth.Account
import com.keymanage.back.domain.auth.AuthenticateUseCase
import com.keymanage.back.domain.auth.DeleteAccountUseCase
import com.keymanage.back.domain.auth.Email
import com.keymanage.back.domain.auth.FakePasswordHasher
import com.keymanage.back.domain.auth.LoginAttemptLimiter
import com.keymanage.back.domain.auth.MutableClock
import com.keymanage.back.domain.auth.RegisterUseCase
import com.keymanage.back.domain.auth.SessionIssuer
import com.keymanage.back.domain.auth.UnauthorizedException
import com.keymanage.back.domain.auth.authHashOf
import com.keymanage.back.domain.auth.kdfOf
import com.keymanage.back.domain.vault.CleanUpOrphanVaultsUseCase
import com.keymanage.back.domain.vault.EncryptedVault
import com.keymanage.back.domain.vault.Revision
import com.keymanage.back.domain.vault.SaveVaultUseCase
import com.keymanage.back.domain.vault.VaultConflictException
import com.keymanage.back.domain.vault.VaultId
import com.keymanage.back.domain.vault.VaultRepository
import com.keymanage.back.infrastructure.auth.FileSystemAccountRepository
import com.keymanage.back.infrastructure.auth.FileSystemSessionRepository
import com.keymanage.back.infrastructure.vault.FileSystemVaultRepository
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 실제 파일 저장소에서 계정 삭제가 계정·볼트·세션 파일을 지우고, 남은 볼트는 다음 시작 때 정리되는지 본다 (SEC-005, ADR-0024) */
class AccountDeletionOnFilesTests {

    @TempDir
    lateinit var root: Path

    private val email = Email.of("user@example.com")
    private val clock = MutableClock()
    private val hasher = FakePasswordHasher()

    private lateinit var accounts: FileSystemAccountRepository
    private lateinit var vaults: FileSystemVaultRepository
    private lateinit var sessions: FileSystemSessionRepository
    private lateinit var issuer: SessionIssuer
    private lateinit var account: Account
    private lateinit var token: String

    @BeforeTest
    fun setUp() {
        accounts = FileSystemAccountRepository(root.resolve("accounts"))
        vaults = FileSystemVaultRepository(root.resolve("vaults"))
        sessions = FileSystemSessionRepository(root.resolve("sessions"))
        issuer = SessionIssuer(sessions, clock)
        token = RegisterUseCase(accounts, hasher, issuer, registrationEnabled = true).execute(email, authHashOf(1), kdfOf(2)).token
        account = accounts.find(email)!!
        SaveVaultUseCase(vaults).execute(account.vaultId, Revision.NONE, EncryptedVault("v1"))
    }

    private fun delete(vaultRepository: VaultRepository = vaults) =
        DeleteAccountUseCase(accounts, vaultRepository, sessions, hasher, LoginAttemptLimiter(clock)).execute(email, authHashOf(1))

    private fun filesIn(dir: String) = root.resolve(dir).listDirectoryEntries().size

    @Test
    fun `계정·볼트·세션 파일을 모두 지우고 토큰은 거절된다`() {
        delete()

        assertEquals(0, filesIn("accounts"))
        assertEquals(0, filesIn("vaults"))
        assertEquals(0, filesIn("sessions"))
        assertFailsWith<UnauthorizedException> { AuthenticateUseCase(sessions, accounts, clock).execute(token) }
    }

    @Test
    fun `삭제 뒤 늦은 저장은 지운 볼트를 되살리지 않는다`() {
        delete()

        assertFailsWith<VaultConflictException> { SaveVaultUseCase(vaults).execute(account.vaultId, Revision(1), EncryptedVault("late")) }
        assertNull(vaults.find(account.vaultId))
        assertEquals(0, filesIn("vaults"))
    }

    @Test
    fun `볼트 파일을 지우지 못해도 계정은 지워지고 다음 시작 때 남은 볼트를 정리한다`() {
        RegisterUseCase(accounts, hasher, issuer, registrationEnabled = true).execute(Email.of("other@example.com"), authHashOf(5), kdfOf(6))
        val failing = object : VaultRepository by vaults {
            override fun delete(id: VaultId) = throw IOException("볼트 삭제 실패")
        }

        delete(failing)

        assertNull(accounts.find(email))
        assertEquals(1, filesIn("vaults"))
        val restarted = FileSystemVaultRepository(root.resolve("vaults"))
        assertEquals(1, CleanUpOrphanVaultsUseCase(FileSystemAccountRepository(root.resolve("accounts")), restarted).execute())
        assertEquals(0, filesIn("vaults"))
    }
}
