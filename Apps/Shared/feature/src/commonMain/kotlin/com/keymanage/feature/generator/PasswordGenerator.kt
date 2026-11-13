package com.keymanage.feature.generator

import com.carti.ribs.Builder
import com.carti.ribs.Dependency
import com.carti.ribs.Interactor
import com.carti.ribs.RibState
import com.carti.ribs.ViewControllable
import com.carti.ribs.ViewPresenter
import com.carti.ribs.ViewRouter
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

/** 생성기 문구 (웹 PROBLEM_MESSAGES와 같다) */
object PasswordGeneratorMessages {
    const val LENGTH_PROBLEM: String = "비밀번호 길이는 최소 8자 이상 64자 이하이어야 합니다."
    const val NO_CHARSET_PROBLEM: String = "최소 1개 이상의 문자 종류를 선택해야 합니다."
    /** 원문을 읽어 주지 않고 바뀌었다는 사실만 보조 기술에 알린다 */
    const val REGENERATED_ANNOUNCEMENT: String = "새 비밀번호를 만들었습니다."
    const val COPY_FAILED: String = "클립보드에 복사하지 못했습니다."
}

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

interface PasswordGeneratorPresentableListener {
    fun handle(action: PasswordGeneratorViewAction)
}

interface PasswordGeneratorPresentable : ViewControllable {
    var listener: PasswordGeneratorPresentableListener?
    fun render(state: PasswordGeneratorViewState)
}

fun interface PasswordGeneratorViewFactory {
    fun makePasswordGeneratorView(): PasswordGeneratorPresentable
}

interface PasswordGeneratorDependency : Dependency {
    val passwordGeneratorViewFactory: PasswordGeneratorViewFactory
    val clipboard: ClipboardPort
    val randomBytes: RandomBytes
}

/** 부모에게 올리는 이벤트. 항목 폼처럼 결과를 받아 쓰는 부모만 듣는다 */
interface PasswordGeneratorListener {
    fun didUsePassword(password: String)
    fun didClosePasswordGenerator()
}

class PasswordGeneratorBuilder(dependency: PasswordGeneratorDependency) : Builder<PasswordGeneratorDependency>(dependency) {
    /**
     * @param listener 항목 폼 안에서 쓸 때 넘긴다. 있으면 사용·닫기 버튼이 생긴다. 생성기 탭처럼 혼자 쓰면 null
     * @param allowCopy 복사 버튼을 둘지. 생성기 탭에서 true
     */
    fun build(listener: PasswordGeneratorListener?, allowCopy: Boolean): PasswordGeneratorRouter {
        val presenter = PasswordGeneratorPresenter(dependency.passwordGeneratorViewFactory.makePasswordGeneratorView())
        val interactor = PasswordGeneratorInteractor(
            presenter = presenter,
            clipboard = dependency.clipboard,
            random = randomSourceOf(dependency.randomBytes),
            listener = listener,
            allowCopy = allowCopy,
        )
        return PasswordGeneratorRouter(presenter.view, interactor)
    }
}

class PasswordGeneratorPresenter(view: PasswordGeneratorPresentable) :
    ViewPresenter<PasswordGeneratorPresentable>(view), PasswordGeneratorPresentableListener {
    var onAction: ((PasswordGeneratorViewAction) -> Unit)? = null

    override fun didLoad() {
        view.listener = this
    }

    override fun willUnload() {
        view.listener = null
    }

    override fun handle(action: PasswordGeneratorViewAction) {
        onAction?.invoke(action)
    }

    fun render(state: PasswordGeneratorViewState) {
        checkMainThread("PasswordGeneratorPresenter.render")
        view.render(state)
    }
}

class PasswordGeneratorInteractor(
    presenter: PasswordGeneratorPresenter,
    private val clipboard: ClipboardPort,
    private val random: RandomSource,
    private val listener: PasswordGeneratorListener?,
    private val allowCopy: Boolean,
) : Interactor<PasswordGeneratorPresenter, PasswordGeneratorRouter>(presenter) {
    private var lengthText = DEFAULT_GENERATOR_OPTIONS.length.toString()
    private var charsets = DEFAULT_GENERATOR_OPTIONS
    private var password = ""
    // 길이 경고는 입력을 마친 뒤에만 보인다. 16을 20으로 고치는 중에 깜빡이지 않게 한다
    private var lengthChecked = false
    private var announcement = ""
    private var copyError: String? = null

    override fun didBecomeActive(savedInstanceState: RibState?) {
        presenter.onAction = ::handle
        // 열면 기본 옵션으로 하나 만들어 보여 준다
        password = generatePassword(DEFAULT_GENERATOR_OPTIONS, random)
        render()
    }

    override fun willResignActive() {
        presenter.onAction = null
    }

    private fun handle(action: PasswordGeneratorViewAction) {
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

    private fun render() {
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
                generated = if (problem == null) describe(password) else null,
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

class PasswordGeneratorRouter(view: PasswordGeneratorPresentable, interactor: PasswordGeneratorInteractor) :
    ViewRouter<PasswordGeneratorPresentable, PasswordGeneratorInteractor>(view, interactor)

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
private fun randomSourceOf(bytes: RandomBytes): RandomSource =
    RandomSource { bytes.nextBytes(4).fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 0xFF) } }
