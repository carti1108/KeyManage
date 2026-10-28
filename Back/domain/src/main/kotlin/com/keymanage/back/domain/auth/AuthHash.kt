package com.keymanage.back.domain.auth

/**
 * 클라이언트가 마스터 키에서 HKDF로 만든 인증 해시 32바이트 (vault-spec 2장, SEC-002).
 * 서버는 이 값으로 암호화 키를 계산할 수 없다. 로그에 남지 않도록 [toString]은 내용을 숨긴다
 */
class AuthHash private constructor(private val raw: ByteArray) {

    fun bytes(): ByteArray = raw.copyOf()

    override fun toString(): String = "AuthHash(****)"

    companion object {
        const val BYTES = 32

        /** @throws InvalidAuthInputException 32바이트가 아닐 때 */
        fun of(bytes: ByteArray): AuthHash {
            if (bytes.size != BYTES) throw InvalidAuthInputException("authHash")
            return AuthHash(bytes.copyOf())
        }
    }
}
