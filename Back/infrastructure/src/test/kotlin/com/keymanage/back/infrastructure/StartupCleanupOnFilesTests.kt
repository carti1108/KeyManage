package com.keymanage.back.infrastructure

import com.keymanage.back.domain.auth.Account
import com.keymanage.back.domain.auth.CleanUpSessionsUseCase
import com.keymanage.back.domain.auth.DeleteAccountUseCase
import com.keymanage.back.domain.auth.Email
import com.keymanage.back.domain.auth.FakePasswordHasher
import com.keymanage.back.domain.auth.KdfParams
import com.keymanage.back.domain.auth.LoginAttemptLimiter
import com.keymanage.back.domain.auth.MutableClock
import com.keymanage.back.domain.auth.RegisterUseCase
import com.keymanage.back.domain.auth.Session
import com.keymanage.back.domain.auth.SessionIssuer
import com.keymanage.back.domain.auth.authHashOf
import com.keymanage.back.domain.auth.kdfOf
import com.keymanage.back.domain.vault.CleanUpPendingVaultDeletionsUseCase
import com.keymanage.back.domain.vault.EncryptedVault
import com.keymanage.back.domain.vault.Revision
import com.keymanage.back.domain.vault.SaveVaultUseCase
import com.keymanage.back.domain.vault.VaultId
import com.keymanage.back.domain.vault.VaultRepository
import com.keymanage.back.infrastructure.auth.FileSystemAccountRepository
import com.keymanage.back.infrastructure.auth.FileSystemPendingVaultDeletions
import com.keymanage.back.infrastructure.auth.FileSystemSessionRepository
import com.keymanage.back.infrastructure.vault.FileSystemVaultRepository
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 실제 파일 저장소에서 삭제 예정 목록과 시작 때 세션 정리를 확인한다 (ADR-BE-002) */
class StartupCleanupOnFilesTests {

    @TempDir
    lateinit var root: Path

    private val email = Email.of("user@example.com")
    private val clock = MutableClock()
    private val hasher = FakePasswordHasher()

    private fun accounts() = FileSystemAccountRepository(root.resolve("auth").resolve("accounts"))

    private fun sessions() = FileSystemSessionRepository(root.resolve("auth").resolve("sessions"))

    private fun vaults() = FileSystemVaultRepository(root.resolve("vaults"))

    private fun pending() = FileSystemPendingVaultDeletions(root.resolve("auth").resolve("pending-vault-deletions.json"))

    private fun vaultFiles() = if (Files.notExists(root.resolve("vaults"))) 0 else root.resolve("vaults").listDirectoryEntries().size

    @Test
    fun `마지막 계정을 지우다 볼트 삭제가 실패해도 재시작하면 볼트가 없다`() {
        val accounts = accounts()
        val vaults = vaults()
        val sessions = sessions()
        RegisterUseCase(accounts, hasher, SessionIssuer(sessions, clock), registrationEnabled = true).execute(email, authHashOf(1), kdfOf(2))
        SaveVaultUseCase(vaults).execute(accounts.find(email)!!.vaultId, Revision.NONE, EncryptedVault("v1"))
        val failing = object : VaultRepository by vaults {
            override fun delete(id: VaultId) = throw IOException("볼트 삭제 실패")
        }

        DeleteAccountUseCase(accounts, failing, sessions, hasher, LoginAttemptLimiter(clock), pendingDeletions = pending()).execute(email, authHashOf(1))
        assertEquals(1, vaultFiles())

        assertEquals(1, CleanUpPendingVaultDeletionsUseCase(accounts(), vaults(), pending()).execute())
        assertEquals(0, vaultFiles())
        assertEquals(emptySet(), pending().all())
    }

    @Test
    fun `삭제 예정 목록은 추가한 ID를 새 인스턴스로 읽어도 돌려주고 뺀 ID는 돌려주지 않는다`() {
        val a = VaultId.of("550e8400-e29b-41d4-a716-446655440000")
        val b = VaultId.of("650e8400-e29b-41d4-a716-446655440000")
        pending().add(a)
        pending().add(b)
        pending().add(a)

        assertEquals(setOf(a, b), pending().all())
        pending().removeAll(setOf(a))
        assertEquals(setOf(b), pending().all())
    }

