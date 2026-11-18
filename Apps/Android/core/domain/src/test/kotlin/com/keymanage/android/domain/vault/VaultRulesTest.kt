package com.keymanage.android.domain.vault

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultRulesTest {
    private val base = Instant.parse("2026-11-18T00:00:00Z")

    private fun liveItem(updated: Instant = base) = VaultItem(
        id = "a",
        serviceName = "GitHub",
        username = "octo",
        password = "pw",
        url = "https://github.com",
        notes = "memo",
        createdAt = base,
        updatedAt = updated,
        extras = mapOf("future" to "1"),
    )

    @Test
    fun `변경 시각은 현재 시각과 이전 시각 + 1초 중 큰 값이다`() {
        assertEquals(base.plusSeconds(10), VaultRules.nextChangeTime(previous = base, now = base.plusSeconds(10)))
        assertEquals(base.plusSeconds(1), VaultRules.nextChangeTime(previous = base, now = base))
        assertEquals(base.plusSeconds(1), VaultRules.nextChangeTime(previous = base, now = base.minusSeconds(60)))
    }

    @Test
    fun `변경 시각은 초 단위로 자른다`() {
        val now = base.plusMillis(1_500)
        assertEquals(base.plusSeconds(1), VaultRules.nextChangeTime(previous = base.minusSeconds(5), now = now))
    }

    @Test
    fun `삭제하면 id와 시각만 남기고 내용과 모르는 필드를 지운다`() {
        // When
        val tombstone = VaultRules.tombstone(liveItem(), now = base.plusSeconds(30))

        // Then
        assertEquals("a", tombstone.id)
        assertEquals(base, tombstone.createdAt)
        assertEquals(base.plusSeconds(30), tombstone.updatedAt)
        assertEquals(base.plusSeconds(30), tombstone.deletedAt)
        assertNull(tombstone.serviceName)
        assertNull(tombstone.username)
        assertNull(tombstone.password)
        assertNull(tombstone.url)
        assertNull(tombstone.notes)
        assertTrue(tombstone.extras.isEmpty())
        assertTrue(tombstone.isDeleted)
    }

    @Test
    fun `서비스 이름은 앞뒤 공백을 뺀 뒤 1자 이상이어야 한다`() {
        assertFailsWith<VaultFormatError> { VaultRules.validate(vaultOf(liveItem().copy(serviceName = "   "))) }
        VaultRules.validate(vaultOf(liveItem().copy(serviceName = " G ")))
    }

    @Test
    fun `삭제되지 않은 항목은 아이디와 비밀번호 필드가 있어야 한다`() {
        assertFailsWith<VaultFormatError> { VaultRules.validate(vaultOf(liveItem().copy(username = null))) }
        assertFailsWith<VaultFormatError> { VaultRules.validate(vaultOf(liveItem().copy(password = null))) }
        VaultRules.validate(vaultOf(liveItem().copy(username = "", password = "")))
    }

    @Test
    fun `삭제된 항목은 내용 필드가 없어도 된다`() {
        VaultRules.validate(vaultOf(VaultRules.tombstone(liveItem(), base.plusSeconds(1))))
    }

    @Test
    fun `같은 id가 두 번 나오면 형식 오류`() {
        assertFailsWith<VaultFormatError> { VaultRules.validate(vaultOf(liveItem(), liveItem())) }
    }

    @Test
    fun `버전이 1보다 작으면 형식 오류, 1보다 크면 읽기 전용`() {
        assertFailsWith<VaultFormatError> { VaultRules.validate(vaultOf(liveItem()).copy(version = 0)) }
        assertTrue(vaultOf(liveItem()).copy(version = 2).isReadOnly)
        assertFalse(vaultOf(liveItem()).isReadOnly)
    }

    @Test
    fun `화면에 보일 항목은 삭제되지 않은 것만이다`() {
        val vault = vaultOf(liveItem(), VaultRules.tombstone(liveItem().copy(id = "b"), base.plusSeconds(1)))
        assertEquals(listOf("a"), vault.visibleItems.map { it.id })
    }

    private fun vaultOf(vararg items: VaultItem) =
        VaultData(version = VaultData.SUPPORTED_VERSION, updatedAt = base, items = items.toList())
}
