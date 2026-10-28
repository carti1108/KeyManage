package com.keymanage.back.infrastructure.vault

import com.keymanage.back.domain.vault.EncryptedVault
import com.keymanage.back.domain.vault.Revision
import com.keymanage.back.domain.vault.StoredVault
import com.keymanage.back.domain.vault.VaultId
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSystemVaultRepositoryTests {

    @TempDir
    lateinit var root: Path

    private val id = VaultId.of("550e8400-e29b-41d4-a716-446655440000")

    // 저장 폴더가 아직 없는 상태에서 시작해 첫 저장 때 만들어지는지도 함께 본다
    private fun storageDir(): Path = root.resolve("vaults")

    private fun repository() = FileSystemVaultRepository(storageDir())

    private fun vault(revision: Long, payload: String) = StoredVault(id, Revision(revision), EncryptedVault(payload))

    @Test
    fun `없는 볼트를 조회하면 null이다`() {
        assertNull(repository().find(id))
    }

    @Test
    fun `기대 revision이 0이면 첫 저장을 하고 볼트ID 이름의 파일을 만든다`() {
        val repository = repository()

        assertTrue(repository.replaceIfRevisionMatches(Revision.NONE, vault(1, "v1")))

        assertEquals(vault(1, "v1"), repository.find(id))
        assertTrue(Files.exists(storageDir().resolve("550e8400-e29b-41d4-a716-446655440000.json")))
    }

    @Test
    fun `기대 revision이 일치하면 교체한다`() {
        val repository = repository()
        repository.replaceIfRevisionMatches(Revision.NONE, vault(1, "v1"))

        assertTrue(repository.replaceIfRevisionMatches(Revision(1), vault(2, "v2")))

        assertEquals(vault(2, "v2"), repository.find(id))
    }

    @Test
    fun `기대 revision이 다르면 교체하지 않고 저장된 볼트는 그대로다`() {
        val repository = repository()
        repository.replaceIfRevisionMatches(Revision.NONE, vault(1, "v1"))

        assertFalse(repository.replaceIfRevisionMatches(Revision(5), vault(6, "stale")))
        assertFalse(repository.replaceIfRevisionMatches(Revision.NONE, vault(1, "stale")))

        assertEquals(vault(1, "v1"), repository.find(id))
    }

    @Test
    fun `저장한 볼트는 새 저장소 인스턴스로 다시 읽어도 같다`() {
        // 페이로드는 해석하지 않는 JSON 문자열이므로 따옴표·한글·줄바꿈이 그대로 보존돼야 한다
        val payload = """{"ciphertext":"k7Z9X","note":"한글\n줄바꿈"}"""
        repository().replaceIfRevisionMatches(Revision.NONE, vault(1, payload))

        assertEquals(vault(1, payload), repository().find(id))
    }

    @Test
    fun `저장 후 폴더에 임시 파일이 남지 않는다`() {
        val repository = repository()
        repository.replaceIfRevisionMatches(Revision.NONE, vault(1, "v1"))
        repository.replaceIfRevisionMatches(Revision(1), vault(2, "v2"))

        assertEquals(listOf("550e8400-e29b-41d4-a716-446655440000.json"), storageDir().listDirectoryEntries().map { it.name })
    }

    @RepeatedTest(20)
    fun `여러 스레드가 같은 revision으로 동시에 저장하면 한 건만 성공한다`() {
        val repository = repository()
        val threads = 8
        val executor = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val results = (1..threads).map { device ->
            executor.submit<Boolean> {
                start.await()
                repository.replaceIfRevisionMatches(Revision.NONE, vault(1, "device-$device"))
            }
        }

        start.countDown()
        val successes = results.count { it.get(5, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(1, successes)
        assertEquals(Revision(1), repository.find(id)?.revision)
    }
}
