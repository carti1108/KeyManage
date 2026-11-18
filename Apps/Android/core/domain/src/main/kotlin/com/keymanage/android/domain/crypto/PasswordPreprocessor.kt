package com.keymanage.android.domain.crypto

import java.text.Normalizer

/**
 * 마스터 비밀번호 전처리(vault-spec 2장 '전처리 세부', ADR-0011).
 * NFC 정규화 → 지정 공백 6종만 앞뒤에서 제거 → UTF-8.
 */
object PasswordPreprocessor {
    private val TRIMMED = charArrayOf('\u0020', '\u0009', '\u000A', '\u000D', '\u00A0', '\u3000')

    fun preprocess(password: String): ByteArray {
        requireNoLoneSurrogate(password)
        val normalized = Normalizer.normalize(password, Normalizer.Form.NFC)
        return normalized.trim { it in TRIMMED }.toByteArray(Charsets.UTF_8)
    }

    private fun requireNoLoneSurrogate(text: String) {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate() -> index += 2
                char.isSurrogate() -> throw CryptoError.InvalidPassword("짝이 맞지 않는 서로게이트")
                else -> index += 1
            }
        }
    }
}
