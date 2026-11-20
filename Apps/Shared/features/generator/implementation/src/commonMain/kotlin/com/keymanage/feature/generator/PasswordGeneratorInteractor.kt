package com.keymanage.feature.generator

import com.carti.ribs.PresentableInteractor
import com.carti.ribs.checkMainThread
import com.keymanage.domain.generator.DEFAULT_GENERATOR_OPTIONS
import com.keymanage.domain.generator.GeneratorOptions
import com.keymanage.domain.generator.GeneratorOptionsProblem
import com.keymanage.domain.generator.RandomSource
import com.keymanage.domain.generator.checkGeneratorOptions
import com.keymanage.domain.generator.generatePassword
import com.keymanage.domain.generator.hasSequentialDigits
import com.keymanage.domain.generator.scorePassword
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.ClipboardPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// 비밀번호 생성기 (GEN-001). 만든 비밀번호는 사용·복사를 누르기 전까지 어디에도 내보내지 않는다.
// 원본: Apps/Web/src/ui/vault/PasswordGenerator.tsx

class PasswordGeneratorInteractor(
    presenter: PasswordGeneratorPresentable,
    private val clipboard: ClipboardPort,
    private val random: RandomSource,
    private val listener: PasswordGeneratorListener?,
    private val allowCopy: Boolean,
) : PresentableInteractor<PasswordGeneratorPresentable>(presenter), PasswordGeneratorPresentableListener {
    private var lengthText = DEFAULT_GENERATOR_OPTIONS.length.toString()
    private var charsets = DEFAULT_GENERATOR_OPTIONS
    private var password = ""
    // 길이 경고는 입력을 마친 뒤에만 보인다. 16을 20으로 고치는 중에 깜빡이지 않게 한다
    private var lengthChecked = false
    private var announcement = ""
    private var copyError: String? = null

    override fun didBecomeActive() {
        presenter.listener = this
        // 열면 기본 옵션으로 하나 만들어 보여 준다
        password = generatePassword(DEFAULT_GENERATOR_OPTIONS, random)
        render()
    }

    override fun willResignActive() {
        presenter.listener = null
        // 내려가기 전에 만든 비밀번호를 비우고 그려, 뷰가 마지막으로 받은 상태에 남지 않게 한다
        password = ""
        render(showsPassword = false)
    }

    override fun handle(action: PasswordGeneratorViewAction) {
        // 내려간 뒤 늦게 들어온 입력(뷰가 쥐고 있던 listener)은 무시한다
        if (!isActive) return
        when (action) {
            is PasswordGeneratorViewAction.ChangeLength -> {
                lengthText = action.text
                regenerate()
            }
            is PasswordGeneratorViewAction.SetCharset -> {
                charsets = charsets.withCharset(action.charset, action.enabled)
                regenerate()
            }
            PasswordGeneratorViewAction.FinishEditingLength -> {
                lengthChecked = true
                render()
            }
            PasswordGeneratorViewAction.Regenerate -> {
                lengthChecked = true
                regenerate()
            }
            PasswordGeneratorViewAction.Use -> {
                if (listener != null && currentProblem() == null) listener.didUsePassword(password)
            }
            PasswordGeneratorViewAction.Close -> listener?.didClosePasswordGenerator()
            PasswordGeneratorViewAction.Copy -> copy()
        }
    }

    private fun currentOptions(): GeneratorOptions = charsets.copy(length = parseLength(lengthText))

    private fun currentProblem(): GeneratorOptionsProblem? = checkGeneratorOptions(currentOptions())

    private fun regenerate() {
        if (currentProblem() == null) {
            lengthChecked = false
            password = generatePassword(currentOptions(), random)
            announcement = PasswordGeneratorMessages.REGENERATED_ANNOUNCEMENT
        }
        render()
    }

    private fun copy() {
        if (!allowCopy || currentProblem() != null) return
        val text = password
        copyError = null
        render()
        coroutineScope.launch {
            try {
                clipboard.copy(text)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // 탭이 활성 상태가 아니거나 권한이 없으면 플랫폼이 거절한다
                copyError = PasswordGeneratorMessages.COPY_FAILED
                render()
            }
        }
    }

    private fun render(showsPassword: Boolean = true) {
        checkMainThread("PasswordGeneratorPresentable.render")
        val problem = currentProblem()
        val shownProblem = when {
            problem == GeneratorOptionsProblem.NO_CHARSET -> PasswordGeneratorMessages.NO_CHARSET_PROBLEM
            problem == GeneratorOptionsProblem.LENGTH && lengthChecked -> PasswordGeneratorMessages.LENGTH_PROBLEM
            else -> null
        }
        presenter.render(
            PasswordGeneratorViewState(
                lengthText = lengthText,
                uppercase = charsets.uppercase,
                lowercase = charsets.lowercase,
                digits = charsets.digits,
                symbols = charsets.symbols,
                generated = if (problem == null && showsPassword) describe(password) else null,
                problemMessage = shownProblem,
                announcement = announcement,
                copyError = copyError,
                showsUse = listener != null,
                showsClose = listener != null,
                showsCopy = allowCopy,
            ),
        )
    }
}

private fun describe(password: String) =
    GeneratedPassword(text = password, score = scorePassword(password), hasSequentialDigits = hasSequentialDigits(password))

private fun GeneratorOptions.withCharset(charset: GeneratorCharset, enabled: Boolean): GeneratorOptions = when (charset) {
    GeneratorCharset.UPPERCASE -> copy(uppercase = enabled)
    GeneratorCharset.LOWERCASE -> copy(lowercase = enabled)
    GeneratorCharset.DIGITS -> copy(digits = enabled)
    GeneratorCharset.SYMBOLS -> copy(symbols = enabled)
}

// 숫자만 받는다. 칸에 보이는 값과 다른 길이로 만들지 않게 한다. 숫자가 아니거나 너무 크면 길이 문제로 걸리는 0을 돌려준다
private fun parseLength(text: String): Int {
    val digits = text.trim()
    if (digits.isEmpty() || digits.any { it !in '0'..'9' }) return 0
    return digits.toIntOrNull() ?: 0
}

// 보안 난수 4바이트를 0 이상 2^32 미만의 정수로 읽는다
internal fun randomSourceOf(bytes: RandomBytes): RandomSource =
    RandomSource { bytes.nextBytes(4).fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 0xFF) } }
