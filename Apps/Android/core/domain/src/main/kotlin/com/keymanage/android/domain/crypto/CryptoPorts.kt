package com.keymanage.android.domain.crypto

/**
 * 암호 구성요소 포트. 구현은 `:core:data`에 두고 Domain은 이 인터페이스만 안다(ADR-AND-001).
 * 규격은 docs/vault-spec.md를 따른다.
 */
interface MasterKeyDeriver {
    /** PBKDF2-HMAC-SHA256으로 32바이트 마스터 키를 만든다. [password]는 [PasswordPreprocessor]를 거친 바이트다. */
    fun deriveMasterKey(password: ByteArray, parameters: KdfParameters): ByteArray
}

interface KeySeparator {
    /** HKDF-SHA256으로 마스터 키에서 암호화 키와 인증 해시를 나눈다. */
    fun separate(masterKey: ByteArray): SeparatedKeys
}

interface VaultCipher {
    fun encrypt(plaintext: ByteArray, key: ByteArray): EncryptedVaultPayload
    fun decrypt(payload: EncryptedVaultPayload, key: ByteArray): ByteArray
}

class SeparatedKeys(val encryptionKey: ByteArray, val authHash: ByteArray)

/** 서버에 저장되는 볼트 암호문(vault-spec 3장). 바이트 필드는 Standard Base64 문자열이다. */
data class EncryptedVaultPayload(
    val version: Int,
    val cipher: String,
    val iv: String,
    val ciphertext: String,
    val tag: String,
) {
    /** AES-GCM 추가 인증 데이터(ADR-0011). */
    val additionalAuthenticatedData: String
        get() = "keymanage-vault|$version|$cipher"

    companion object {
        const val CURRENT_VERSION = 1
        const val CIPHER_AES_256_GCM = "AES-256-GCM"
    }
}
