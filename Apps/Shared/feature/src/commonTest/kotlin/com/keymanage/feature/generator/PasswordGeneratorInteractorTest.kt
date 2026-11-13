package com.keymanage.feature.generator

import com.carti.ribs.RibCoroutinesConfig
import com.carti.ribs.RibDispatchersProvider
import com.keymanage.domain.generator.SYMBOLS
import com.keymanage.domain.generator.WEAK_BELOW
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.ClipboardPort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 원본: Apps/Web/src/ui/vault/PasswordGenerator.test.tsx
@OptIn(ExperimentalCoroutinesApi::class)
class PasswordGeneratorInteractorTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private lateinit var view: FakeGeneratorView
    private lateinit var clipboard: FakeClipboard
    private lateinit var random: CountingRandomBytes
    private lateinit var listener: RecordingListener
    private var router: PasswordGeneratorRouter? = null

    @BeforeTest
    fun setUp() {
        RibCoroutinesConfig.dispatchers = SingleDispatcher(dispatcher)
        view = FakeGeneratorView()
        clipboard = FakeClipboard()
        random = CountingRandomBytes()
        listener = RecordingListener()
    }

    @AfterTest
    fun tearDown() {
        router?.dispatchDetach()
        RibCoroutinesConfig.reset()
    }

    private fun open(withListener: Boolean = true, allowCopy: Boolean = false) {
        val dependency = object : PasswordGeneratorDependency {
            override val passwordGeneratorViewFactory = PasswordGeneratorViewFactory { view }
            override val clipboard = this@PasswordGeneratorInteractorTest.clipboard
            override val randomBytes: RandomBytes = random
        }
        router = PasswordGeneratorBuilder(dependency).build(if (withListener) listener else null, allowCopy).also { it.dispatchAttach(null) }
    }

    private val state get() = checkNotNull(view.states.lastOrNull()) { "아직 그리지 않음" }
    private fun send(action: PasswordGeneratorViewAction) = checkNotNull(view.listener).handle(action)

    @Test
    fun opensWithDefaultOptionsAndOneGeneratedPassword() {
        open()

        assertEquals("16", state.lengthText)
        assertTrue(state.uppercase && state.lowercase && state.digits && state.symbols)
        assertEquals(16, state.generated?.text?.length)
        assertNull(state.problemMessage)
        assertEquals("", state.announcement)
        assertTrue(state.canUseGenerated)
    }

    @Test
    fun generatedPasswordComesWithItsStrength() {
        open()

        val generated = assertNotNull(state.generated)
        assertTrue(generated.score >= WEAK_BELOW)
    }

    @Test
    fun changingOptionsGeneratesANewPasswordWithThem() {
        open()

        send(PasswordGeneratorViewAction.ChangeLength("20"))
        send(PasswordGeneratorViewAction.SetCharset(GeneratorCharset.SYMBOLS, false))

        val text = assertNotNull(state.generated).text
        assertEquals(20, text.length)
        assertFalse(text.any { it in SYMBOLS })
        assertFalse(state.symbols)
        assertEquals("20", state.lengthText)
    }

    @Test
    fun regenerateGivesANewPasswordWithTheSameOptions() {
        open()
        val first = assertNotNull(state.generated).text

        send(PasswordGeneratorViewAction.Regenerate)

        val second = assertNotNull(state.generated).text
        assertNotEquals(first, second)
        assertEquals(16, second.length)
    }

    @Test
    fun useHandsOverThePasswordThatIsShown() {
        open()
        val shown = assertNotNull(state.generated).text

        send(PasswordGeneratorViewAction.Use)

        assertEquals(listOf(shown), listener.used)
    }

    @Test
    fun closeNotifiesTheParent() {
        open()

        send(PasswordGeneratorViewAction.Close)

        assertEquals(1, listener.closed)
    }

    @Test
    fun invalidLengthsBlockUseAndWarnOnlyAfterEditingEnds() {
        for (length in listOf("7", "65", "", "1e1", "12.5", "-8", "99999999999")) {
            open()
            val callsBefore = random.calls

            send(PasswordGeneratorViewAction.ChangeLength(length))

            // 입력하는 도중(16 → 2 → 20)에는 경고를 깜빡이지 않는다
            assertNull(state.problemMessage, length)
            assertFalse(state.canUseGenerated, length)
            // 옵션과 맞지 않는 이전 결과는 보이지 않는다
            assertNull(state.generated, length)
            send(PasswordGeneratorViewAction.Use)
            assertEquals(emptyList(), listener.used, length)

            send(PasswordGeneratorViewAction.FinishEditingLength)

            assertEquals(PasswordGeneratorMessages.LENGTH_PROBLEM, state.problemMessage, length)
            assertEquals(callsBefore, random.calls, length)
            router?.dispatchDetach()
            router = null
        }
    }

    @Test
    fun fixingTheLengthClearsTheWarningAndGeneratesAgain() {
        open()

        send(PasswordGeneratorViewAction.ChangeLength("7"))
        send(PasswordGeneratorViewAction.FinishEditingLength)
        assertEquals(PasswordGeneratorMessages.LENGTH_PROBLEM, state.problemMessage)
        send(PasswordGeneratorViewAction.ChangeLength("20"))

        assertNull(state.problemMessage)
        assertTrue(state.canUseGenerated)
        assertEquals(20, assertNotNull(state.generated).text.length)
    }

    @Test
    fun regenerateWithABadLengthShowsTheWarningWithoutGenerating() {
        open()
        send(PasswordGeneratorViewAction.ChangeLength("7"))
        val callsBefore = random.calls

        send(PasswordGeneratorViewAction.Regenerate)

        assertEquals(PasswordGeneratorMessages.LENGTH_PROBLEM, state.problemMessage)
        assertEquals(callsBefore, random.calls)
    }

    @Test
    fun turningEveryCharsetOffWarnsAtOnceAndBlocksUse() {
        open()

        GeneratorCharset.entries.forEach { send(PasswordGeneratorViewAction.SetCharset(it, false)) }
        send(PasswordGeneratorViewAction.Use)

        assertEquals(PasswordGeneratorMessages.NO_CHARSET_PROBLEM, state.problemMessage)
        assertFalse(state.canUseGenerated)
        assertEquals(emptyList(), listener.used)
    }

    @Test
    fun announcesWhenANewPasswordIsMade() {
        open()
        assertEquals("", state.announcement)

        send(PasswordGeneratorViewAction.Regenerate)

        assertEquals(PasswordGeneratorMessages.REGENERATED_ANNOUNCEMENT, state.announcement)
    }

    @Test
    fun insideAFormItOffersUseAndCloseButNotCopy() {
        open(withListener = true, allowCopy = false)

        assertTrue(state.showsUse && state.showsClose)
        assertFalse(state.showsCopy)
    }

    @Test
    fun standaloneItOffersCopyButNotUseOrClose() {
        open(withListener = false, allowCopy = true)

        assertFalse(state.showsUse || state.showsClose)
        assertTrue(state.showsCopy)
        send(PasswordGeneratorViewAction.Use)
        send(PasswordGeneratorViewAction.Close)
        assertEquals(emptyList(), listener.used)
        assertEquals(0, listener.closed)
    }

    @Test
    fun copySendsTheShownPasswordToTheClipboard() {
        open(withListener = false, allowCopy = true)
        val shown = assertNotNull(state.generated).text

        send(PasswordGeneratorViewAction.Copy)
        scope.runCurrent()

        assertEquals(listOf(shown), clipboard.copied)
        assertNull(state.copyError)
    }

    @Test
    fun copyFailureShowsThePolicyMessage() {
        open(withListener = false, allowCopy = true)
        clipboard.failure = IllegalStateException("문서에 포커스가 없음")

        send(PasswordGeneratorViewAction.Copy)
        scope.runCurrent()

        assertEquals(PasswordGeneratorMessages.COPY_FAILED, state.copyError)
        assertEquals(emptyList(), clipboard.copied)
    }

    @Test
    fun copyIsIgnoredWhenNotAllowedOrOptionsAreInvalid() {
        open(withListener = true, allowCopy = false)
        send(PasswordGeneratorViewAction.Copy)
        scope.runCurrent()
        assertEquals(emptyList(), clipboard.copied)

        router?.dispatchDetach()
        open(withListener = false, allowCopy = true)
        send(PasswordGeneratorViewAction.ChangeLength("7"))
        send(PasswordGeneratorViewAction.Copy)
        scope.runCurrent()
        assertEquals(emptyList(), clipboard.copied)
    }

    @Test
    fun viewReceivesActionsOnlyWhileAttached() {
        open()
        assertNotNull(view.listener)

        router?.dispatchDetach()
        router = null

        assertNull(view.listener)
    }

    @Test
    fun clearsTheGeneratedPasswordFromTheViewWhenDetached() {
        open()
        assertNotNull(state.generated)

        router?.dispatchDetach()
        router = null

        assertNull(state.generated)
    }
}

