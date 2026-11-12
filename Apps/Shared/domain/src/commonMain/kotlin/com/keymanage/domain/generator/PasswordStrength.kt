package com.keymanage.domain.generator

import kotlin.math.floor
import kotlin.math.log2

// 비밀번호 강도 점수 (GEN-001). 실제로 들어 있는 문자군으로 풀 크기를 정한다
// 원본: Apps/Web/src/domain/generator/passwordStrength.ts

private const val MAX_SCORE = 100

/** 이 점수 미만이면 취약. 경고만 하고 사용은 막지 않는다 */
const val WEAK_BELOW: Int = 60
private const val OTHER_POOL_SIZE = 7
private const val SEQUENCE_MIN_LENGTH = 3

private val KNOWN_SETS = listOf(UPPERCASE, LOWERCASE, DIGITS, SYMBOLS)

/** min(100, floor(길이 × log₂(풀 크기))). 길이는 코드포인트 수, 빈 문자열은 0점 */
fun scorePassword(password: String): Int {
    val chars = codePoints(password)
    if (chars.isEmpty()) return 0
    var pool = KNOWN_SETS.filter { set -> chars.any { it in set } }.sumOf { it.length }
    // 네 문자군에 없는 문자(한글, 이모지, ~ 등)는 몇 종류든 7을 한 번만 더한다
    if (chars.any { char -> KNOWN_SETS.none { char in it } }) pool += OTHER_POOL_SIZE
    return minOf(MAX_SCORE, floor(chars.size * log2(pool.toDouble())).toInt())
}

fun isWeak(score: Int): Boolean = score < WEAK_BELOW

/**
 * 3자리 이상 1씩 오르거나 내리는 숫자(123, 987)가 있는지. 점수와 별개로 안내만 한다 (GEN-001).
 * 9→0, 0→9로 넘어가는 경우는 보지 않고, 사이에 다른 문자가 끼면 끊긴 것으로 본다. ASCII 숫자만 숫자로 본다
 */
fun hasSequentialDigits(password: String): Boolean {
    val chars = codePoints(password)
    var run = 1
    var step = 0
    for (i in 1 until chars.size) {
        val previous = asciiDigit(chars[i - 1])
        val current = asciiDigit(chars[i])
        val diff = if (previous != null && current != null) current - previous else 0
        if (diff == 1 || diff == -1) {
            // 방향이 바뀌면(121) 직전 두 글자부터 새로 센다
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

private fun asciiDigit(char: String): Int? = char.singleOrNull()?.takeIf { it in '0'..'9' }?.let { it - '0' }

// 원본 [...password]와 같이 서로게이트 쌍을 한 글자로 나눈다
private fun codePoints(text: String): List<String> {
    val result = ArrayList<String>(text.length)
    var i = 0
    while (i < text.length) {
        val end = if (text[i].isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) i + 2 else i + 1
        result += text.substring(i, end)
        i = end
    }
    return result
}
