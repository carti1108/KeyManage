package com.keymanage.android.designsystem.component

/** 비밀번호 강도 단계(design-system.md 6.5). 점수는 0~100으로 자른 뒤 나눈다. */
enum class EntropyLevel(val label: String) {
    WEAK("취약함"),
    MEDIUM("보통"),
    STRONG("초강력"),
    ;

    companion object {
        const val MIN_SCORE = 0
        const val MAX_SCORE = 100
        private const val MEDIUM_FROM = 60
        private const val STRONG_FROM = 80

        fun clamp(score: Int): Int = score.coerceIn(MIN_SCORE, MAX_SCORE)

        fun of(score: Int): EntropyLevel = when (clamp(score)) {
            in STRONG_FROM..MAX_SCORE -> STRONG
            in MEDIUM_FROM until STRONG_FROM -> MEDIUM
            else -> WEAK
        }
    }
}
