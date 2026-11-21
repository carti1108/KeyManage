package com.keymanage.crypto

/** 마스터 비밀번호 전처리: NFC 정규화 → 지정 공백만 앞뒤 제거 → UTF-8 (vault-spec 2장 '전처리 세부') */
object PasswordPreprocessor {
    // 플랫폼마다 trim 범위가 달라 문자를 고정한다
    private val trimmedCodePoints = setOf(' ', '\u0009', '\u000A', '\u000D', ' ', '　')

    fun preprocess(password: String): ByteArray =
        normalizeNfc(password).trim { it in trimmedCodePoints }.encodeToByteArray()
}

/** Kotlin 공통 코드에는 유니코드 정규화 API가 없어 대상마다 플랫폼 API를 쓴다 */
internal expect fun normalizeNfc(value: String): String
