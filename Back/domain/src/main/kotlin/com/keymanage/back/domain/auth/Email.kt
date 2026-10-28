package com.keymanage.back.domain.auth

/** 계정을 식별하는 이메일. 앞뒤 공백을 지우고 소문자로 맞춘다 (SEC-002) */
@JvmInline
value class Email private constructor(val value: String) {

    companion object {
        const val MAX_LENGTH = 254

        // 형식은 가볍게만 본다: 공백 없는 local@domain.tld
        private val FORMAT = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")

        /** @throws InvalidAuthInputException 형식이 아니거나 254자를 넘을 때 */
        fun of(raw: String): Email {
            val normalized = raw.trim().lowercase()
            if (normalized.length > MAX_LENGTH || !FORMAT.matches(normalized)) throw InvalidAuthInputException("email")
            return Email(normalized)
        }
    }
}
