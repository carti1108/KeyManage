package com.keymanage.back.infrastructure.vault

import com.keymanage.back.domain.vault.EncryptedVault
import com.keymanage.back.domain.vault.Revision
import com.keymanage.back.domain.vault.StoredVault
import com.keymanage.back.domain.vault.VaultId
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 비밀번호 변경에 쓰는 새 볼트 ID 쓰기·옛 볼트 삭제·볼트 목록 (ADR-BE-001 결정 1·5) */
class FileSystemVaultSwapTests {

    @TempDir
    lateinit var root: Path

    private val oldId = VaultId.of("550e8400-e29b-41d4-a716-446655440000")
    private val newId = VaultId.of("650e8400-e29b-41d4-a716-446655440000")

    private fun storageDir(): Path = root.resolve("vaults")

    private fun repository() = FileSystemVaultRepository(storageDir())

    private fun vault(id: VaultId, revision: Long, payload: String) = StoredVault(id, Revision(revision), EncryptedVault(payload))

    @Test
    fun `새 볼트 ID로 볼트를 쓰면 그 revision 그대로 읽힌다`() {
        repository().create(vault(newId, 8, "new"))

        assertEquals(vault(newId, 8, "new"), repository().find(newId))
    }

    @Test
    fun `이미 있는 볼트 ID로는 새로 쓰지 않고 기존 볼트는 그대로다`() {
        val repository = repository()
        repository.replaceIfRevisionMatches(Revision.NONE, vault(oldId, 1, "old"))

        assertFailsWith<IllegalStateException> { repository.create(vault(oldId, 5, "new")) }

        assertEquals(vault(oldId, 1, "old"), repository.find(oldId))
    }

    @Test
    fun `볼트를 지우면 조회되지 않고 파일도 없다`() {
        val repository = repository()
        repository.replaceIfRevisionMatches(Revision.NONE, vault(oldId, 1, "old"))

        repository.delete(oldId)

        assertNull(repository.find(oldId))
        assertFalse(Files.exists(storageDir().resolve("${oldId.value}.json")))
    }

    @Test
    fun `지운 볼트 ID로는 다시 저장하지 않는다`() {
        val repository = repository()
        repository.replaceIfRevisionMatches(Revision.NONE, vault(oldId, 1, "old"))
        repository.delete(oldId)

        assertFalse(repository.replaceIfRevisionMatches(Revision(1), vault(oldId, 2, "late")))
        assertFalse(repository.replaceIfRevisionMatches(Revision.NONE, vault(oldId, 1, "late")))
        assertFailsWith<IllegalStateException> { repository.create(vault(oldId, 1, "late")) }

        assertNull(repository.find(oldId))
        assertFalse(Files.exists(storageDir().resolve("${oldId.value}.json")))
    }

    @Test
    fun `없는 볼트를 지워도 실패하지 않고 그 ID는 더 쓰지 않는다`() {
        val repository = repository()

        repository.delete(oldId)

        assertFalse(repository.replaceIfRevisionMatches(Revision.NONE, vault(oldId, 1, "late")))
    }

    @Test
    fun `저장된 볼트 ID 목록은 볼트 파일만 센다`() {
        val repository = repository()
        repository.create(vault(oldId, 1, "a"))
        repository.create(vault(newId, 1, "b"))
        storageDir().resolve("${oldId.value}.json123.tmp").writeText("x")
        storageDir().resolve("not-a-uuid.json").writeText("x")

        assertEquals(setOf(oldId, newId), repository.allIds())
    }

    @Test
    fun `볼트 폴더가 없으면 볼트 목록은 비어 있다`() {
        assertEquals(emptySet(), repository().allIds())
    }

    @Test
    fun `새로 쓴 뒤 폴더에 임시 파일이 남지 않는다`() {
        repository().create(vault(newId, 1, "b"))

        assertEquals(listOf("${newId.value}.json"), storageDir().listDirectoryEntries().map { it.name })
    }

    @Test
    fun `볼트 잠금을 잡은 동안 다른 스레드는 그 볼트를 저장할 수 없다`() {
        val repository = repository()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val holder = executor.submit {
            repository.withLock(oldId) {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        entered.await(5, TimeUnit.SECONDS)
        val saved = AtomicBoolean(false)
        val other = Thread { saved.set(repository.replaceIfRevisionMatches(Revision.NONE, vault(oldId, 1, "a"))) }.apply { start() }

        other.join(200)
        assertTrue(other.isAlive)
        release.countDown()
        other.join(5_000)
        holder.get(5, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue(saved.get())
    }

    @Test
    fun `볼트 잠금 안에서 같은 볼트를 다시 저장할 수 있다`() {
        val repository = repository()

        val saved = repository.withLock(oldId) { repository.replaceIfRevisionMatches(Revision.NONE, vault(oldId, 1, "a")) }

        assertTrue(saved)
    }
}
