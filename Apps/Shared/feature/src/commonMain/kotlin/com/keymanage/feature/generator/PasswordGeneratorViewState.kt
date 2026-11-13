package com.keymanage.feature.generator

import com.keymanage.domain.generator.DEFAULT_GENERATOR_OPTIONS
import com.keymanage.domain.generator.hasSequentialDigits

enum class GeneratorCharset { UPPERCASE, LOWERCASE, DIGITS, SYMBOLS }

/** 옵션이 맞아서 만들어 둔 비밀번호 */
data class GeneratedPassword(
    val text: String,
    /** 강도 점수 (0~100). 취약 여부는 domain의 isWeak로 본다 */
    val score: Int,
    /** 이어지는 숫자 안내를 보일지. 점수와 별개다 (GEN-001) */
    val hasSequentialDigits: Boolean,
)

data class PasswordGeneratorViewState(
    /** 입력 중에 비울 수 있도록 길이는 글자로 든다 */
    val lengthText: String = DEFAULT_GENERATOR_OPTIONS.length.toString(),
    val uppercase: Boolean = DEFAULT_GENERATOR_OPTIONS.uppercase,
    val lowercase: Boolean = DEFAULT_GENERATOR_OPTIONS.lowercase,
    val digits: Boolean = DEFAULT_GENERATOR_OPTIONS.digits,
    val symbols: Boolean = DEFAULT_GENERATOR_OPTIONS.symbols,
    /** 옵션이 잘못된 동안은 null. 옵션과 맞지 않는 이전 결과를 보이지 않는다 */
    val generated: GeneratedPassword? = null,
    /** 보일 경고. 길이 경고는 입력을 마친 뒤(칸을 벗어나거나 다시 생성)에만 나온다 */
    val problemMessage: String? = null,
    /** 새로 만들 때마다 갱신. 보조 기술이 읽는다. 처음 열 때는 비어 있다 */
    val announcement: String = "",
    val copyError: String? = null,
    /** 사용·닫기·복사 버튼을 둘지. 항목 폼 안에서는 사용·닫기, 생성기 탭에서는 복사 */
    val showsUse: Boolean = false,
    val showsClose: Boolean = false,
    val showsCopy: Boolean = false,
) {
    /** 사용·복사를 누를 수 있는지. 옵션이 잘못되면 막는다 */
    val canUseGenerated: Boolean get() = generated != null
}

sealed interface PasswordGeneratorViewAction {
    /** 길이 칸에 입력한 글자. 입력할 때마다 새로 만든다 */
    data class ChangeLength(val text: String) : PasswordGeneratorViewAction
    data class SetCharset(val charset: GeneratorCharset, val enabled: Boolean) : PasswordGeneratorViewAction
    /** 길이 칸을 벗어남. 이때부터 길이 경고를 보인다 */
    data object FinishEditingLength : PasswordGeneratorViewAction
    data object Regenerate : PasswordGeneratorViewAction
    data object Use : PasswordGeneratorViewAction
    data object Close : PasswordGeneratorViewAction
    data object Copy : PasswordGeneratorViewAction
}
