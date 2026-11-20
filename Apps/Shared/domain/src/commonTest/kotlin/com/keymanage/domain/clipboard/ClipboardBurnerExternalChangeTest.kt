package com.keymanage.domain.clipboard

import com.keymanage.domain.testing.FakeTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// 사용자가 그사이 다른 것을 복사했으면 비우지 않는다 (COM-001 규칙 2)
// 원본: Apps/Android/core/domain/src/test/.../clipboard/ClipboardBurnerTest.kt의 onExternalChange 경우
private class RecordingClipboard : ClipboardWriter {
    val writes = mutableListOf<String>()
    var failing = false

    override suspend fun write(text: String) {
        if (failing) throw IllegalStateException("클립보드를 쓸 수 없음")
        writes += text
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun externalChangeTest(body: suspend TestScope.(ClipboardBurner, RecordingClipboard, FakeTime) -> Unit) =
    runTest(UnconfinedTestDispatcher()) {
        val time = FakeTime()
        val board = RecordingClipboard()
        body(ClipboardBurner(board, time, time, backgroundScope), board, time)
    }

class ClipboardBurnerExternalChangeTest {
    @Test
    fun externalCopyCancelsTheWipe() = externalChangeTest { burner, board, time ->
        burner.copy("secret")
        time.advance(10.seconds)

        burner.onExternalChange()
        time.advance(30.seconds)

        assertEquals(listOf("secret"), board.writes)
        assertEquals(BurnState.Idle, burner.state.value)
    }

    @Test
    fun lockAfterExternalCopyLeavesClipboardAlone() = externalChangeTest { burner, board, _ ->
        burner.copy("secret")
        burner.onExternalChange()

        burner.clear()

        assertEquals(listOf("secret"), board.writes)
    }

    @Test
    fun externalCopyWhileWipeIsPendingCancelsTheRetry() = externalChangeTest { burner, board, time ->
        burner.copy("secret")
        board.failing = true
        time.advance(30.seconds)
        assertEquals(BurnState.PendingBurn, burner.state.value)
        board.failing = false

        burner.onExternalChange()
        burner.retry()

        assertEquals(listOf("secret"), board.writes)
        assertEquals(BurnState.Idle, burner.state.value)
    }

    @Test
    fun externalCopyWithNothingCopiedDoesNothing() = externalChangeTest { burner, board, time ->
        burner.onExternalChange()
        time.advance(30.seconds)

        assertEquals(emptyList(), board.writes)
        assertEquals(BurnState.Idle, burner.state.value)
    }

    @Test
    fun copyAfterExternalCopyStartsANewTimer() = externalChangeTest { burner, board, time ->
        burner.copy("first")
        time.advance(10.seconds)
        burner.onExternalChange()

        burner.copy("second")
        assertEquals(BurnState.Counting(Instant.fromEpochMilliseconds(0) + 40.seconds), burner.state.value)
        time.advance(30.seconds)

        assertEquals(listOf("first", "second", ""), board.writes)
    }
}