private class SingleDispatcher(dispatcher: CoroutineDispatcher) : RibDispatchersProvider {
    override val Main: CoroutineDispatcher = dispatcher
    override val MainImmediate: CoroutineDispatcher = dispatcher
    override val Default: CoroutineDispatcher = dispatcher
    override val Unconfined: CoroutineDispatcher = dispatcher
}

private class FakeGeneratorView : PasswordGeneratorPresentable {
    override var listener: PasswordGeneratorPresentableListener? = null
    val states = mutableListOf<PasswordGeneratorViewState>()
    override fun render(state: PasswordGeneratorViewState) {
        states += state
    }
}

private class FakeClipboard : ClipboardPort {
    val copied = mutableListOf<String>()
    var failure: Throwable? = null
    override suspend fun copy(text: String) {
        failure?.let { throw it }
        copied += text
    }
}

private class RecordingListener : PasswordGeneratorListener {
    val used = mutableListOf<String>()
    var closed = 0
    override fun didUsePassword(password: String) {
        used += password
    }

    override fun didClosePasswordGenerator() {
        closed++
    }
}

/** 시드를 고정한 난수. 몇 번 불렸는지 센다 */
private class CountingRandomBytes : RandomBytes {
    private val random = Random(42)
    var calls = 0
        private set

    override fun nextBytes(size: Int): ByteArray {
        calls++
        return random.nextBytes(size)
    }
}
