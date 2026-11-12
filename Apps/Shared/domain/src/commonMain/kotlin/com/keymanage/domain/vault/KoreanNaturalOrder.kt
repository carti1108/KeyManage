package com.keymanage.domain.vault

/**
 * 항목 목록 정렬 순서. 원본은 `Intl.Collator('ko', { sensitivity: 'base', numeric: true })`를 썼다.
 * Kotlin 공통 코드에는 같은 기능이 없고 플랫폼 정렬기(ICU, java.text, Foundation)는 결과가 서로 달라서,
 * 원본 화면이 보장하던 규칙만 직접 구현해 세 플랫폼이 같은 순서를 보이게 한다.
 *
 * 1. 숫자 묶음은 값으로 비교한다("2" < "10").
 * 2. 문자 종류 순서: 숫자 → 한글 → 라틴 문자 → 그 밖의 문자.
 * 3. 한글은 음절 코드 순(사전순과 같다), 라틴 문자는 대소문자를 무시한다.
 *
 * 원본과 다를 수 있는 곳: 악센트(é와 e를 같게 보는 것), 구두점·공백의 세부 순서. 볼트 항목 이름 정렬에는 영향이 작다.
 */
object KoreanNaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isAsciiDigit() && cb.isAsciiDigit()) {
                val endA = a.digitRunEnd(i)
                val endB = b.digitRunEnd(j)
                val byValue = compareDigitRuns(a.substring(i, endA), b.substring(j, endB))
                if (byValue != 0) return byValue
                i = endA
                j = endB
                continue
            }
            val byRank = rank(ca).compareTo(rank(cb))
            if (byRank != 0) return byRank
            val byChar = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (byChar != 0) return byChar
            i++
            j++
        }
        return (a.length - i).compareTo(b.length - j)
    }

    private fun rank(c: Char): Int = when {
        c.isAsciiDigit() -> 0
        c in '가'..'힣' || c in 'ㄱ'..'ㆎ' -> 1
        c.lowercaseChar() in 'a'..'z' -> 2
        else -> 3
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'

    private fun String.digitRunEnd(start: Int): Int {
        var end = start
        while (end < length && this[end].isAsciiDigit()) end++
        return end
    }

    // 앞자리 0을 빼고 길이 → 사전순으로 비교한다. 아주 긴 숫자도 넘치지 않는다
    private fun compareDigitRuns(a: String, b: String): Int {
        val x = a.trimStart('0')
        val y = b.trimStart('0')
        return if (x.length != y.length) x.length.compareTo(y.length) else x.compareTo(y)
    }
}
