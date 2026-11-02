package com.keymanage.back.infrastructure

import com.keymanage.back.domain.auth.Account
import com.keymanage.back.domain.auth.AccountRepository
import com.keymanage.back.domain.auth.AuthFailedException
import com.keymanage.back.domain.auth.ChangePasswordUseCase
import com.keymanage.back.domain.auth.Email
import com.keymanage.back.domain.auth.FakePasswordHasher
import com.keymanage.back.domain.auth.LoginAttemptLimiter
import com.keymanage.back.domain.auth.LoginUseCase
import com.keymanage.back.domain.auth.MutableClock
import com.keymanage.back.domain.auth.PasswordChange
import com.keymanage.back.domain.auth.RegisterUseCase
import com.keymanage.back.domain.auth.SessionIssuer
import com.keymanage.back.domain.auth.authHashOf
import com.keymanage.back.domain.auth.kdfOf
import com.keymanage.back.domain.vault.CleanUpOrphanVaultsUseCase
import com.keymanage.back.domain.vault.EncryptedVault
import com.keymanage.back.domain.vault.Revision
import com.keymanage.back.domain.vault.SaveVaultUseCase
import com.keymanage.back.domain.vault.StoredVault
import com.keymanage.back.domain.vault.VaultConflictException
import com.keymanage.back.domain.vault.VaultRepository
import com.keymanage.back.infrastructure.auth.FileSystemAccountRepository
import com.keymanage.back.infrastructure.auth.FileSystemSessionRepository
import com.keymanage.back.infrastructure.vault.FileSystemVaultRepository
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.listDirectoryEntries
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 실제 파일 저장소에서 비밀번호 변경이 전부 되거나 전부 안 되는지, 동시 저장과 겹쳐도 어긋나지 않는지 본다 (SEC-004, ADR-BE-001) */
class PasswordChangeOnFilesTests {

    @TempDir
    lateinit var root: Path

    private val email = Email.of("user@example.com")
    private val clock = MutableClock()
    private val hasher = FakePasswordHasher()

    private lateinit var accounts: FileSystemAccountRepository
    private lateinit var vaults: FileSystemVaultRepository
    private lateinit var sessions: FileSystemSessionRepository
    private lateinit var issuer: SessionIssuer
    private lateinit var limiter: LoginAttemptLimiter
    private lateinit var oldAccount: Account

    @BeforeTest
    fun setUp() {
        accounts = FileSystemAccountRepository(root.resolve("accounts"))
        vaults = FileSystemVaultRepository(root.resolve("vaults"))
        sessions = FileSystemSessionRepository(root.resolve("sessions"))
        issuer = SessionIssuer(sessions, clock)
        limiter = LoginAttemptLimiter(clock)
        RegisterUseCase(accounts, hasher, issuer, registrationEnabled = true).execute(email, authHashOf(1), kdfOf(2))
        oldAccount = accounts.find(email)!!
        SaveVaultUseCase(vaults).execute(oldAccount.vaultId, Revision.NONE, EncryptedVault("v1"))
    }

    private fun useCase(accountRepository: AccountRepository = accounts, vaultRepository: VaultRepository = vaults) =
        ChangePasswordUseCase(accountRepository, vaultRepository, sessions, hasher, issuer, limiter)

    private fun command(expected: Revision = Revision(1)) =
        PasswordChange(authHashOf(1), authHashOf(3), kdfOf(4), expected, EncryptedVault("re-encrypted"))

    private fun loginWith(fill: Int) = LoginUseCase(accounts, hasher, issuer, limiter).execute(email, authHashOf(fill))

    private fun vaultFiles() = root.resolve("vaults").listDirectoryEntries().size

    @Test
    fun `새 볼트 쓰기에 실패하면 파일은 옛 계정과 볼트 그대로이고 옛 비밀번호로 로그인해 옛 볼트를 받는다`() {
        val failing = object : VaultRepository by vaults {
            override fun create(vault: StoredVault) = throw IOException("디스크 가득 참")
        }

        assertFailsWith<IOException> { useCase(vaultRepository = failing).execute(email, command()) }

        loginWith(1)
        val reloaded = FileSystemAccountRepository(root.resolve("accounts")).find(email)!!
        assertEquals(oldAccount, reloaded)
        assertEquals(EncryptedVault("v1"), FileSystemVaultRepository(root.resolve("vaults")).find(reloaded.vaultId)?.vault)
        assertEquals(1, vaultFiles())
    }

