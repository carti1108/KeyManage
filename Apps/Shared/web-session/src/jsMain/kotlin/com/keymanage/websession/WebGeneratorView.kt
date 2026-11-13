@file:OptIn(ExperimentalJsExport::class)

package com.keymanage.websession

import com.carti.ribs.web.ViewStore
import com.carti.ribs.web.WebViewControllable
import com.keymanage.domain.generator.isWeak
import com.keymanage.feature.generator.GeneratorCharset
import com.keymanage.feature.generator.PasswordGeneratorPresentable
import com.keymanage.feature.generator.PasswordGeneratorPresentableListener
import com.keymanage.feature.generator.PasswordGeneratorViewAction
import com.keymanage.feature.generator.PasswordGeneratorViewState

// 비밀번호 생성기 화면의 웹 뷰. 생성기 탭과 항목 폼 안의 생성기가 같은 뷰를 쓴다

/** 생성기 화면이 그리는 전부. 옵션이 잘못된 동안은 [generatedText]가 null이다 */
@JsExport
data class WebGeneratorState(
    /** 입력 중에 비울 수 있도록 길이는 글자로 든다 */
    val lengthText: String,
    val uppercase: Boolean,
    val lowercase: Boolean,
    val digits: Boolean,
    val symbols: Boolean,
    val generatedText: String?,
    /** 0~100. 만든 비밀번호가 없으면 0 */
    val generatedScore: Int,
    val generatedIsWeak: Boolean,
    /** 이어지는 숫자 안내를 보일지. 점수와 별개다 */
    val generatedHasSequentialDigits: Boolean,
    /** 보일 경고(길이·문자 종류). 없으면 null */
    val problemMessage: String?,
    /** 새로 만들 때마다 갱신. 보조 기술이 읽는다. 처음 열 때는 빈 문자열 */
    val announcement: String,
    val copyError: String?,
    val showsUse: Boolean,
    val showsClose: Boolean,
    val showsCopy: Boolean,
    /** 사용·복사를 누를 수 있는지 */
    val canUseGenerated: Boolean,
)

private fun PasswordGeneratorViewState.toWeb() = WebGeneratorState(
    lengthText = lengthText,
    uppercase = uppercase,
    lowercase = lowercase,
    digits = digits,
    symbols = symbols,
    generatedText = generated?.text,
    generatedScore = generated?.score ?: 0,
    generatedIsWeak = generated?.let { isWeak(it.score) } ?: false,
    generatedHasSequentialDigits = generated?.hasSequentialDigits ?: false,
    problemMessage = problemMessage,
    announcement = announcement,
    copyError = copyError,
    showsUse = showsUse,
    showsClose = showsClose,
    showsCopy = showsCopy,
    canUseGenerated = canUseGenerated,
)

private fun charsetOf(wire: String): GeneratorCharset = when (wire) {
    "uppercase" -> GeneratorCharset.UPPERCASE
    "lowercase" -> GeneratorCharset.LOWERCASE
    "digits" -> GeneratorCharset.DIGITS
    "symbols" -> GeneratorCharset.SYMBOLS
    else -> throw IllegalArgumentException("알 수 없는 문자 종류: $wire")
}

@JsExport
class WebGeneratorView : PasswordGeneratorPresentable, WebViewControllable {
    override val screenKey: String = "passwordGenerator"
    val store: ViewStore<WebGeneratorState> = ViewStore(PasswordGeneratorViewState().toWeb())

    @JsExport.Ignore
    override var listener: PasswordGeneratorPresentableListener? = null

    /** 길이 칸에 입력한 글자. 입력할 때마다 새로 만든다 */
    fun changeLength(text: String) = send(PasswordGeneratorViewAction.ChangeLength(text))

    /** @param charset 'uppercase' | 'lowercase' | 'digits' | 'symbols' */
    fun setCharset(charset: String, enabled: Boolean) = send(PasswordGeneratorViewAction.SetCharset(charsetOf(charset), enabled))

    /** 길이 칸을 벗어남. 이때부터 길이 경고를 보인다 */
    fun finishEditingLength() = send(PasswordGeneratorViewAction.FinishEditingLength)
    fun regenerate() = send(PasswordGeneratorViewAction.Regenerate)
    fun use() = send(PasswordGeneratorViewAction.Use)
    fun close() = send(PasswordGeneratorViewAction.Close)
    fun copy() = send(PasswordGeneratorViewAction.Copy)

    private fun send(action: PasswordGeneratorViewAction) {
        listener?.handle(action)
    }

    @JsExport.Ignore
    override fun render(state: PasswordGeneratorViewState) = store.set(state.toWeb())
}
