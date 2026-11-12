package com.keymanage.crypto

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.coroutines.cancellation.CancellationException

// vault-spec 2장 파라미터. 라이브러리 타입은 이 파일 밖으로 내보내지 않는다(다른 구현으로 바꿀 때 범위를 줄이려고)
private const val MIN_ITERATIONS = 600_000
private const val MAX_ITERATIONS = 10_000_000
private const val SALT_SIZE = 16
private const val KEY_BITS = 256
private const val IV_SIZE = 12
private const val TAG_SIZE = 16

private val provider get() = CryptographyProvider.Default

/** PBKDF2-HMAC-SHA256으로 마스터 키(32바이트)를 유도한다 */
object PasswordKeyDeriver {
    suspend fun deriveMasterKey(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "반복 횟수는 $MIN_ITERATIONS~$MAX_ITERATIONS 사이여야 한다: $iterations" }
        require(salt.size == SALT_SIZE) { "salt는 ${SALT_SIZE}바이트여야 한다: ${salt.size}" }
        return provider.get(PBKDF2)
            .secretDerivation(digest = SHA256, iterations = iterations, outputSize = KEY_BITS.bits, salt = salt)
            .deriveSecretToByteArray(password)
    }
}

/** HKDF-SHA256으로 마스터 키에서 암호화 키와 인증 해시를 나눈다. salt는 빈 값 */
object KeySeparator {
    suspend fun encryptionKey(masterKey: ByteArray): ByteArray = derive(masterKey, "keymanage-enc")
    suspend fun authHash(masterKey: ByteArray): ByteArray = derive(masterKey, "keymanage-auth")

    private suspend fun derive(masterKey: ByteArray, info: String): ByteArray =
        provider.get(HKDF)
            .secretDerivation(digest = SHA256, outputSize = KEY_BITS.bits, salt = ByteArray(0), info = info.encodeToByteArray())
            .deriveSecretToByteArray(masterKey)
}

/** AES-256-GCM 결과. 태그는 따로 둔다(EncryptedVaultPayload 형식) */
class SealedVault(val iv: ByteArray, val ciphertext: ByteArray, val tag: ByteArray)

/** 태그 검증 실패 등으로 볼트를 열 수 없을 때 */
class VaultDecryptionException(cause: Throwable) : Exception("볼트를 복호화할 수 없다", cause)

/**
 * AES-256-GCM 볼트 암호화. IV 12바이트, 태그 16바이트.
 * IV를 페이로드에 따로 저장하는 형식이라(vault-spec 3장) 라이브러리의 IV 직접 지정 API를 쓴다.
 * IV는 기본값으로 매번 새 난수를 만들고, 고정 IV는 테스트 벡터 확인에만 쓴다.
 */
@OptIn(DelicateCryptographyApi::class)
object VaultCipher {
    suspend fun encrypt(
        key: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray,
        iv: ByteArray = CryptographyRandom.nextBytes(IV_SIZE),
    ): SealedVault {
        require(iv.size == IV_SIZE) { "IV는 ${IV_SIZE}바이트여야 한다: ${iv.size}" }
        val combined = cipherFor(key).encryptWithIv(iv, plaintext, associatedData)
        val split = combined.size - TAG_SIZE
        return SealedVault(iv, combined.copyOfRange(0, split), combined.copyOfRange(split, combined.size))
    }

    suspend fun decrypt(key: ByteArray, sealed: SealedVault, associatedData: ByteArray): ByteArray {
        require(sealed.iv.size == IV_SIZE) { "IV는 ${IV_SIZE}바이트여야 한다: ${sealed.iv.size}" }
        require(sealed.tag.size == TAG_SIZE) { "태그는 ${TAG_SIZE}바이트여야 한다: ${sealed.tag.size}" }
        return try {
            cipherFor(key).decryptWithIv(sealed.iv, sealed.ciphertext + sealed.tag, associatedData)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // WebCrypto의 OperationError는 Kotlin Exception이 아닌 JS 오류라 Throwable로 받는다
            throw VaultDecryptionException(e)
        }
    }

    private suspend fun cipherFor(key: ByteArray) =
        provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key).cipher()
}
