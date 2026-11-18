package com.keymanage.android.domain.generator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasswordGeneratorTest {
    @Test
    fun `길이 8~64를 벗어나면 길이 문제를 먼저 알린다`() {
        assertEquals(GeneratorOptionsProblem.LENGTH, PasswordGenerator.check(GeneratorOptions(length = 7)))
        assertEquals(GeneratorOptionsProblem.LENGTH, PasswordGenerator.check(GeneratorOptions(length = 65, uppercase = false, lowercase = false, digits = false, symbols = false)))
        assertNull(PasswordGenerator.check(GeneratorOptions(length = 8)))
        assertNull(PasswordGenerator.check(GeneratorOptions(length = 64)))
    }

    @Test
    fun `문자군을 하나도 고르지 않으면 생성을 막는다`() {
        val options = GeneratorOptions(uppercase = false, lowercase = false, digits = false, symbols = false)
        val error = assertFailsWith<InvalidGeneratorOptions> { PasswordGenerator().generate(options) }
        assertEquals(GeneratorOptionsProblem.NO_CHARSET, error.problem)
    }

    @Test
    fun `기본 옵션은 16자이고 고른 문자군이 모두 들어간다`() {
        repeat(200) {
            val password = PasswordGenerator().generate(GeneratorOptions())
            assertEquals(16, password.length)
            for (set in listOf(PasswordGenerator.UPPERCASE, PasswordGenerator.LOWERCASE, PasswordGenerator.DIGITS, PasswordGenerator.SYMBOLS)) {
                assertTrue(password.any { it in set }, password)
            }
        }
    }

    @Test
    fun `고르지 않은 문자군은 들어가지 않는다`() {
        val password = PasswordGenerator().generate(GeneratorOptions(length = 64, uppercase = false, symbols = false))
        assertTrue(password.all { it in PasswordGenerator.LOWERCASE || it in PasswordGenerator.DIGITS })
    }

    @Test
    fun `빠진 문자군이 있으면 통째로 다시 뽑는다`() {
        // Given: 첫 8글자는 모두 풀의 0번(대문자 A), 다음부터는 차례로 돈다
        var calls = 0
        val random = RandomSource { bound -> if (calls++ < 8) 0 else (calls * 7) % bound }

        // When
        val password = PasswordGenerator(random).generate(GeneratorOptions(length = 8, symbols = false, digits = false))

        // Then
        assertTrue(calls > 8)
        assertTrue(password.any { it.isLowerCase() } && password.any { it.isUpperCase() })
    }

    @Test
    fun `점수는 실제 문자군 풀로 계산하고 100에서 자른다`() {
        assertEquals(0, PasswordStrength.score(""))
        // 소문자 26 → 8 × log2(26) = 37.6
        assertEquals(37, PasswordStrength.score("abcdefgh"))
        // 대·소·숫자·특수 88 → 16 × log2(88) = 103.3 → 100
        assertEquals(100, PasswordStrength.score("Aa1!Aa1!Aa1!Aa1!"))
        // 그 외 문자는 한 번만 7을 더한다: 소문자 26 + 7 = 33 → 4 × log2(33) = 20.2
        assertEquals(20, PasswordStrength.score("ab가나"))
        // 이모지는 한 글자로 센다: 7 → 2 × log2(7) = 5.6
        assertEquals(5, PasswordStrength.score("🔑🔒"))
    }

    @Test
    fun `60점 미만이면 취약`() {
        assertTrue(PasswordStrength.isWeak(59))
        assertFalse(PasswordStrength.isWeak(60))
    }

    @Test
    fun `이어지는 숫자는 3자리 이상 한 방향일 때만 안내한다`() {
        for (yes in listOf("a123", "987x", "x3210", "45678")) assertTrue(PasswordStrength.hasSequentialDigits(yes), yes)
        for (no in listOf("12", "121", "890", "901", "1a23", "135", "abc")) assertFalse(PasswordStrength.hasSequentialDigits(no), no)
    }
}
