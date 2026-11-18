package com.keymanage.android.data.crypto

import com.keymanage.android.domain.crypto.CryptoError
import com.keymanage.android.domain.crypto.KdfParameters
import com.keymanage.android.domain.crypto.KeySeparator
import com.keymanage.android.domain.crypto.SeparatedKeys
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF-SHA256 (RFC 5869). JDK에 HKDF API가 없어 HMAC으로 Extract·Expand를 계산한다(platform-bindings.md).
 * salt는 빈 값이며 RFC에 따라 32바이트 0을 쓴다. info는 `keymanage-enc` / `keymanage-auth`(vault-spec 2장).
 */
class HkdfKeySeparator : KeySeparator {

    override fun separate(masterKey: ByteArray): SeparatedKeys {
        if (masterKey.size != KdfParameters.MASTER_KEY_LENGTH) {
            throw CryptoError.InvalidInput("마스터 키는 ${KdfParameters.MASTER_KEY_LENGTH}바이트여야 한다")
        }
        val prk = hmac(ByteArray(HASH_LENGTH), masterKey)
        try {
            return SeparatedKeys(
                encryptionKey = expand(prk, ENCRYPTION_INFO, OUTPUT_LENGTH),
                authHash = expand(prk, AUTH_INFO, OUTPUT_LENGTH),
            )
        } finally {
            prk.fill(0)
        }
    }

    private fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            previous = hmac(prk, previous + info + byteArrayOf(counter.toByte()))
            val count = minOf(previous.size, length - offset)
            previous.copyInto(output, destinationOffset = offset, endIndex = count)
            offset += count
            counter += 1
        }
        previous.fill(0)
        return output
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance(HMAC_SHA256).run {
            init(SecretKeySpec(key, HMAC_SHA256))
            doFinal(data)
        }

    private companion object {
        const val HMAC_SHA256 = "HmacSHA256"
        const val HASH_LENGTH = 32
        const val OUTPUT_LENGTH = 32
        val ENCRYPTION_INFO = "keymanage-enc".toByteArray(Charsets.UTF_8)
        val AUTH_INFO = "keymanage-auth".toByteArray(Charsets.UTF_8)
    }
}
