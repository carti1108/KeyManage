package com.keymanage.domain.clipboard

import com.keymanage.domain.testing.FakeTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// 원본: Apps/Web/src/domain/clipboard/clipboardBurner.test.ts (COM-001)
private class FakeClipboard : ClipboardWriter {
    val writes = mutableListOf<String>()
    var failing = false

    /** 설정하면 그 값의 쓰기가 끝나지 않고 기다린다. 늦게 끝나는 쓰기를 흉내 낸다 */
    var holdFor: ((String) -> CompletableDeferred<Unit>?)? = null

    override suspend fun write(text: String) {
        if (failing) throw IllegalStateException("문서에 포커스가 없음")
        writes += text
        holdFor?.invoke(text)?.await()
    }
}

private fun at(seconds: Int) = Instant.fromEpochMilliseconds(0) + seconds.seconds

@OptIn(ExperimentalCoroutinesApi::class)
private class Setup(scope: TestScope) {
    val time = FakeTime()
    val board = FakeClipboard()
    val burner = ClipboardBurner(board, time, time, scope.backgroundScope)
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun burnerTest(body: suspend TestScope.(Setup) -> Unit) = runTest(UnconfinedTestDispatcher()) { body(Setup(this)) }

class ClipboardBurnerTest {
    @Test
    fun wipesExactly30SecondsAfterCopy() = burnerTest { s ->
        s.burner.copy("secret")
        assertEquals(listOf("secret"), s.board.writes)
        assertEquals(BurnState.Counting(expiresAt = at(30)), s.burner.state.value)

        s.time.advance(30.seconds - kotlin.time.Duration.parse("1ms"))
        assertEquals(listOf("secret"), s.board.writes)

        s.time.advance(kotlin.time.Duration.parse("1ms"))
        assertEquals(listOf("secret", ""), s.board.writes)
        assertEquals(BurnState.Idle, s.burner.state.value)
    }

    @Test
    fun recopyWithin30SecondsRestartsTheTimer() = burnerTest { s ->
        s.burner.copy("first")
        s.time.advance(20.seconds)
        s.burner.copy("second")
        s.time.advance(10.seconds)

        assertEquals(listOf("first", "second"), s.board.writes)

        s.time.advance(20.seconds)
        assertEquals(listOf("first", "second", ""), s.board.writes)
    }

    @Test
    fun failedCopyThrowsAndDoesNotStartTimer() = burnerTest { s ->
        s.board.failing = true

        assertFailsWith<IllegalStateException> { s.burner.copy("secret") }

        assertEquals(BurnState.Idle, s.burner.state.value)
    }

    @Test
    fun failedWipeWaitsForRetry() = burnerTest { s ->
        s.burner.copy("secret")

        s.board.failing = true
        s.time.advance(30.seconds)
        assertEquals(BurnState.PendingBurn, s.burner.state.value)

        s.board.failing = false
        s.burner.retry()

        assertEquals(listOf("secret", ""), s.board.writes)
        assertEquals(BurnState.Idle, s.burner.state.value)
    }

    @Test
    fun retryWithNothingToWipeLeavesClipboardAlone() = burnerTest { s ->
        s.burner.retry()
        s.burner.copy("secret")
        s.burner.retry()

        assertEquals(listOf("secret"), s.board.writes)
    }

    @Test
    fun lockWipesImmediately() = burnerTest { s ->
        s.burner.copy("secret")

        s.burner.clear()
        assertEquals(listOf("secret", ""), s.board.writes)

        s.time.advance(30.seconds)
        assertEquals(listOf("secret", ""), s.board.writes)
    }

    @Test
    fun failedWipeOnLockRetriesLater() = burnerTest { s ->
        s.burner.copy("secret")

        s.board.failing = true
        s.burner.clear()
        assertEquals(BurnState.PendingBurn, s.burner.state.value)

        s.board.failing = false
        s.burner.retry()
        assertEquals(listOf("secret", ""), s.board.writes)
    }

    @Test
    fun lockWithoutCopyLeavesClipboardAlone() = burnerTest { s ->
        s.burner.clear()

        assertEquals(emptyList(), s.board.writes)
    }

    @Test
    fun newCopyDuringSlowWipeIsNotWiped() = burnerTest { s ->
        val wipe = CompletableDeferred<Unit>()
        s.burner.copy("first")
        // 비우기(빈 값 쓰기)가 늦게 끝나게 한다
        s.board.holdFor = { text -> wipe.takeIf { text == "" } }

        s.time.advance(30.seconds)
        s.burner.copy("second")
        wipe.complete(Unit)

        assertEquals(BurnState.Counting(expiresAt = at(60)), s.burner.state.value)
    }

    @Test
    fun lockBeforeCopyFinishesWipesWithoutTimer() = burnerTest { s ->
        val copyDone = CompletableDeferred<Unit>()
        s.board.holdFor = { text -> copyDone.takeIf { text != "" } }

        val copying = async { s.burner.copy("secret") }
        s.burner.clear()
        copyDone.complete(Unit)
        copying.await()

        assertEquals(listOf("secret", ""), s.board.writes)
        assertEquals(BurnState.Idle, s.burner.state.value)
        s.time.advance(30.seconds)
        assertEquals(listOf("secret", ""), s.board.writes)
    }

    @Test
    fun earlierSlowCopyDoesNotReplaceLaterCopysTimer() = burnerTest { s ->
        val firstDone = CompletableDeferred<Unit>()
        s.board.holdFor = { text -> firstDone.takeIf { text == "first" } }

        val first = async { s.burner.copy("first") }
        s.time.advance(5.seconds)
        s.burner.copy("second")
        s.time.advance(1.seconds)
        firstDone.complete(Unit)
        first.await()

        assertEquals(BurnState.Counting(expiresAt = at(35)), s.burner.state.value)
    }

    @Test
    fun stateChangesArePublished() = burnerTest { s ->
        val seen = mutableListOf<BurnState>()
        val watching = launch { s.burner.state.collect { seen += it } }

        s.burner.copy("secret")
        s.time.advance(30.seconds)
        watching.cancel()
        s.burner.copy("again")

        assertEquals(listOf(BurnState.Idle, BurnState.Counting(at(30)), BurnState.Idle), seen)
    }
}
