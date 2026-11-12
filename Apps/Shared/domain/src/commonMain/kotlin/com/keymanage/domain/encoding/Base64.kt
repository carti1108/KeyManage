package com.keymanage.domain.encoding

import kotlin.io.encoding.Base64

// Standard Base64 (RFC 4648) ↔ 바이트. 원본: Apps/Web/src/domain/crypto/base64.ts
// 볼트 암호문·salt·IV를 서버·기기에 담는 형식이라, 플랫폼마다 판단이 갈리지 않게 표준 형식만 받는다

/** 표준 Base64가 아님 (공백, 패딩 누락, URL용 문자 등) */
class InvalidBase64Exception(cause: Throwable? = null) : IllegalArgumentException("표준 Base64 형식이 아님", cause)

// 4글자 묶음 반복 정규식은 큰 입력에서 스택이 넘칠 수 있어, 문자 검사와 길이 검사로 나눈다(원본과 같은 이유)
private val STANDARD = Regex("^[A-Za-z0-9+/]*={0,2}$")

private val strict = Base64.Default.withPadding(Base64.PaddingOption.PRESENT)

fun toBase64(bytes: ByteArray): String = strict.encode(bytes)

/** @throws InvalidBase64Exception 표준 Base64가 아닐 때 */
fun fromBase64(text: String): ByteArray {
    if (text.length % 4 != 0 || !STANDARD.matches(text)) throw InvalidBase64Exception()
    return try {
        strict.decode(text)
    } catch (error: IllegalArgumentException) {
        // 패딩 뒤에 남은 비트가 0이 아닌 경우 등, 정규식이 거르지 못한 형식 오류
        throw InvalidBase64Exception(error)
    }
}
