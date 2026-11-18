package com.keymanage.android.domain.generator

import java.security.SecureRandom
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min

/** 생성 옵션(GEN-001 값 표). */
data class GeneratorOptions(
    val length: Int = DEFAULT_LENGTH,
    val uppercase: Boolean = true,
    val lowercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
) {
    companion object {
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 64
        const val DEFAULT_LENGTH = 16
    }
}

/** 생성을 막는 옵션(규칙 4). 길이 문제를 먼저 알린다. */
enum class GeneratorOptionsProblem { LENGTH, NO_CHARSET }

class InvalidGeneratorOptions(val problem: GeneratorOptionsProblem) : Exception("생성 옵션 오류: $problem")

/** 0 이상 [bound] 미만의 균등한 정수. */
fun interface RandomSource {
    fun nextInt(bound: Int): Int
}

/**
 * 무작위 비밀번호 생성(GEN-001, ADR-0005). 웹 `generatePassword`와 같은 규칙이다.
 * 선택한 문자군 전체 풀에서 균등하게 뽑고, 빠진 문자군이 있으면 통째로 다시 뽑는다(규칙 1·2).
 * 문자군마다 한 글자씩 강제로 넣으면 위치가 예측되기 때문이다.
 */
class PasswordGenerator(private val random: RandomSource = secureRandom()) {
    fun generate(options: GeneratorOptions): String {
        check(options)?.let { throw InvalidGeneratorOptions(it) }
        val charsets = charsetsOf(options)
        val pool = charsets.joinToString("")
        while (true) {
            val password = buildString { repeat(options.length) { append(pool[random.nextInt(pool.length)]) } }
            if (charsets.all { set -> password.any { it in set } }) return password
        }
    }

    companion object {
        const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        const val DIGITS = "0123456789"
        const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"

        fun check(options: GeneratorOptions): GeneratorOptionsProblem? = when {
            options.length !in GeneratorOptions.MIN_LENGTH..GeneratorOptions.MAX_LENGTH -> GeneratorOptionsProblem.LENGTH
            charsetsOf(options).isEmpty() -> GeneratorOptionsProblem.NO_CHARSET
            else -> null
        }

        private fun charsetsOf(options: GeneratorOptions) = listOfNotNull(
            UPPERCASE.takeIf { options.uppercase },
            LOWERCASE.takeIf { options.lowercase },
            DIGITS.takeIf { options.digits },
            SYMBOLS.takeIf { options.symbols },
        )

        /** SecureRandom.nextInt(bound)는 거부 샘플링으로 균등 분포를 보장한다. */
        private fun secureRandom(): RandomSource {
            val random = SecureRandom()
            return RandomSource { random.nextInt(it) }
        }
    }
}

/** 비밀번호 강도 감사(GEN-001 규칙 3·5·7). 웹 `passwordStrength`와 같은 규칙이다. */
object PasswordStrength {
    const val MAX_SCORE = 100

    /** 이 점수 미만이면 취약. 경고만 하고 사용은 막지 않는다. */
    const val WEAK_BELOW = 60
    private const val OTHER_POOL_SIZE = 7
    private const val SEQUENCE_MIN_LENGTH = 3
    private val KNOWN_SETS = listOf(
        PasswordGenerator.UPPERCASE, PasswordGenerator.LOWERCASE, PasswordGenerator.DIGITS, PasswordGenerator.SYMBOLS,
    )

    /** min(100, floor(길이 × log₂(풀 크기))). 길이는 코드포인트 수, 빈 문자열은 0점. */
    fun score(password: String): Int {
        val chars = password.codePoints().toArray().map { String(Character.toChars(it)) }
        if (chars.isEmpty()) return 0
        var pool = KNOWN_SETS.filter { set -> chars.any { it.length == 1 && it[0] in set } }.sumOf { it.length }
        if (chars.any { char -> KNOWN_SETS.none { set -> char.length == 1 && char[0] in set } }) pool += OTHER_POOL_SIZE
        return min(MAX_SCORE, floor(chars.size * ln(pool.toDouble()) / ln(2.0)).toInt())
    }

    fun isWeak(score: Int) = score < WEAK_BELOW

    /** 3자리 이상 1씩 오르거나 내리는 ASCII 숫자가 있는지. 9→0·0→9와 다른 문자가 끼면 끊긴 것으로 본다. */
    fun hasSequentialDigits(password: String): Boolean {
        var run = 1
        var step = 0
        for (i in 1 until password.length) {
            val a = password[i - 1]
            val b = password[i]
            val diff = if (a in PasswordGenerator.DIGITS && b in PasswordGenerator.DIGITS) b - a else 0
            if (diff == 1 || diff == -1) {
                run = if (diff == step) run + 1 else 2
                step = diff
                if (run >= SEQUENCE_MIN_LENGTH) return true
            } else {
                run = 1
                step = 0
            }
        }
        return false
    }
}
