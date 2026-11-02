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
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSystemAuthRepositoryTests {

    @TempDir
    lateinit var root: Path

    private val email = Email.of("user@example.com")
    private val kdf = KdfParams(ByteArray(KdfParams.SALT_BYTES) { 3 }, KdfParams.MIN_ITERATIONS)
    private val account = Account(email, kdf, "\$argon2id\$v=19\$m=19456,t=2,p=1\$abc\$def", VaultId.of("550e8400-e29b-41d4-a716-446655440000"))

    private fun accounts() = FileSystemAccountRepository(root.resolve("accounts"))

    private fun sessions() = FileSystemSessionRepository(root.resolve("sessions"))

    @Test
    fun `없는 계정을 조회하면 null이다`() {
        assertNull(accounts().find(email))
    }

    @Test
    fun `만든 계정은 새 저장소 인스턴스로 다시 읽어도 같다`() {
        assertTrue(accounts().createIfAbsent(account))

        assertEquals(account, accounts().find(email))
    }

    @Test
    fun `같은 이메일 계정이 있으면 만들지 않고 기존 계정은 그대로다`() {
        val repository = accounts()
        repository.createIfAbsent(account)

        assertFalse(repository.createIfAbsent(account.copy(passwordHash = "other")))

        assertEquals(account, repository.find(email))
    }

    @Test
    fun `계정 파일 이름에는 이메일 원문이 들어가지 않는다`() {
        accounts().createIfAbsent(account)

        val names = root.resolve("accounts").listDirectoryEntries().map { it.name }
        assertEquals(1, names.size)
        assertFalse(names.single().contains("example"))
    }

    @RepeatedTest(10)
    fun `같은 이메일로 동시에 만들면 한 건만 성공한다`() {
        val repository = accounts()
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val results = (1..8).map { n ->
            executor.submit<Boolean> {
                start.await()
                repository.createIfAbsent(account.copy(passwordHash = "hash-$n"))
            }
        }

        start.countDown()
        val successes = results.count { it.get(5, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(1, successes)
    }

    @Test
    fun `저장한 세션은 토큰 해시로 다시 읽을 수 있다`() {
        val session = Session("a".repeat(64), email, Instant.parse("2026-11-27T00:00:00Z"), account.vaultId)
        sessions().save(session)

        assertEquals(session, sessions().find(session.tokenHash))
    }

    @Test
    fun `세션을 지우면 조회되지 않고 다시 지워도 실패하지 않는다`() {
        val repository = sessions()
        val session = Session("b".repeat(64), email, Instant.parse("2026-11-27T00:00:00Z"), account.vaultId)
        repository.save(session)

        repository.delete(session.tokenHash)
        repository.delete(session.tokenHash)

        assertNull(repository.find(session.tokenHash))
    }

    @Test
    fun `16진수 해시가 아닌 키로는 파일에 접근하지 않는다`() {
        assertNull(sessions().find("../../accounts/x"))
        sessions().delete("../../accounts/x")
    }

    @Test
    fun `세션 파일에는 토큰 해시와 이메일, 만료 시각만 있다`() {
        val session = Session("c".repeat(64), email, Instant.parse("2026-11-27T00:00:00Z"), account.vaultId)
        sessions().save(session)

        val text = root.resolve("sessions").resolve("${session.tokenHash}.json").readText()
        assertTrue(text.contains("user@example.com"))
        assertTrue(Files.exists(root.resolve("sessions")))
    }
}
