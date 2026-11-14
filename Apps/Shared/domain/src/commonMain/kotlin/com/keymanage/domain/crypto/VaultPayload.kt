package com.keymanage.domain.crypto

import com.keymanage.crypto.SealedVault
import com.keymanage.crypto.VaultCipher
import com.keymanage.crypto.VaultDecryptionException
import com.keymanage.domain.encoding.InvalidBase64Exception
import com.keymanage.domain.encoding.fromBase64
import com.keymanage.domain.encoding.toBase64
import com.keymanage.domain.platform.SecureRandomBytes
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// 볼트 평문 JSON ↔ 암호화 페이로드 (vault-spec 3장, ADR-0011). 원본: Apps/Web/src/domain/crypto/vaultCipher.ts

private const val VERSION = 1
private const val CIPHER = "AES-256-GCM"
private const val IV_BYTES = 12
private const val TAG_BYTES = 16

/** 서버와 기기에 저장되는 암호화 볼트. 평문 헤더(version, cipher)는 AAD로 인증된다 */
data class EncryptedVaultPayload(
    val version: Int,
    val cipher: String,
    val iv: String,
    val ciphertext: String,
    val tag: String,
) {
    /** 저장·전송 형식. 키 순서는 원본 객체와 같다 */
    fun toJson(): JsonObject = JsonObject(
        linkedMapOf(
            "version" to JsonPrimitive(version),
            "cipher" to JsonPrimitive(cipher),
            "iv" to JsonPrimitive(iv),
            "ciphertext" to JsonPrimitive(ciphertext),
            "tag" to JsonPrimitive(tag),
        ),
    )
}

/** 키가 다르거나(다른 마스터 비밀번호·계정) 볼트가 변조되어 인증 태그 검증에 실패함 */
class VaultDecryptException(message: String = "마스터 키가 다르거나 볼트가 변조됨", cause: Throwable? = null) : Exception(message, cause)

/** 이 앱이 모르는 형식(version·cipher)이거나 필드 형식이 틀린 볼트. 복호화를 시도하지 않는다. 키 문제가 아니다 */
class UnsupportedPayloadException(message: String) : Exception(message)

private fun aadOf(version: Int, cipher: String) = "keymanage-vault|$version|$cipher".encodeToByteArray()

/**
 * 서버나 저장소에서 온 값은 타입을 믿지 않는다. 형식이 틀리면 키 문제와 구분해 알린다
 * @throws UnsupportedPayloadException
 */
fun parseEncryptedVaultPayload(element: JsonElement): EncryptedVaultPayload {
    val json = element as? JsonObject ?: throw UnsupportedPayloadException("페이로드가 객체가 아님")
    val version = (json["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
        ?: throw UnsupportedPayloadException("version은 정수여야 함")
    fun text(name: String) = (json[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: throw UnsupportedPayloadException("${name}은 문자열이어야 함")
    return EncryptedVaultPayload(version, text("cipher"), text("iv"), text("ciphertext"), text("tag"))
}

/** 암호화할 때마다 새 무작위 IV를 쓴다. 같은 키로 IV를 다시 쓰면 GCM의 기밀성과 무결성이 모두 깨진다 */
suspend fun sealVault(key: EncryptionKey, plaintext: String): EncryptedVaultPayload =
    seal(key, plaintext, SecureRandomBytes.nextBytes(IV_BYTES))

/** 테스트 벡터 재현 전용. 실제 코드에서 쓰면 IV 재사용으로 볼트가 노출된다 */
suspend fun sealVaultWithFixedIvForTest(key: EncryptionKey, plaintext: String, iv: ByteArray): EncryptedVaultPayload =
    seal(key, plaintext, iv)

private suspend fun seal(key: EncryptionKey, plaintext: String, iv: ByteArray): EncryptedVaultPayload {
    require(iv.size == IV_BYTES) { "IV는 ${IV_BYTES}바이트여야 함" }
    val sealed = VaultCipher.encrypt(key.usableBytes(), plaintext.encodeToByteArray(), aadOf(VERSION, CIPHER), iv)
    return EncryptedVaultPayload(VERSION, CIPHER, toBase64(sealed.iv), toBase64(sealed.ciphertext), toBase64(sealed.tag))
}

/**
 * @throws UnsupportedPayloadException 모르는 version·cipher이거나 필드가 표준 Base64가 아닐 때
 * @throws VaultDecryptException 키가 다르거나, 헤더·암호문이 변조됐거나, IV·태그 길이가 틀릴 때
 */
suspend fun openVault(key: EncryptionKey, payload: EncryptedVaultPayload): String {
    if (payload.version != VERSION || payload.cipher != CIPHER) {
        throw UnsupportedPayloadException("지원하지 않는 볼트 형식: version ${payload.version}, ${payload.cipher}")
    }
    val iv = decodeField(payload.iv, "iv")
    val ciphertext = decodeField(payload.ciphertext, "ciphertext")
    val tag = decodeField(payload.tag, "tag")
    if (iv.size != IV_BYTES || tag.size != TAG_BYTES) throw VaultDecryptException("IV 또는 태그 길이가 틀림")
    val plaintext = try {
        VaultCipher.decrypt(key.usableBytes(), SealedVault(iv, ciphertext, tag), aadOf(payload.version, payload.cipher))
    } catch (error: VaultDecryptionException) {
        throw VaultDecryptException(cause = error)
    }
    return plaintext.decodeToString()
}

private fun decodeField(value: String, name: String): ByteArray = try {
    fromBase64(value)
} catch (error: InvalidBase64Exception) {
    throw UnsupportedPayloadException("${name}이 표준 Base64가 아님")
}
