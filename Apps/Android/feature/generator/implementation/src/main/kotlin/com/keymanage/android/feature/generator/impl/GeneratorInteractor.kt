package com.keymanage.android.feature.generator.impl

import com.keymanage.android.domain.clipboard.ClipboardBurner
import com.keymanage.android.domain.generator.GeneratorOptions
import com.keymanage.android.domain.generator.GeneratorOptionsProblem
import com.keymanage.android.domain.generator.PasswordGenerator
import com.keymanage.android.domain.generator.PasswordStrength
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.BasicComposeRouter
import com.uber.rib.core.BasicInteractor
import com.uber.rib.core.Bundle
import com.uber.rib.core.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** 생성기 화면 상태. 옵션이 잘못되면 [password]는 비고 [problemMessage]를 보인다. */
data class GeneratorViewState(
    val options: GeneratorOptions = GeneratorOptions(),
    val password: String = "",
    val problemMessage: String? = null,
    val copyFailed: Boolean = false,
    val burnSeconds: Int? = null,
) {
    val score: Int get() = PasswordStrength.score(password)
    val isWeak: Boolean get() = password.isNotEmpty() && PasswordStrength.isWeak(score)
    val hasSequentialDigits: Boolean get() = PasswordStrength.hasSequentialDigits(password)

    override fun toString() = "GeneratorViewState(options=$options, problemMessage=$problemMessage)"
}

/** 생성기 문자군. */
enum class Charset { UPPERCASE, LOWERCASE, DIGITS, SYMBOLS }

sealed interface GeneratorViewAction {
    data class LengthChanged(val length: Int) : GeneratorViewAction
    data class Toggle(val charset: Charset, val enabled: Boolean) : GeneratorViewAction
    data object Regenerate : GeneratorViewAction
    data object Copy : GeneratorViewAction
}

/** GEN-001 사용자 문구. */
object GeneratorMessages {
    const val LENGTH = "비밀번호 길이는 최소 8자 이상 64자 이하이어야 합니다."
    const val NO_CHARSET = "최소 1개 이상의 문자 종류를 선택해야 합니다."
    const val WEAK = "현재 비밀번호는 보안에 취약합니다. 80점 이상의 강력한 비밀번호로 재생성하세요."
    const val SEQUENTIAL = "123처럼 이어지는 숫자가 들어 있어 추측하기 쉽습니다."
    const val COPY_FAILED = "클립보드에 복사하지 못했습니다."
}

/** 옵션이 바뀌면 바로 다시 만들고, 복사하면 30초 뒤 클립보드를 비운다(GEN-001, COM-001). */
class GeneratorInteractor(
    presenter: GeneratorPresenter,
    private val store: ViewStateStore<GeneratorViewState>,
    private val actions: ViewActionStream<GeneratorViewAction>,
    private val generator: PasswordGenerator,
    private val clipboard: ClipboardBurner,
) : BasicInteractor<GeneratorPresenter, BasicComposeRouter<GeneratorInteractor>>(presenter) {
    private var ticker: Job? = null

    init {
        regenerate(store.current.options)
    }

    override fun didBecomeActive(savedInstanceState: Bundle?) {
        super.didBecomeActive(savedInstanceState)
        actions.actions.onEach(::handle).launchIn(coroutineScope)
    }

    fun handle(action: GeneratorViewAction) {
        val options = store.current.options
        when (action) {
            is GeneratorViewAction.LengthChanged -> regenerate(options.copy(length = action.length))
            is GeneratorViewAction.Toggle -> regenerate(
                when (action.charset) {
                    Charset.UPPERCASE -> options.copy(uppercase = action.enabled)
                    Charset.LOWERCASE -> options.copy(lowercase = action.enabled)
                    Charset.DIGITS -> options.copy(digits = action.enabled)
                    Charset.SYMBOLS -> options.copy(symbols = action.enabled)
                },
            )
            GeneratorViewAction.Regenerate -> regenerate(options)
            GeneratorViewAction.Copy -> copy()
        }
    }

    private fun regenerate(options: GeneratorOptions) {
        val problem = PasswordGenerator.check(options)
        val message = when (problem) {
            GeneratorOptionsProblem.LENGTH -> GeneratorMessages.LENGTH
            GeneratorOptionsProblem.NO_CHARSET -> GeneratorMessages.NO_CHARSET
            null -> null
        }
        val password = if (problem == null) generator.generate(options) else ""
        store.update { it.copy(options = options, password = password, problemMessage = message, copyFailed = false) }
    }

    private fun copy() {
        val password = store.current.password
        if (password.isEmpty()) return
        try {
            clipboard.copy(password)
        } catch (error: Exception) {
            store.update { it.copy(copyFailed = true) }
            return
        }
        store.update { it.copy(burnSeconds = clipboard.remainingSeconds(), copyFailed = false) }
        if (!isAttached()) return
        ticker?.cancel()
        ticker = coroutineScope.launch {
            while (true) {
                delay(TICK_MILLIS)
                val seconds = clipboard.remainingSeconds()
                store.update { it.copy(burnSeconds = seconds) }
                if (seconds == null) break
            }
        }
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
    }
}
