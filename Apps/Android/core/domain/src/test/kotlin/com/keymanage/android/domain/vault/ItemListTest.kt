package com.keymanage.android.domain.vault

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ItemListTest {
    private val t0 = Instant.parse("2026-11-18T00:00:00Z")

    private fun item(id: String, service: String?, username: String? = null, url: String? = null, password: String = "p") =
        VaultItem(id = id, serviceName = service, username = username, password = password, url = url, createdAt = t0, updatedAt = t0)

    private val vault = VaultData(
        1, t0,
        listOf(
            item("1", "notion", "dev@example.com"),
            item("2", "GitHub", "octo"),
            item("3", "은행", null, url = "https://bank.example"),
            VaultRules.tombstone(item("4", "AWS"), t0),
            item("5", "github", "second"),
            item("6", "메모", "x", password = "github-secret"),
        ),
    )

    @Test
    fun `삭제되지 않은 항목을 서비스 이름순으로, 같으면 id순으로 준다`() {
        assertEquals(listOf("2", "5", "1", "6", "3"), ItemList.list(vault, "").map { it.id })
    }

    @Test
    fun `서비스 이름·아이디·URL에서 대소문자 없이 찾고 비밀번호는 찾지 않는다`() {
        assertEquals(listOf("2", "5"), ItemList.list(vault, "  GITHUB ").map { it.id })
        assertEquals(listOf("1"), ItemList.list(vault, "example.com").map { it.id })
        assertEquals(listOf("3"), ItemList.list(vault, "bank").map { it.id })
    }
}
