package com.keymanage.android.domain.clipboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ClipboardBurnerTest {
    private var clock = 0L
    private val clipboard = FakeClipboard()
    private val scheduler = ManualScheduler()
    private val burner = ClipboardBurner(clipboard, scheduler, now = { clock })

    @Test
    fun `복사하고 정확히 30초 뒤에 비운다`() {
        // When
        burner.copy("secret")

        // Then
        assertEquals("secret", clipboard.content)
        assertEquals(listOf(30_000L), scheduler.delays)
        assertEquals(30, burner.remainingSeconds())
        scheduler.runPending()
        assertEquals("", clipboard.content)
        assertNull(burner.remainingSeconds())
    }

    @Test
    fun `다시 복사하면 이전 타이머를 취소하고 새로 30초를 잰다`() {
        burner.copy("a")
        clock = 10_000
        burner.copy("b")
        assertEquals(1, scheduler.cancelled)
        assertEquals(40_000L, burner.expiresAtMillis)
    }

    @Test
    fun `밖에서 다른 텍스트를 복사하면 타이머를 무효로 하고 비우지 않는다`() {
        burner.copy("secret")
        clipboard.content = "user text"
        burner.onExternalChange()
        scheduler.runPending()
        assertEquals("user text", clipboard.content)
    }

    @Test
    fun `잠기면 바로 비우고 기다리는 값이 없으면 건드리지 않는다`() {
        clipboard.content = "user text"
        burner.burnNow()
        assertEquals("user text", clipboard.content)

        burner.copy("secret")
        burner.burnNow()
        assertEquals("", clipboard.content)
        assertEquals(1, scheduler.cancelled)
    }

    @Test
    fun `복사에 실패하면 타이머를 걸지 않는다`() {
        clipboard.failCopy = true
        assertFailsWith<IllegalStateException> { burner.copy("secret") }
        assertEquals(emptyList(), scheduler.delays)
    }

    private class FakeClipboard : ClipboardPort {
        var content = ""
        var failCopy = false
        override fun copySecret(text: String) {
            check(!failCopy) { "복사 실패" }
            content = text
        }

        override fun clear() {
            content = ""
        }
    }

    private class ManualScheduler : Scheduler {
        val delays = mutableListOf<Long>()
        var cancelled = 0
        private val pending = mutableListOf<() -> Unit>()

        override fun schedule(delayMillis: Long, action: () -> Unit): () -> Unit {
            delays += delayMillis
            pending += action
            return {
                cancelled++
                pending.remove(action)
            }
        }

        fun runPending() = pending.toList().forEach { pending.remove(it); it() }
    }
}
