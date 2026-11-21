package com.keymanage.domain.generator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// 원본: Apps/Web/src/domain/generator/passwordGenerator.test.ts (GEN-001)
// 길이 12.5·NaN 사례는 Int 타입이 막으므로 옮기지 않았다

/** 준비한 난수를 다 쓰면 무한 루프 대신 바로 실패한다 */
private class Sequence(vararg values: Long) : RandomSource {
    val remaining = ArrayDeque(values.toList())
    override fun nextUInt32(): Long = remaining.removeFirstOrNull() ?: error("준비한 난수를 다 씀")
}

private val ALL = GeneratorOptions(length = 16, uppercase = true, lowercase = true, digits = true, symbols = true)

private fun hasUpper(s: String) = s.any { it in 'A'..'Z' }
private fun hasLower(s: String) = s.any { it in 'a'..'z' }
private fun hasDigit(s: String) = s.any { it in '0'..'9' }
private fun hasSymbol(s: String) = s.any { it in SYMBOLS }

class PasswordGeneratorTest {
    @Test
    fun defaultIs16CharsWithAllCharsets() {
        assertEquals(ALL, DEFAULT_GENERATOR_OPTIONS)
        assertEquals(16, generatePassword(DEFAULT_GENERATOR_OPTIONS).length)
    }

    @Test
    fun symbolsAreThe26DefinedByPolicy() {
        assertEquals("!@#$%^&*()_+-=[]{}|;:,.<>?", SYMBOLS)
        assertEquals(26, SYMBOLS.length)
    }

    @Test
    fun everyGeneratedPasswordHasAllChosenCharsetsAndNothingElse() {
        repeat(1000) {
            val password = generatePassword(ALL.copy(length = 8))
            assertEquals(8, password.length)
            assertTrue(hasUpper(password) && hasLower(password) && hasDigit(password) && hasSymbol(password), password)
        }
        repeat(200) {
            val password = generatePassword(GeneratorOptions(length = 12, uppercase = false, lowercase = true, digits = true, symbols = false))
            assertTrue(Regex("^[a-z0-9]{12}$").matches(password), password)
        }
    }

    @Test
    fun boundaryLengthsAreAllowed() {
        listOf(8, 64).forEach { assertEquals(it, generatePassword(ALL.copy(length = it)).length) }
    }

    @Test
    fun outOfRangeLengthsAreRejected() {
        listOf(7, 65, 0, -1).forEach { length ->
            val error = assertFailsWith<InvalidGeneratorOptionsException>("$length") { generatePassword(ALL.copy(length = length)) }
            assertEquals(GeneratorOptionsProblem.LENGTH, error.reason)
        }
    }

    @Test
    fun noCharsetIsRejected() {
        val none = GeneratorOptions(length = 16, uppercase = false, lowercase = false, digits = false, symbols = false)

        val error = assertFailsWith<InvalidGeneratorOptionsException> { generatePassword(none) }
        assertEquals(GeneratorOptionsProblem.NO_CHARSET, error.reason)
    }

    @Test
    fun lengthProblemIsReportedBeforeCharsetProblem() {
        val both = GeneratorOptions(length = 3, uppercase = false, lowercase = false, digits = false, symbols = false)
        assertEquals(GeneratorOptionsProblem.LENGTH, checkGeneratorOptions(both))
    }

    @Test
    fun missingCharsetDiscardsTheWholeAttempt() {
        // 풀은 대문자·소문자·숫자·특수기호 순서다. 소문자+숫자면 0~25는 소문자, 26~35는 숫자
        // 첫 시도는 모두 소문자라 버리고, 둘째 시도에서 숫자가 섞인다
        val random = Sequence(0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 26)

        val password = generatePassword(GeneratorOptions(length = 8, uppercase = false, lowercase = true, digits = true, symbols = false), random)

        assertEquals("abcdefg0", password)
        assertTrue(random.remaining.isEmpty())
    }

    @Test
    fun discardsLargeValuesThatWouldBiasModulo() {
        // 2^32 = 4294967296. 풀 36이면 4294967292 이상은 편향 구간이라 버린다
        val random = Sequence(4294967295, 26, 0, 1, 2, 3, 4, 5, 6)

        assertEquals("0abcdefg", generatePassword(GeneratorOptions(length = 8, uppercase = false, lowercase = true, digits = true, symbols = false), random))
    }

    @Test
    fun secureRandomSourceStaysInUnsigned32BitRange() {
        repeat(1000) {
            val value = SecureRandomSource.nextUInt32()
            assertTrue(value in 0L..0xFFFF_FFFFL, "$value")
        }
    }
}
