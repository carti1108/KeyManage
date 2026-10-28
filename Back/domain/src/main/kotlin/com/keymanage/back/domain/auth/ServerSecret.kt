package com.keymanage.back.domain.auth

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** 설치본마다 다른 서버 비밀키 32바이트. 없는 이메일의 가짜 salt를 만드는 데 쓴다 (SEC-002) */
class ServerSecret(bytes: ByteArray) {

    private val key = bytes.copyOf()

    init {
        require(key.size == BYTES) { "서버 비밀키는 ${BYTES}바이트여야 함" }
    }

    fun hmac(data: ByteArray): ByteArray =
        Mac.getInstance(HMAC).apply { init(SecretKeySpec(key, HMAC)) }.doFinal(data)

    override fun toString(): String = "ServerSecret(****)"

    companion object {
        const val BYTES = 32
        private const val HMAC = "HmacSHA256"
    }
}