    @Test
    fun `삭제 예정 목록 파일이 없으면 비어 있고 쓴 뒤 임시 파일이 남지 않는다`() {
        assertEquals(emptySet(), pending().all())

        pending().add(VaultId.of("550e8400-e29b-41d4-a716-446655440000"))

        assertEquals(listOf("pending-vault-deletions.json"), root.resolve("auth").listDirectoryEntries().map { it.fileName.toString() })
    }

    @Test
    fun `삭제 예정 목록 파일에는 볼트 ID만 적는다`() {
        pending().add(VaultId.of("550e8400-e29b-41d4-a716-446655440000"))

        val text = Files.readString(root.resolve("auth").resolve("pending-vault-deletions.json"))
        assertTrue(text.contains("550e8400-e29b-41d4-a716-446655440000"))
        assertFalse(text.contains("@"))
    }

    @Test
    fun `시작 때 만료·형식 오류·계정 없음·볼트 ID 불일치 세션 파일을 지우고 정상 세션은 남긴다`() {
        val vaultId = VaultId.of("550e8400-e29b-41d4-a716-446655440000")
        accounts().createIfAbsent(Account(email, KdfParams(ByteArray(KdfParams.SALT_BYTES) { 1 }, KdfParams.MIN_ITERATIONS), "hash", vaultId))
        val sessions = sessions()
        val future = clock.instant().plus(Duration.ofDays(1))
        val valid = Session("a".repeat(64), email, future, vaultId)
        val expired = Session("b".repeat(64), email, clock.instant().minusSeconds(1), vaultId)
        val ghost = Session("c".repeat(64), Email.of("ghost@example.com"), future, vaultId)
        val stale = Session("d".repeat(64), email, future, VaultId.of("650e8400-e29b-41d4-a716-446655440000"))
        listOf(valid, expired, ghost, stale).forEach(sessions::save)
        val malformed = root.resolve("auth").resolve("sessions").resolve("e".repeat(64) + ".json")
        malformed.writeText("{")

        CleanUpSessionsUseCase(sessions(), accounts(), clock).execute()

        assertNotNull(sessions().find(valid.tokenHash))
        listOf(expired, ghost, stale).forEach { assertNull(sessions().find(it.tokenHash)) }
        assertFalse(Files.exists(malformed))
        assertEquals(1, root.resolve("auth").resolve("sessions").listDirectoryEntries().size)
    }

    @Test
    fun `계정 파일을 하나라도 읽지 못하면 세션 파일을 지우지 않는다`() {
        accounts().createIfAbsent(Account(email, KdfParams(ByteArray(KdfParams.SALT_BYTES) { 1 }, KdfParams.MIN_ITERATIONS), "hash", VaultId.of("550e8400-e29b-41d4-a716-446655440000")))
        root.resolve("auth").resolve("accounts").resolve("broken.json").writeText("{")
        val ghost = Session("c".repeat(64), Email.of("ghost@example.com"), clock.instant().plus(Duration.ofDays(1)), VaultId.of("550e8400-e29b-41d4-a716-446655440000"))
        sessions().save(ghost)

        assertEquals(0, CleanUpSessionsUseCase(sessions(), accounts(), clock).execute())

        assertTrue(Files.exists(root.resolve("auth").resolve("sessions").resolve(ghost.tokenHash + ".json")))
    }

    @Test
    fun `세션 저장소는 세션 파일의 토큰 해시만 돌려준다`() {
        val sessions = sessions()
        val session = Session("a".repeat(64), email, clock.instant().plus(Duration.ofDays(1)), VaultId.of("550e8400-e29b-41d4-a716-446655440000"))
        sessions.save(session)
        root.resolve("auth").resolve("sessions").resolve("unrelated.txt").writeText("x")
        root.resolve("auth").resolve("sessions").resolve("not-a-hash.json").writeText("{}")

        assertEquals(setOf(session.tokenHash), sessions.allTokenHashes())
        assertEquals(emptySet(), FileSystemSessionRepository(root.resolve("missing")).allTokenHashes())
    }
}
