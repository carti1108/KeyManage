package com.keymanage.domain.crypto

import com.keymanage.domain.encoding.fromBase64
import com.keymanage.domain.encoding.toBase64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.minutes

// 원본: Apps/Web/src/domain/crypto/vaultCipher.test.ts (vault-spec 3장, ADR-0011)
class VaultPayloadTest {
    private val vector = Vectors.encryptionCases[0]
    private val payloadJson = vector.getValue("payload").jsonObject
    private val payload = parseEncryptedVaultPayload(payloadJson)

    private suspend fun key() = Vectors.keysOf(vector.text("keyFrom")).encryptionKey
    private suspend fun otherKey() = Vectors.keysOf("korean-emoji").encryptionKey

    private fun modified(field: String, value: kotlinx.serialization.json.JsonElement) = JsonObject(payloadJson + (field to value))

    @Test
    fun fixedIvSealMatchesVectorPayload() = runTest(timeout = 5.minutes) {
        val sealed = sealVaultWithFixedIvForTest(key(), vector.text("plaintext"), fromBase64(payload.iv))
        assertEquals(payloadJson, sealed.toJson())
        // 키 순서까지 웹 JSON.stringify와 같다
        assertEquals(payloadJson.toString(), sealed.toJson().toString())
    }

    @Test
    fun opensVectorPayload() = runTest(timeout = 5.minutes) {
        assertEquals(vector.text("plaintext"), openVault(key(), payload))
    }

    @Test
    fun eachSealUsesFresh12ByteIv() = runTest(timeout = 5.minutes) {
        val first = sealVault(key(), "same")
        val second = sealVault(key(), "same")

        assertEquals(12, fromBase64(first.iv).size)
        assertNotEquals(first.iv, second.iv)
        assertEquals("same", openVault(key(), first))
    }

    @Test
    fun otherAccountsKeyCannotOpen() = runTest(timeout = 5.minutes) {
        assertFailsWith<VaultDecryptException> { openVault(otherKey(), payload) }
    }

    @Test
    fun anyCiphertextChangeCannotOpen() = runTest(timeout = 5.minutes) {
        val bytes = fromBase64(payload.ciphertext)
        bytes[0] = (bytes[0].toInt() xor 1).toByte()

        assertFailsWith<VaultDecryptException> { openVault(key(), payload.copy(ciphertext = toBase64(bytes))) }
    }

    @Test
    fun unsupportedVersionOrCipherIsRejectedWithoutDecrypting() = runTest(timeout = 5.minutes) {
        assertFailsWith<UnsupportedPayloadException> { openVault(key(), payload.copy(version = 2)) }
        assertFailsWith<UnsupportedPayloadException> { openVault(key(), payload.copy(cipher = "AES-128-GCM")) }
    }

    @Test
    fun nonStringFieldsAreFormatErrorsNotKeyErrors() {
        assertFailsWith<UnsupportedPayloadException> { parseEncryptedVaultPayload(modified("ciphertext", JsonNull)) }
        assertFailsWith<UnsupportedPayloadException> { parseEncryptedVaultPayload(modified("iv", JsonPrimitive(12345678))) }
        assertFailsWith<UnsupportedPayloadException> { parseEncryptedVaultPayload(JsonPrimitive("not an object")) }
        assertFailsWith<UnsupportedPayloadException> { parseEncryptedVaultPayload(modified("version", JsonPrimitive("1"))) }
    }

    @Test
    fun wrongIvOrTagLengthCannotOpen() = runTest(timeout = 5.minutes) {
        assertFailsWith<VaultDecryptException> { openVault(key(), payload.copy(iv = toBase64(ByteArray(8)))) }
        assertFailsWith<VaultDecryptException> { openVault(key(), payload.copy(tag = toBase64(ByteArray(8)))) }
    }

    @Test
    fun nonStandardBase64IsFormatError() = runTest(timeout = 5.minutes) {
        assertFailsWith<UnsupportedPayloadException> { openVault(key(), payload.copy(iv = "${payload.iv}\n")) }
        assertFailsWith<UnsupportedPayloadException> { openVault(key(), payload.copy(tag = payload.tag.trimEnd('='))) }
    }
}
