package com.keymanage.android.domain.vault

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultEditingTest {
    private val t0 = Instant.parse("2026-11-01T00:00:00Z")
    private val now = Instant.parse("2026-11-18T09:00:00.700Z")
    private val fields = ItemFields("GitHub", "octo", "secret")
    private val empty = VaultData(1, t0, emptyList())

    @Test
    fun `서비스 이름이 비면 문제로 본다`() {
        assertEquals(ItemFieldsProblem.MISSING_SERVICE_NAME, VaultEditing.check(fields.copy(serviceName = "  ")))
        assertNull(VaultEditing.check(fields))
    }

    @Test
    fun `추가하면 초 단위 시각으로 만들고 빈 선택 필드는 없음으로 둔다`() {
        // When
        val vault = VaultEditing.add(empty, fields, now, id = "a")

        // Then
        val item = vault.items.single()
        assertEquals(Instant.parse("2026-11-18T09:00:00Z"), item.createdAt)
        assertNull(item.url)
        assertNull(item.notes)
        assertEquals(item.updatedAt, vault.updatedAt)
    }

    @Test
    fun `같은 id로는 추가하지 않는다`() {
        val vault = VaultEditing.add(empty, fields, now, id = "a")
        assertFailsWith<IllegalArgumentException> { VaultEditing.add(vault, fields, now, id = "a") }
    }

    @Test
    fun `바뀐 것이 없으면 null을 돌려 저장하지 않는다`() {
        val vault = VaultEditing.add(empty, fields, now, id = "a")
        assertNull(VaultEditing.update(vault, "a", fields, now.plusSeconds(60)))
    }

    @Test
    fun `수정하면 이전 시각보다 늦게, 지운 선택 필드는 없음으로 둔다`() {
        // Given: 같은 초에 다시 고친다
        val vault = VaultEditing.add(empty, fields.copy(url = "https://github.com"), now, id = "a")

        // When
        val updated = checkNotNull(VaultEditing.update(vault, "a", fields.copy(password = "new"), now))

        // Then
        val item = updated.items.single()
        assertEquals("new", item.password)
        assertNull(item.url)
        assertEquals(Instant.parse("2026-11-18T09:00:01Z"), item.updatedAt)
    }

    @Test
    fun `삭제하면 삭제 표시만 남고 목록에서 빠진다`() {
        val vault = VaultEditing.add(empty, fields, now, id = "a")
        val deleted = VaultEditing.delete(vault, "a", now.plusSeconds(5))
        assertTrue(deleted.items.single().isDeleted)
        assertTrue(deleted.visibleItems.isEmpty())
    }

    @Test
    fun `지워졌거나 없는 항목은 고치거나 지울 수 없다`() {
        val deleted = VaultEditing.delete(VaultEditing.add(empty, fields, now, id = "a"), "a", now)
        assertFailsWith<VaultItemNotFound> { VaultEditing.update(deleted, "a", fields, now) }
        assertFailsWith<VaultItemNotFound> { VaultEditing.delete(empty, "x", now) }
    }
}
