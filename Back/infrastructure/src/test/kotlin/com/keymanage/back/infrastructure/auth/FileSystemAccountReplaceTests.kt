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
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 비밀번호 변경에 쓰는 계정 원자 교체와 계정 세션 일괄 삭제 (ADR-BE-001 결정 2·3·6) */
class FileSystemAccountReplaceTests {

    @TempDir
    lateinit var root: Path

    private val email = Email.of("user@example.com")
    private val oldVaultId = VaultId.of("550e8400-e29b-41d4-a716-446655440000")
    private val newVaultId = VaultId.of("650e8400-e29b-41d4-a716-446655440000")
    private val account = Account(email, KdfParams(ByteArray(KdfParams.SALT_BYTES) { 3 }, KdfParams.MIN_ITERATIONS), "old-hash", oldVaultId)
    private val replacement = Account(email, KdfParams(ByteArray(KdfParams.SALT_BYTES) { 4 }, KdfParams.MIN_ITERATIONS), "new-hash", newVaultId)

    private fun accountsDir(): Path = root.resolve("accounts")

    private fun accounts() = FileSystemAccountRepository(accountsDir())

    private fun sessionsDir(): Path = root.resolve("sessions")

    private fun sessions() = FileSystemSessionRepository(sessionsDir())

    @Test
    fun `저장된 계정이 기대한 계정과 같으면 교체하고 새 인스턴스로 읽어도 바뀌어 있다`() {
        accounts().createIfAbsent(account)

        assertTrue(accounts().replaceIfMatches(account, replacement))

        assertEquals(replacement, accounts().find(email))
    }

    @Test
    fun `저장된 계정이 기대와 다르면 교체하지 않는다`() {
        val repository = accounts()
        repository.createIfAbsent(account)

        assertFalse(repository.replaceIfMatches(account.copy(passwordHash = "stale"), replacement))

        assertEquals(account, repository.find(email))
    }

    @Test
    fun `없는 계정은 교체하지 않고 만들지도 않는다`() {
        assertFalse(accounts().replaceIfMatches(account, replacement))

        assertNull(accounts().find(email))
    }

    @Test
    fun `이메일이 다른 계정으로는 교체할 수 없다`() {
        accounts().createIfAbsent(account)

        assertFailsWith<IllegalArgumentException> {
            accounts().replaceIfMatches(account, replacement.copy(email = Email.of("other@example.com")))
        }
        assertEquals(account, accounts().find(email))
    }

    @Test
    fun `교체 뒤 폴더에 임시 파일이 남지 않는다`() {
        val repository = accounts()
        repository.createIfAbsent(account)
        repository.replaceIfMatches(account, replacement)

        assertEquals(1, accountsDir().listDirectoryEntries().size)
        assertTrue(accountsDir().listDirectoryEntries().single().name.endsWith(".json"))
    }

    @RepeatedTest(10)
    fun `같은 계정을 동시에 교체하면 한 건만 성공한다`() {
        val repository = accounts()
        repository.createIfAbsent(account)
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val results = (1..8).map { n ->
            executor.submit<Boolean> {
                start.await()
                repository.replaceIfMatches(account, replacement.copy(passwordHash = "hash-$n"))
            }
        }

        start.countDown()
        val successes = results.count { it.get(5, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(1, successes)
    }

    @Test
    fun `계정 잠금은 같은 스레드에서 다시 잡을 수 있고 그 안에서 교체할 수 있다`() {
        val repository = accounts()
        repository.createIfAbsent(account)

        val replaced = repository.withLock(email) { repository.withLock(email) { repository.replaceIfMatches(account, replacement) } }

        assertTrue(replaced)
    }

    @Test
    fun `계정 잠금을 잡은 동안 다른 스레드는 같은 계정을 교체할 수 없다`() {
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
        val replacedWhileLocked = AtomicBoolean(false)
        val other = Thread { replacedWhileLocked.set(repository.replaceIfMatches(account, replacement)) }.apply { start() }

        other.join(200)
        assertTrue(other.isAlive)
        release.countDown()
        other.join(5_000)
        holder.get(5, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue(replacedWhileLocked.get())
    }

    @Test
    fun `모든 계정이 가리키는 볼트 ID를 돌려준다`() {
        val repository = accounts()
        repository.createIfAbsent(account)
        repository.createIfAbsent(replacement.copy(email = Email.of("b@example.com")))

        assertEquals(setOf(oldVaultId, newVaultId), repository.allVaultIds())
    }

    @Test
    fun `계정 폴더가 없으면 가리키는 볼트가 없다`() {
        assertEquals(emptySet(), accounts().allVaultIds())
    }

    @Test
    fun `읽지 못하는 계정 파일이 있으면 볼트 목록 대신 예외를 던진다`() {
        accounts().createIfAbsent(account)
        accountsDir().resolve("broken.json").writeText("{")

        assertFailsWith<Exception> { accounts().allVaultIds() }
    }

    @Test
    fun `쓰다 남은 임시 파일은 계정으로 보지 않는다`() {
        accounts().createIfAbsent(account)
        accountsDir().resolve("abc.json123.tmp").writeText("{")

        assertEquals(setOf(oldVaultId), accounts().allVaultIds())
    }

    @Test
    fun `계정의 세션을 모두 지우고 다른 계정의 세션은 남긴다`() {
        val repository = sessions()
        val expiresAt = Instant.parse("2026-11-27T00:00:00Z")
        val mine = listOf("a", "b").map { Session(it.repeat(64), email, expiresAt) }
        val others = Session("c".repeat(64), Email.of("other@example.com"), expiresAt)
        (mine + others).forEach(repository::save)

        repository.deleteAllOf(email)

        mine.forEach { assertNull(repository.find(it.tokenHash)) }
        assertNotNull(repository.find(others.tokenHash))
    }

    @Test
    fun `세션 폴더가 없어도 계정 세션 삭제는 실패하지 않는다`() {
        sessions().deleteAllOf(email)

        assertFalse(Files.exists(sessionsDir()))
    }

    @Test
    fun `읽지 못하는 세션 파일은 건너뛰고 나머지 세션을 지운다`() {
        val repository = sessions()
        val session = Session("d".repeat(64), email, Instant.parse("2026-11-27T00:00:00Z"))
        repository.save(session)
        sessionsDir().resolve("e".repeat(64) + ".json").writeText("{")
        sessionsDir().resolve("unrelated.txt").writeText("x")

        repository.deleteAllOf(email)

        assertNull(repository.find(session.tokenHash))
        assertTrue(Files.exists(sessionsDir().resolve("unrelated.txt")))
    }
}
