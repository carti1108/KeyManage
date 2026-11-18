package com.keymanage.android.data.crypto

import com.keymanage.android.domain.crypto.CryptoError
import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class AesGcmVaultCipherTest {
    private val cipher = AesGcmVaultCipher()
    private val key = ByteArray(32) { it.toByte() }
    private val plaintext = "vault".toByteArray()
    private val encoder = Base64.getEncoder()

    @Test
    fun `암호화한 값을 같은 키로 복호화하면 원문이 나온다`() {
        val payload = cipher.encrypt(plaintext, key)
        assertContentEquals(plaintext, cipher.decrypt(payload, key))
    }

    @Test
    fun `암호화할 때마다 IV가 바뀐다`() {
        assertNotEquals(cipher.encrypt(plaintext, key).iv, cipher.encrypt(plaintext, key).iv)
    }

    @Test
    fun `새 페이로드의 헤더는 현재 버전과 AES-256-GCM이다`() {
        val payload = cipher.encrypt(plaintext, key)
        assertEquals(EncryptedVaultPayload.CURRENT_VERSION, payload.version)
        assertEquals(EncryptedVaultPayload.CIPHER_AES_256_GCM, payload.cipher)
    }

    @Test
    fun `다른 키로 복호화하면 인증 실패`() {
        val payload = cipher.encrypt(plaintext, key)
        assertFailsWith<CryptoError.AuthenticationFailed> { cipher.decrypt(payload, ByteArray(32)) }
    }

    @Test
    fun `헤더의 version을 바꾸면 AAD가 달라져 인증 실패`() {
        val payload = cipher.encrypt(plaintext, key).copy(version = 2)
        assertFailsWith<CryptoError.AuthenticationFailed> { cipher.decrypt(payload, key) }
    }

    @Test
    fun `태그를 바꾸면 인증 실패`() {
        val payload = cipher.encrypt(plaintext, key)
        val tag = Base64.getDecoder().decode(payload.tag)
        tag[0] = (tag[0].toInt() xor 1).toByte()
        assertFailsWith<CryptoError.AuthenticationFailed> {
            cipher.decrypt(payload.copy(tag = encoder.encodeToString(tag)), key)
        }
    }

    @Test
    fun `키가 32바이트가 아니면 거절한다`() {
        assertFailsWith<CryptoError.InvalidInput> { cipher.encrypt(plaintext, ByteArray(16)) }
        assertFailsWith<CryptoError.InvalidInput> { cipher.decrypt(cipher.encrypt(plaintext, key), ByteArray(31)) }
    }

    @Test
    fun `IV가 12바이트가 아니면 거절한다`() {
        val payload = cipher.encrypt(plaintext, key).copy(iv = encoder.encodeToString(ByteArray(11)))
        assertFailsWith<CryptoError.InvalidInput> { cipher.decrypt(payload, key) }
    }

    @Test
    fun `태그가 16바이트가 아니면 거절한다`() {
        val payload = cipher.encrypt(plaintext, key).copy(tag = encoder.encodeToString(ByteArray(15)))
        assertFailsWith<CryptoError.InvalidInput> { cipher.decrypt(payload, key) }
    }

    @Test
    fun `알 수 없는 암호 이름이나 Base64가 아닌 값은 거절한다`() {
        val payload = cipher.encrypt(plaintext, key)
        assertFailsWith<CryptoError.InvalidInput> { cipher.decrypt(payload.copy(cipher = "AES-128-CBC"), key) }
        assertFailsWith<CryptoError.InvalidInput> { cipher.decrypt(payload.copy(iv = "not base64*"), key) }
    }
}
