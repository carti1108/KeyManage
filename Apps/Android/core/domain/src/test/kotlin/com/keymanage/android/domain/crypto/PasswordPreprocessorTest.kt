package com.keymanage.android.domain.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class PasswordPreprocessorTest {
    @Test
    fun `NFD로 입력해도 NFC와 같은 바이트가 된다`() {
        // Given
        val composed = "Caf\u00E9"
        val decomposed = "Cafe\u0301"

        // When
        val a = PasswordPreprocessor.preprocess(composed)
        val b = PasswordPreprocessor.preprocess(decomposed)

        // Then
        assertContentEquals(a, b)
    }

    @Test
    fun `지정한 공백 6종만 앞뒤에서 자르고 중간 공백은 둔다`() {
        // Given
        val input = " \t\n\r\u00A0\u3000a b\u3000\u00A0\r\n\t "

        // When
        val result = PasswordPreprocessor.preprocess(input)

        // Then
        assertContentEquals("a b".toByteArray(Charsets.UTF_8), result)
    }

    @Test
    fun `지정하지 않은 공백(U+2003)은 자르지 않는다`() {
        // Given
        val input = "\u2003pw\u2003"

        // When
        val result = PasswordPreprocessor.preprocess(input)

        // Then
        assertContentEquals(input.toByteArray(Charsets.UTF_8), result)
    }

    @Test
    fun `짝 없는 서로게이트가 있으면 거절한다`() {
        assertFailsWith<CryptoError.InvalidPassword> { PasswordPreprocessor.preprocess("pw\uD83D") }
        assertFailsWith<CryptoError.InvalidPassword> { PasswordPreprocessor.preprocess("\uDD11pw") }
    }

    @Test
    fun `짝이 맞는 서로게이트(이모지)는 허용한다`() {
        // Given
        val input = "\uD83D\uDD11"

        // When
        val result = PasswordPreprocessor.preprocess(input)

        // Then
        assertContentEquals(input.toByteArray(Charsets.UTF_8), result)
    }
}
