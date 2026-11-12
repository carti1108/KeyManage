package com.keymanage.domain.generator

import com.keymanage.domain.platform.SecureRandomBytes

// 무작위 비밀번호 생성 (GEN-001, ADR-0005). 서버로 보내거나 기록하지 않는다
// 원본: Apps/Web/src/domain/generator/passwordGenerator.ts

const val MIN_GENERATED_LENGTH: Int = 8
const val MAX_GENERATED_LENGTH: Int = 64

const val UPPERCASE: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
const val LOWERCASE: String = "abcdefghijklmnopqrstuvwxyz"
const val DIGITS: String = "0123456789"
const val SYMBOLS: String = "!@#$%^&*()_+-=[]{}|;:,.<>?"

data class GeneratorOptions(
    val length: Int,
    val uppercase: Boolean,
    val lowercase: Boolean,
    val digits: Boolean,
    val symbols: Boolean,
)

val DEFAULT_GENERATOR_OPTIONS: GeneratorOptions =
    GeneratorOptions(length = 16, uppercase = true, lowercase = true, digits = true, symbols = true)

/** LENGTH: 8~64가 아님, NO_CHARSET: 문자군을 하나도 고르지 않음 */
enum class GeneratorOptionsProblem { LENGTH, NO_CHARSET }

/** 생성을 막는 옵션 */
class InvalidGeneratorOptionsException(val reason: GeneratorOptionsProblem) : IllegalArgumentException(
    when (reason) {
        GeneratorOptionsProblem.LENGTH -> "길이는 8~64 정수여야 함"
        GeneratorOptionsProblem.NO_CHARSET -> "문자군을 하나 이상 골라야 함"
    },
)

/** 0 이상 2^32 미만의 균등한 정수를 돌려준다 */
fun interface RandomSource {
    fun nextUInt32(): Long
}

/** 플랫폼 보안 난수에서 4바이트를 읽는다 */
object SecureRandomSource : RandomSource {
    override fun nextUInt32(): Long =
        SecureRandomBytes.nextBytes(4).fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 0xFF) }
}

private const val UINT32_RANGE = 1L shl 32

// 0 이상 n 미만 균등 정수. 2^32가 n으로 나누어떨어지지 않으면 끝부분 값이 나머지 연산에서 더 자주 나오므로 버린다
private fun uniformIndex(n: Int, random: RandomSource): Int {
    val limit = UINT32_RANGE - (UINT32_RANGE % n)
    while (true) {
        val value = random.nextUInt32()
        if (value < limit) return (value % n).toInt()
    }
}

/** 생성을 막을 문제가 있으면 그 이유. 길이 문제를 먼저 알린다 */
fun checkGeneratorOptions(options: GeneratorOptions): GeneratorOptionsProblem? = when {
    options.length !in MIN_GENERATED_LENGTH..MAX_GENERATED_LENGTH -> GeneratorOptionsProblem.LENGTH
    !options.uppercase && !options.lowercase && !options.digits && !options.symbols -> GeneratorOptionsProblem.NO_CHARSET
    else -> null
}

/** @throws InvalidGeneratorOptionsException */
fun generatePassword(options: GeneratorOptions, random: RandomSource = SecureRandomSource): String {
    checkGeneratorOptions(options)?.let { throw InvalidGeneratorOptionsException(it) }
    val charsets = listOfNotNull(
        UPPERCASE.takeIf { options.uppercase },
        LOWERCASE.takeIf { options.lowercase },
        DIGITS.takeIf { options.digits },
        SYMBOLS.takeIf { options.symbols },
    )

    // 문자군마다 한 글자씩 강제로 넣으면 위치가 예측되므로, 전체 풀에서 균등하게 뽑고 빠진 문자군이 있으면 통째로 다시 뽑는다
    val pool = charsets.joinToString("")
    while (true) {
        val password = buildString { repeat(options.length) { append(pool[uniformIndex(pool.length, random)]) } }
        if (charsets.all { set -> password.any { it in set } }) return password
    }
}
