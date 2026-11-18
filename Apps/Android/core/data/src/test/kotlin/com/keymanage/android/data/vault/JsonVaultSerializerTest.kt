package com.keymanage.android.data.vault

import com.keymanage.android.data.crypto.CryptoTestVectors
import com.keymanage.android.domain.vault.VaultFormatError
import com.keymanage.android.domain.vault.VaultRules
import org.json.JSONObject
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonVaultSerializerTest {
    private val serializer = JsonVaultSerializer()

    private fun decode(json: String) = serializer.decode(json.toByteArray(Charsets.UTF_8))

    private val item = """{"id":"550e8400-e29b-41d4-a716-446655440000","serviceName":"GitHub","username":"octo","password":"pw",""" +
        """"createdAt":"2026-10-27T10:00:00Z","updatedAt":"2026-10-27T11:20:00Z"}"""

    @Test
    fun `암호화 벡터의 평문 볼트를 읽는다`() {
        // Given
        val plaintext = CryptoTestVectors.encryptionCases().first().getString("plaintext")

        // When
        val vault = decode(plaintext)

        // Then
        assertEquals(1, vault.version)
        assertEquals("GitHub", vault.items.first().serviceName)
    }

    @Test
    fun `모르는 최상위·항목 필드는 다시 쓸 때 그대로 남는다`() {
        // Given
        val withItemExtra = item.dropLast(1) + ""","tags":["a","b"],"meta":{"n":1}}"""
        val json = """{"version":1,"updatedAt":"2026-10-27T11:20:00Z","folders":[{"id":"f"}],"items":[$withItemExtra]}"""

        // When
        val written = JSONObject(serializer.encode(decode(json)).toString(Charsets.UTF_8))

        // Then
        assertEquals("f", written.getJSONArray("folders").getJSONObject(0).getString("id"))
        val writtenItem = written.getJSONArray("items").getJSONObject(0)
        assertEquals("b", writtenItem.getJSONArray("tags").getString(1))
        assertEquals(1, writtenItem.getJSONObject("meta").getInt("n"))
    }

    @Test
    fun `선택 필드의 null은 없는 것으로 본다`() {
        val json = """{"version":1,"updatedAt":"2026-10-27T11:20:00Z","items":[${item.dropLast(1)},"url":null,"notes":null,"deletedAt":null}]}"""
        val parsed = decode(json).items.single()
        assertNull(parsed.url)
        assertNull(parsed.notes)
        assertFalse(parsed.isDeleted)
    }

    @Test
    fun `오프셋이 있는 시각은 받고 UTC Z 형식으로 다시 쓴다`() {
        // Given
        val json = """{"version":1,"updatedAt":"2026-10-27T20:20:00+09:00","items":[]}"""

        // When
        val vault = decode(json)
        val written = JSONObject(serializer.encode(vault).toString(Charsets.UTF_8))

        // Then
        assertEquals(Instant.parse("2026-10-27T11:20:00Z"), vault.updatedAt)
        assertEquals("2026-10-27T11:20:00Z", written.getString("updatedAt"))
    }

    @Test
    fun `시간대 표기가 없는 시각은 형식 오류`() {
        assertFailsWith<VaultFormatError> { decode("""{"version":1,"updatedAt":"2026-10-27T11:20:00","items":[]}""") }
    }

    @Test
    fun `필수 필드가 없거나 타입이 틀리면 형식 오류`() {
        assertFailsWith<VaultFormatError> { decode("""{"version":1,"items":[]}""") }
        assertFailsWith<VaultFormatError> { decode("""{"version":"1","updatedAt":"2026-10-27T11:20:00Z","items":[]}""") }
        assertFailsWith<VaultFormatError> { decode("""{"version":1.5,"updatedAt":"2026-10-27T11:20:00Z","items":[]}""") }
        assertFailsWith<VaultFormatError> { decode("""{"version":1,"updatedAt":"2026-10-27T11:20:00Z","items":[{"id":"a"}]}""") }
        assertFailsWith<VaultFormatError> { decode("""{"version":1,"updatedAt":"2026-10-27T11:20:00Z","items":[${item.replace("\"pw\"", "123")}]}""") }
        assertFailsWith<VaultFormatError> { decode("not json") }
    }

    @Test
    fun `버전이 0이면 형식 오류, 2면 읽기 전용으로 연다`() {
        assertFailsWith<VaultFormatError> { decode("""{"version":0,"updatedAt":"2026-10-27T11:20:00Z","items":[]}""") }
        assertTrue(decode("""{"version":2,"updatedAt":"2026-10-27T11:20:00Z","items":[]}""").isReadOnly)
    }

    @Test
    fun `삭제된 항목은 id와 시각 네 개만 쓴다`() {
        // Given
        val vault = decode("""{"version":1,"updatedAt":"2026-10-27T11:20:00Z","items":[$item]}""")
        val deleted = vault.copy(items = vault.items.map { VaultRules.tombstone(it, Instant.parse("2026-11-01T00:00:00Z")) })

        // When
        val writtenItem = JSONObject(serializer.encode(deleted).toString(Charsets.UTF_8)).getJSONArray("items").getJSONObject(0)

        // Then
        assertEquals(setOf("id", "createdAt", "updatedAt", "deletedAt"), writtenItem.keys().asSequence().toSet())
    }

    @Test
    fun `쓰고 다시 읽으면 같은 볼트다`() {
        val vault = decode("""{"version":1,"updatedAt":"2026-10-27T11:20:00Z","x":true,"items":[${item.dropLast(1)},"url":"https://g","notes":"n"}]}""")
        assertEquals(vault, serializer.decode(serializer.encode(vault)))
    }

    @Test
    fun `규격에 맞지 않는 볼트는 쓰지 않는다`() {
        val vault = decode("""{"version":1,"updatedAt":"2026-10-27T11:20:00Z","items":[$item]}""")
        val broken = vault.copy(items = vault.items.map { it.copy(serviceName = " ") })
        assertFailsWith<VaultFormatError> { serializer.encode(broken) }
    }
}