    @Test
    fun `계정 교체에 실패하면 파일은 옛 계정과 볼트 그대로이고 새로 쓴 볼트 파일은 남지 않는다`() {
        val failing = object : AccountRepository by accounts {
            override fun replaceIfMatches(expected: Account, replacement: Account): Boolean = throw IOException("디스크 가득 참")
        }

        assertFailsWith<IOException> { useCase(accountRepository = failing).execute(email, command()) }

        loginWith(1)
        assertEquals(oldAccount, FileSystemAccountRepository(root.resolve("accounts")).find(email))
        assertEquals(EncryptedVault("v1"), FileSystemVaultRepository(root.resolve("vaults")).find(oldAccount.vaultId)?.vault)
        assertEquals(1, vaultFiles())
    }

    @Test
    fun `옛 볼트를 지우지 못해 남은 파일은 다음 시작 때 정리된다`() {
        val failing = object : VaultRepository by vaults {
            override fun delete(id: com.keymanage.back.domain.vault.VaultId) = throw IOException("파일 잠김")
        }
        useCase(vaultRepository = failing).execute(email, command())
        assertEquals(2, vaultFiles())

        val restarted = FileSystemVaultRepository(root.resolve("vaults"))
        CleanUpOrphanVaultsUseCase(FileSystemAccountRepository(root.resolve("accounts")), restarted).execute()

        assertEquals(setOf(accounts.find(email)!!.vaultId), restarted.allIds())
        assertEquals(EncryptedVault("re-encrypted"), restarted.find(accounts.find(email)!!.vaultId)?.vault)
    }

    @Test
    fun `성공하면 파일에 새 KDF 값과 볼트가 남고 새 비밀번호로만 로그인한다`() {
        val result = useCase().execute(email, command())

        val reloaded = FileSystemAccountRepository(root.resolve("accounts")).find(email)!!
        assertEquals(kdfOf(4), reloaded.kdf)
        assertEquals(StoredVault(reloaded.vaultId, Revision(2), EncryptedVault("re-encrypted")), FileSystemVaultRepository(root.resolve("vaults")).find(reloaded.vaultId))
        assertEquals(Revision(2), result.revision)
        assertEquals(1, vaultFiles())
        assertFailsWith<AuthFailedException> { loginWith(1) }
        loginWith(3)
    }

    @RepeatedTest(20)
    fun `동시에 들어온 볼트 저장과 비밀번호 변경은 하나만 성공하고 계정은 자기 볼트를 가리킨다`() {
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val change = executor.submit(Callable {
            start.await()
            runCatching { useCase().execute(email, command()) }
        })
        val save = executor.submit(Callable {
            start.await()
            runCatching { SaveVaultUseCase(vaults).execute(oldAccount.vaultId, Revision(1), EncryptedVault("from-other-device")) }
        })

        start.countDown()
        val changed = change.get(10, TimeUnit.SECONDS)
        val saved = save.get(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertEquals(1, listOf(changed, saved).count { it.isSuccess })
        listOf(changed, saved).filter { it.isFailure }.forEach { assertEquals(VaultConflictException::class, it.exceptionOrNull()!!::class) }
        val account = accounts.find(email)!!
        val expected = if (changed.isSuccess) "re-encrypted" else "from-other-device"
        assertEquals(EncryptedVault(expected), vaults.find(account.vaultId)?.vault)
        assertEquals(Revision(2), vaults.find(account.vaultId)?.revision)
    }

    @RepeatedTest(10)
    fun `같은 계정의 비밀번호 변경이 동시에 오면 하나만 성공한다`() {
        val executor = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        val results = (1..4).map {
            executor.submit(Callable {
                start.await()
                runCatching { useCase().execute(email, command()) }
            })
        }

        start.countDown()
        val outcomes = results.map { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(1, vaultFiles())
        loginWith(3)
    }
}
