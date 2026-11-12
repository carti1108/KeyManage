package com.keymanage.crypto

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes

// docs/crypto-test-vectors.json과 같은 결과를 내는지 확인한다 (vault-spec 2장)
class VaultCryptoVectorTest {

    private val vectors: JsonObject = Json.parseToJsonElement(TEST_VECTORS_JSON).jsonObject
    private val iterations = vectors.getValue("kdf").jsonObject.getValue("iterations").jsonPrimitive.int
    private val keyCases = vectors.getValue("keyDerivationCases").jsonArray.map { it.jsonObject }

    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.bytes(name: String) = Base64.decode(text(name))

    @Test
    fun preprocess_matchesNormalizedUtf8ForEveryCase() {
        keyCases.forEach { case ->
            assertContentEquals(
                case.bytes("normalizedPasswordUtf8"),
                PasswordPreprocessor.preprocess(case.text("password")),
                "케이스 ${case.text("name")}",
            )
        }
    }

    @Test
    fun deriveKeys_matchesMasterEncryptionAndAuthForEveryCase() = runTest(timeout = 10.minutes) {
        keyCases.forEach { case ->
            val name = case.text("name")
            val masterKey = PasswordKeyDeriver.deriveMasterKey(
                password = PasswordPreprocessor.preprocess(case.text("password")),
                salt = case.bytes("salt"),
                iterations = iterations,
            )
            assertContentEquals(case.bytes("masterKey"), masterKey, "masterKey $name")
            assertContentEquals(case.bytes("encryptionKey"), KeySeparator.encryptionKey(masterKey), "encryptionKey $name")
            assertContentEquals(case.bytes("authHash"), KeySeparator.authHash(masterKey), "authHash $name")
        }
    }

    @Test
    fun vaultCipher_encryptsAndDecryptsVectorPayload() = runTest {
        val encryption = vectors.getValue("encryptionCases").jsonArray.first().jsonObject
        val key = keyCases.first { it.text("name") == encryption.text("keyFrom") }.bytes("encryptionKey")
        val payload = encryption.getValue("payload").jsonObject
        val aad = encryption.text("aad").encodeToByteArray()
        val plaintext = encryption.text("plaintext").encodeToByteArray()

        val sealed = VaultCipher.encrypt(key, plaintext, aad, iv = payload.bytes("iv"))
        assertContentEquals(payload.bytes("ciphertext"), sealed.ciphertext)
        assertContentEquals(payload.bytes("tag"), sealed.tag)

        val opened = VaultCipher.decrypt(key, SealedVault(payload.bytes("iv"), payload.bytes("ciphertext"), payload.bytes("tag")), aad)
        assertContentEquals(plaintext, opened)
    }

    @Test
    fun vaultCipher_rejectsTamperedAad() = runTest {
        val key = keyCases.first().bytes("encryptionKey")
        val sealed = VaultCipher.encrypt(key, "secret".encodeToByteArray(), "keymanage-vault|1|AES-256-GCM".encodeToByteArray())

        assertFailsWith<VaultDecryptionException> {
            VaultCipher.decrypt(key, sealed, "keymanage-vault|2|AES-256-GCM".encodeToByteArray())
        }
    }

    @Test
    fun vaultCipher_generatesFreshTwelveByteIv() = runTest {
        val key = keyCases.first().bytes("encryptionKey")
        val first = VaultCipher.encrypt(key, byteArrayOf(1), byteArrayOf())
        val second = VaultCipher.encrypt(key, byteArrayOf(1), byteArrayOf())

        assertEquals(12, first.iv.size)
        assertEquals(16, first.tag.size)
        assertEquals(false, first.iv.contentEquals(second.iv))
    }

    @Test
    fun deriveMasterKey_rejectsIterationsOutsideBounds() = runTest {
        val password = PasswordPreprocessor.preprocess("password")
        val salt = ByteArray(16)

        assertFailsWith<IllegalArgumentException> { PasswordKeyDeriver.deriveMasterKey(password, salt, 599_999) }
        assertFailsWith<IllegalArgumentException> { PasswordKeyDeriver.deriveMasterKey(password, salt, 10_000_001) }
        assertFailsWith<IllegalArgumentException> { PasswordKeyDeriver.deriveMasterKey(password, ByteArray(15), 600_000) }
    }

    @Test
    fun preprocess_trimsOnlyListedCodePointsAndKeepsInnerSpaces() {
        assertContentEquals("a b".encodeToByteArray(), PasswordPreprocessor.preprocess("　  \t\r\na b\n "))
        assertContentEquals(" x".encodeToByteArray(), PasswordPreprocessor.preprocess(" x"))
    }
}
