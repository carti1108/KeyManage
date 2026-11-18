package com.keymanage.android.domain.vault

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class VaultMergerTest {
    private fun t(seconds: Long): Instant = Instant.ofEpochSecond(1_800_000_000L + seconds)

    private fun item(id: String, updated: Long, name: String = id, deleted: Long? = null) = VaultItem(
        id = id,
        serviceName = if (deleted == null) name else null,
        username = if (deleted == null) "user" else null,
        password = if (deleted == null) "pw" else null,
        createdAt = t(0),
        updatedAt = t(updated),
        deletedAt = deleted?.let(::t),
    )

    private fun vault(updated: Long, vararg items: VaultItem, extras: Map<String, String> = emptyMap()) =
        VaultData(version = 1, updatedAt = t(updated), items = items.toList(), extras = extras)

    @Test
    fun `같은 id는 updatedAt이 늦은 쪽을 통째로 고른다`() {
        // Given
        val local = vault(10, item("a", updated = 20, name = "local"))
        val server = vault(5, item("a", updated = 10, name = "server"))

        // When
        val merged = VaultMerger.merge(local, server)

        // Then
        assertEquals("local", merged.items.single().serviceName)
    }

    @Test
    fun `시각이 같으면 서버 쪽을 고른다`() {
        val local = vault(1, item("a", updated = 10, name = "local"))
        val server = vault(1, item("a", updated = 10, name = "server"))
        assertEquals("server", VaultMerger.merge(local, server).items.single().serviceName)
    }

    @Test
    fun `삭제 시각이 수정 시각보다 늦으면 삭제가 이긴다`() {
        // Given: 서버는 20초에 수정, 이 기기는 10초 수정 항목을 30초에 삭제
        val local = vault(1, item("a", updated = 10, deleted = 30))
        val server = vault(1, item("a", updated = 20, name = "edited"))

        // When
        val merged = VaultMerger.merge(local, server)

        // Then
        assertEquals(t(30), merged.items.single().deletedAt)
    }

    @Test
    fun `한쪽에만 있는 항목은 그대로 두고 서버 항목 뒤에 붙인다`() {
        val local = vault(1, item("a", 1), item("local-only", 1))
        val server = vault(1, item("server-only", 1), item("a", 1))
        assertEquals(listOf("server-only", "a", "local-only"), VaultMerger.merge(local, server).items.map { it.id })
    }

    @Test
    fun `볼트 updatedAt은 늦은 값, 모르는 최상위 필드는 서버를 따른다`() {
        val local = vault(50, extras = mapOf("x" to "\"local\""))
        val server = vault(40, extras = mapOf("x" to "\"server\""))
        val merged = VaultMerger.merge(local, server)
        assertEquals(t(50), merged.updatedAt)
        assertEquals(mapOf("x" to "\"server\""), merged.extras)
    }
}
