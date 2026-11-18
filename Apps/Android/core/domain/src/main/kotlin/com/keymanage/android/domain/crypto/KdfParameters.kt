package com.keymanage.android.domain.crypto

/**
 * 계정의 키 유도 파라미터. 서버(prelogin)에서 받은 값을 만들 때 범위를 검사한다(vault-spec 2장, SEC-002).
 */
class KdfParameters(salt: ByteArray, val iterations: Int) {
    private val saltBytes: ByteArray = salt.copyOf()

    val salt: ByteArray
        get() = saltBytes.copyOf()

    init {
        if (saltBytes.size != SALT_LENGTH) {
            throw CryptoError.InvalidKdfParameters("salt는 ${SALT_LENGTH}바이트여야 한다")
        }
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw CryptoError.InvalidKdfParameters("반복 횟수는 $MIN_ITERATIONS~$MAX_ITERATIONS 사이여야 한다")
        }
    }

    companion object {
        const val SALT_LENGTH = 16
        const val DEFAULT_ITERATIONS = 600_000
        const val MIN_ITERATIONS = 600_000
        const val MAX_ITERATIONS = 10_000_000
        const val MASTER_KEY_LENGTH = 32
    }
}
