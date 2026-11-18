package com.keymanage.android.feature.generator

import com.keymanage.android.domain.clipboard.ClipboardBurner
import com.keymanage.android.domain.clipboard.ClipboardPort
import com.keymanage.android.domain.clipboard.Scheduler
import com.keymanage.android.domain.generator.PasswordGenerator
import com.keymanage.android.feature.generator.impl.Charset
import com.keymanage.android.feature.generator.impl.GeneratorInteractor
import com.keymanage.android.feature.generator.impl.GeneratorMessages
import com.keymanage.android.feature.generator.impl.GeneratorPresenter
import com.keymanage.android.feature.generator.impl.GeneratorViewAction
import com.keymanage.android.feature.generator.impl.GeneratorViewState
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeneratorInteractorTest {
    private var copied = ""
    private val clipboard = object : ClipboardPort {
        override fun copySecret(text: String) {
            copied = text
        }

        override fun clear() {
            copied = ""
        }
    }
    private val store = ViewStateStore(GeneratorViewState())
    private val interactor = GeneratorInteractor(
        GeneratorPresenter(store, ViewActionStream()), store, ViewActionStream(), PasswordGenerator(),
        ClipboardBurner(clipboard, Scheduler { _, _ -> {} }, now = { 0L }),
    )

    @Test
    fun `처음에 기본 옵션 16자 비밀번호를 만든다`() {
        assertEquals(16, store.current.password.length)
        assertNull(store.current.problemMessage)
    }

    @Test
    fun `길이를 바꾸면 그 길이로 다시 만든다`() {
        interactor.handle(GeneratorViewAction.LengthChanged(40))
        assertEquals(40, store.current.password.length)
    }

    @Test
    fun `문자군을 끄면 그 문자군은 빠진다`() {
        interactor.handle(GeneratorViewAction.Toggle(Charset.SYMBOLS, false))
        assertTrue(store.current.password.none { it in PasswordGenerator.SYMBOLS })
    }

    @Test
    fun `문자군을 모두 끄면 생성을 막고 GEN-001 문구를 보인다`() {
        for (charset in Charset.entries) interactor.handle(GeneratorViewAction.Toggle(charset, false))
        assertEquals("", store.current.password)
        assertEquals(GeneratorMessages.NO_CHARSET, store.current.problemMessage)
    }

    @Test
    fun `복사하면 클립보드에 넣고 30초 배지를 보인다`() {
        interactor.handle(GeneratorViewAction.Copy)
        assertEquals(store.current.password, copied)
        assertEquals(30, store.current.burnSeconds)
    }
}
