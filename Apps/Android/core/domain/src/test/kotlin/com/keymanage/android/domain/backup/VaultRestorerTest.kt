package com.keymanage.android.domain.backup

import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultFormatError
import com.keymanage.android.domain.vault.VaultItem
import com.keymanage.android.domain.vault.VaultMerger
import com.keymanage.android.domain.vault.VaultRules
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class VaultRestorerTest {
    private val t0 = Instant.parse("2026-11-01T00:00:00Z")
    private val now = Instant.parse("2026-11-18T09:00:00Z")

    private fun item(id: String, password: String = "p", updatedSeconds: Long = 0) = VaultItem(
        id = id, serviceName = id, username = "u", password = password,
        createdAt = t0, updatedAt = t0.plusSeconds(updatedSeconds),
    )

    private fun vault(vararg items: VaultItem) = VaultData(1, t0, items.toList())

    @Test
    fun `백업 뒤 고친 항목은 백업 내용으로 돌아가고 수정 시각은 복원 시각이다`() {
        // Given
        val backup = vault(item("a", password = "old"))
        val current = vault(item("a", password = "new", updatedSeconds = 60))

        // When
        val result = VaultRestorer.restore(current, backup, now)

        // Then
        val restored = result.vault.items.single()
        assertEquals("old", restored.password)
        assertEquals(now, restored.updatedAt)
        assertEquals(1, result.restored)
    }

    @Test
    fun `지운 항목은 되살아나고 백업 뒤 새로 만든 항목은 남는다`() {
        // Given
        val backup = vault(item("a"))
        val current = vault(VaultRules.tombstone(item("a"), t0.plusSeconds(10)), item("new"))

        // When
        val result = VaultRestorer.restore(current, backup, now)

        // Then
        assertEquals(listOf("a", "new"), result.vault.visibleItems.map { it.id })
        assertEquals(1, result.restored)
    }

    @Test
    fun `지금 볼트에 없는 백업 항목은 뒤에 더한다`() {
        val result = VaultRestorer.restore(vault(item("x")), vault(item("a")), now)
        assertEquals(listOf("x", "a"), result.vault.items.map { it.id })
        assertEquals(now, result.vault.items.last().updatedAt)
    }

    @Test
    fun `내용이 같은 항목과 백업에서 지워진 항목은 건드리지 않는다`() {
        // Given
        val current = vault(item("a", updatedSeconds = 30), item("b"))
        val backup = vault(item("a"), VaultRules.tombstone(item("b"), t0.plusSeconds(5)))

        // When
        val result = VaultRestorer.restore(current, backup, now)

        // Then: 바뀐 것이 없으면 지금 볼트를 그대로 돌려준다
        assertSame(current, result.vault)
        assertEquals(0, result.restored)
    }

    @Test
    fun `지금 항목의 변경 시각이 더 늦으면 그보다 1초 뒤로 정한다`() {
        // Given: 시계가 앞선 기기에서 고친 항목
        val future = Instant.parse("2026-12-01T00:00:00Z")
        val current = vault(item("a", password = "new").copy(updatedAt = future))

        // When
        val result = VaultRestorer.restore(current, vault(item("a", password = "old")), now)

        // Then
        assertEquals(future.plusSeconds(1), result.vault.items.single().updatedAt)
    }

    @Test
    fun `복원한 항목은 다른 기기의 지금 볼트와 병합해도 남는다`() {
        // Given
        val other = vault(item("a", password = "new", updatedSeconds = 60))
        val restored = VaultRestorer.restore(other, vault(item("a", password = "old")), now).vault

        // When
        val merged = VaultMerger.merge(restored, other)

        // Then
        assertEquals("old", merged.items.single().password)
    }

    @Test
    fun `볼트 updatedAt은 복원 시각까지 올린다`() {
        val result = VaultRestorer.restore(vault(item("x")), vault(item("a")), now)
        assertEquals(now, result.vault.updatedAt)
    }

    @Test
    fun `백업에 같은 id가 두 번 있으면 형식 오류`() {
        assertFailsWith<VaultFormatError> { VaultRestorer.restore(vault(), vault(item("a"), item("a")), now) }
    }
}
