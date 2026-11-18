package com.keymanage.android.data.crypto

import com.keymanage.android.domain.crypto.CryptoError
import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import com.keymanage.android.domain.crypto.VaultCipher
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM 볼트 암호기(vault-spec 2·3장). IV 12바이트, 태그 16바이트, AAD는 페이로드 헤더(ADR-0011).
 * JCE는 암호문 뒤에 태그를 붙여 돌려주므로, 페이로드에서는 둘을 나눠 담는다.
 *
 * @param ivSource 새 IV를 만드는 함수. 테스트에서만 고정 IV를 넣는다.
 */
class AesGcmVaultCipher(
    private val ivSource: () -> ByteArray = ::randomIv,
) : VaultCipher {

    override fun encrypt(plaintext: ByteArray, key: ByteArray): EncryptedVaultPayload {
        requireKey(key)
        val iv = ivSource()
        if (iv.size != IV_LENGTH) throw CryptoError.InvalidInput("IV는 ${IV_LENGTH}바이트여야 한다")
        val header = EncryptedVaultPayload(
            version = EncryptedVaultPayload.CURRENT_VERSION,
            cipher = EncryptedVaultPayload.CIPHER_AES_256_GCM,
            iv = "",
            ciphertext = "",
            tag = "",
        )
        val sealed = newCipher(Cipher.ENCRYPT_MODE, key, iv, header.additionalAuthenticatedData).doFinal(plaintext)
        val ciphertextLength = sealed.size - TAG_LENGTH
        val encoder = Base64.getEncoder()
        return header.copy(
            iv = encoder.encodeToString(iv),
            ciphertext = encoder.encodeToString(sealed.copyOfRange(0, ciphertextLength)),
            tag = encoder.encodeToString(sealed.copyOfRange(ciphertextLength, sealed.size)),
        )
    }

    override fun decrypt(payload: EncryptedVaultPayload, key: ByteArray): ByteArray {
        requireKey(key)
        if (payload.cipher != EncryptedVaultPayload.CIPHER_AES_256_GCM) {
            throw CryptoError.InvalidInput("지원하지 않는 암호: ${payload.cipher}")
        }
        val iv = decodeBase64(payload.iv, "iv")
        val ciphertext = decodeBase64(payload.ciphertext, "ciphertext")
        val tag = decodeBase64(payload.tag, "tag")
        if (iv.size != IV_LENGTH) throw CryptoError.InvalidInput("IV는 ${IV_LENGTH}바이트여야 한다")
        if (tag.size != TAG_LENGTH) throw CryptoError.InvalidInput("태그는 ${TAG_LENGTH}바이트여야 한다")

        val cipher = newCipher(Cipher.DECRYPT_MODE, key, iv, payload.additionalAuthenticatedData)
        return try {
            cipher.doFinal(ciphertext + tag)
        } catch (error: AEADBadTagException) {
            throw CryptoError.AuthenticationFailed()
        }
    }

    private fun newCipher(mode: Int, key: ByteArray, iv: ByteArray, aad: String): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LENGTH * Byte.SIZE_BITS, iv))
            updateAAD(aad.toByteArray(Charsets.UTF_8))
        }

    private fun requireKey(key: ByteArray) {
        if (key.size != KEY_LENGTH) throw CryptoError.InvalidInput("키는 ${KEY_LENGTH}바이트여야 한다")
    }

    private fun decodeBase64(value: String, field: String): ByteArray =
        try {
            Base64.getDecoder().decode(value)
        } catch (error: IllegalArgumentException) {
            throw CryptoError.InvalidInput("$field 필드가 Standard Base64가 아니다")
        }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_LENGTH = 32
        const val IV_LENGTH = 12
        const val TAG_LENGTH = 16
        val secureRandom = SecureRandom()

        fun randomIv(): ByteArray = ByteArray(IV_LENGTH).also(secureRandom::nextBytes)
    }
}
