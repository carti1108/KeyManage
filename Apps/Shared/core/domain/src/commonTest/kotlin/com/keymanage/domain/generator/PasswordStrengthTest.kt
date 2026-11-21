package com.keymanage.domain.generator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 원본: Apps/Web/src/domain/generator/passwordStrength.test.ts (GEN-001)
class PasswordStrengthTest {
    @Test
    fun crossPlatformSamples() {
        mapOf("123456" to 19, "abc~~~~~" to 40, "Aa1!Aa1!Aa1!" to 77, "Aa1!Aa1!Aa1!Aa1!" to 100)
            .forEach { (password, score) -> assertEquals(score, scorePassword(password), password) }
    }

    @Test
    fun emptyScoresZero() {
        assertEquals(0, scorePassword(""))
    }

    @Test
    fun otherCharactersAddSevenOnce() {
        // 소문자 26 + 그 외 7 = 33. 8 × log₂33 = 40.35
        assertEquals(40, scorePassword("ab~~€€한한"))
    }

    @Test
    fun lengthCountsCodePoints() {
        // 이모지 4개: UTF-16으로 8단위지만 코드포인트는 4. 4 × log₂7 = 11.2
        assertEquals(11, scorePassword("🔑🔑🔑🔑"))
    }

    @Test
    fun sequentialDigitsDoNotChangeScore() {
        assertEquals(19, scorePassword("123456"))
    }

    @Test
    fun below60IsWeak() {
        assertTrue(isWeak(59))
        assertFalse(isWeak(60))
    }

    @Test
    fun detectsSequentialDigits() {
        listOf("123", "a123b", "456", "9876", "3210", "xx0123", "pw789!", "1210", "2123", "😀123")
            .forEach { assertTrue(hasSequentialDigits(it), it) }
    }

    @Test
    fun ignoresNonSequentialOrNonAsciiDigits() {
        listOf("", "12", "890", "901", "1a2b3", "135", "1122", "121", "1212", "abc", "１２３", "١٢٣", "1😀2😀3")
            .forEach { assertFalse(hasSequentialDigits(it), it) }
    }
}
