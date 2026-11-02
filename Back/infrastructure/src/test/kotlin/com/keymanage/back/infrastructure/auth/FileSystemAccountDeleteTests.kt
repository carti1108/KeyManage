package com.keymanage.back.infrastructure.auth

import com.keymanage.back.domain.auth.Account
import com.keymanage.back.domain.auth.Email
import com.keymanage.back.domain.auth.KdfParams
import com.keymanage.back.domain.auth.Session
import com.keymanage.back.domain.vault.VaultId
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 계정 삭제에 쓰는 계정 원자 삭제와 계정 세션 전부 삭제 (SEC-005, ADR-0024) */
class FileSystemAccountDeleteTests {

    @TempDir
    lateinit var root: Path

    private val email = Email.of("user@example.com")
    private val vaultId = VaultId.of("550e8400-e29b-41d4-a716-446655440000")
    private val otherVaultId = VaultId.of("650e8400-e29b-41d4-a716-446655440000")
    private val account = Account(email, KdfParams(ByteArray(KdfParams.SALT_BYTES) { 3 }, KdfParams.MIN_ITERATIONS), "hash", vaultId)

    private fun accountsDir(): Path = root.resolve("accounts")

    private fun accounts() = FileSystemAccountRepository(accountsDir())

    private fun sessionsDir(): Path = root.resolve("sessions")

    private fun sessions() = FileSystemSessionRepository(sessionsDir())

    @Test
    fun `저장된 계정이 기대한 계정과 같으면 지우고 새 인스턴스로 읽어도 없다`() {
        accounts().createIfAbsent(account)

        assertTrue(accounts().deleteIfMatches(account))

        assertNull(accounts().find(email))
        assertEquals(emptyList(), accountsDir().listDirectoryEntries())
    }

    @Test
    fun `저장된 계정이 기대와 다르면 지우지 않는다`() {
        accounts().createIfAbsent(account)

        assertFalse(accounts().deleteIfMatches(account.copy(passwordHash = "stale")))

        assertEquals(account, accounts().find(email))
    }

    @Test
    fun `없는 계정은 지우지 않고 실패하지도 않는다`() {
        assertFalse(accounts().deleteIfMatches(account))
    }

    @Test
    fun `지운 뒤 같은 이메일로 다시 만들 수 있다`() {
        val repository = accounts()
        repository.createIfAbsent(account)
        repository.deleteIfMatches(account)

        val again = account.copy(passwordHash = "new", vaultId = otherVaultId)

        assertTrue(repository.createIfAbsent(again))
        assertEquals(again, accounts().find(email))
    }

    @Test
    fun `지운 계정의 볼트는 가리키는 볼트 목록에서 빠진다`() {
        val repository = accounts()
        repository.createIfAbsent(account)
        repository.createIfAbsent(account.copy(email = Email.of("b@example.com"), vaultId = otherVaultId))

        repository.deleteIfMatches(account)

        assertEquals(setOf(otherVaultId), repository.allVaultIds())
    }

    @Test
    fun `계정 잠금 안에서 지울 수 있다`() {
        val repository = accounts()
        repository.createIfAbsent(account)

        assertTrue(repository.withLock(email) { repository.deleteIfMatches(account) })
    }

    @Test
    fun `계정 잠금을 잡은 동안 다른 스레드는 같은 계정을 지울 수 없다`() {
        val repository = accounts()
        repository.createIfAbsent(account)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val holder = executor.submit {
            repository.withLock(email) {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        entered.await(5, TimeUnit.SECONDS)
        val deletedWhileLocked = AtomicBoolean(false)
        val other = Thread { deletedWhileLocked.set(repository.deleteIfMatches(account)) }.apply { start() }

        other.join(200)
        assertTrue(other.isAlive)
        release.countDown()
        other.join(5_000)
        holder.get(5, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue(deletedWhileLocked.get())
    }

    @RepeatedTest(10)
    fun `같은 계정을 동시에 지우면 한 건만 성공한다`() {
        val repository = accounts()
        repository.createIfAbsent(account)
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val results = (1..8).map {
            executor.submit<Boolean> {
                start.await()
                repository.deleteIfMatches(account)
            }
        }

        start.countDown()
        val successes = results.count { it.get(5, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(1, successes)
    }

    @Test
    fun `계정의 세션을 볼트 ID와 관계없이 모두 지우고 다른 계정의 세션은 남긴다`() {
        val repository = sessions()
        val expiresAt = Instant.parse("2026-11-27T00:00:00Z")
        val mine = listOf(
            Session("a".repeat(64), email, expiresAt, vaultId),
            Session("b".repeat(64), email, expiresAt, otherVaultId),
        )
        val others = Session("c".repeat(64), Email.of("other@example.com"), expiresAt, vaultId)
        (mine + others).forEach(repository::save)

        repository.deleteAllOf(email)

        mine.forEach { assertNull(repository.find(it.tokenHash)) }
        assertNotNull(repository.find(others.tokenHash))
    }

    @Test
    fun `세션 폴더가 없어도 계정 세션 전부 삭제는 실패하지 않는다`() {
        sessions().deleteAllOf(email)

        assertFalse(Files.exists(sessionsDir()))
    }

    @Test
    fun `전부 삭제할 때 읽지 못하는 세션 파일과 세션이 아닌 파일은 건너뛴다`() {
        val repository = sessions()
        val session = Session("d".repeat(64), email, Instant.parse("2026-11-27T00:00:00Z"), vaultId)
        repository.save(session)
        sessionsDir().resolve("e".repeat(64) + ".json").writeText("{")
        sessionsDir().resolve("unrelated.txt").writeText("x")

        repository.deleteAllOf(email)

        assertNull(repository.find(session.tokenHash))
        assertTrue(Files.exists(sessionsDir().resolve("unrelated.txt")))
    }
}
