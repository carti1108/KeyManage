package com.keymanage.back.domain.auth

/** 계정의 마스터 키 유도 값. 가입 때 클라이언트가 정하고 prelogin으로 돌려준다 (vault-spec 2장, SEC-002) */
class KdfParams(salt: ByteArray, val iterations: Int) {

    private val saltBytes = salt.copyOf()

    init {
        if (saltBytes.size != SALT_BYTES) throw InvalidAuthInputException("kdfSalt")
        if (iterations < MIN_ITERATIONS) throw InvalidAuthInputException("kdfIterations")
    }

    val salt: ByteArray get() = saltBytes.copyOf()

    override fun equals(other: Any?): Boolean =
        other is KdfParams && iterations == other.iterations && saltBytes.contentEquals(other.saltBytes)

    override fun hashCode(): Int = 31 * saltBytes.contentHashCode() + iterations

    override fun toString(): String = "KdfParams(iterations=$iterations)"

    companion object {
        const val ALGORITHM = "PBKDF2-HMAC-SHA256"
        const val SALT_BYTES = 16
        const val MIN_ITERATIONS = 600_000

        /** 클라이언트가 받는 반복 횟수 상한 (SEC-002). 가입은 하한만 보고, 비밀번호 변경은 이 상한까지 본다 (api-spec 2.5.5) */
        const val MAX_ITERATIONS = 10_000_000
    }
}
