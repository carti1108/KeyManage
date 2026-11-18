package com.keymanage.android.data.crypto

import com.keymanage.android.domain.crypto.CryptoError
import com.keymanage.android.domain.crypto.KdfParameters
import com.keymanage.android.domain.crypto.MasterKeyDeriver
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

/**
 * PBKDF2-HMAC-SHA256 (RFC 8018). 출력은 32바이트 한 블록이다.
 *
 * `SecretKeyFactory("PBKDF2WithHmacSHA256")`은 비밀번호를 char[]로 받아 플랫폼마다 바이트 변환이 다를 수 있다.
 * 전처리를 마친 UTF-8 바이트를 그대로 쓰려고 HMAC으로 직접 계산한다(platform-bindings.md).
 */
class Pbkdf2MasterKeyDeriver : MasterKeyDeriver {

    override fun deriveMasterKey(password: ByteArray, parameters: KdfParameters): ByteArray {
        // JCE의 HMAC 키는 빈 값을 받지 않는다. 전처리 후 빈 비밀번호는 키를 유도하지 않고 거절한다
        if (password.isEmpty()) throw CryptoError.InvalidPassword("전처리 후 비밀번호가 비어 있다")
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(password, HMAC_SHA256))

        // 블록 1만 계산한다: U1 = HMAC(P, S || INT(1)), Ui = HMAC(P, U(i-1)), T = U1 xor ... xor Uc
        mac.update(parameters.salt)
        mac.update(byteArrayOf(0, 0, 0, 1))
        var u = mac.doFinal()
        val result = u.copyOf()
        repeat(parameters.iterations - 1) {
            u = mac.doFinal(u)
            for (i in result.indices) result[i] = result[i] xor u[i]
        }
        u.fill(0)
        return result.copyOf(KdfParameters.MASTER_KEY_LENGTH)
    }

    private companion object {
        const val HMAC_SHA256 = "HmacSHA256"
    }
}
