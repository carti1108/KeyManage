package com.keymanage.domain.session

import com.keymanage.domain.auth.AuthService
import com.keymanage.domain.tabs.SingleTab
import com.keymanage.domain.vault.RevisionMemory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

// 원본 Promise 사슬에는 없는 경우: 줄에서 기다리던 작업이 취소돼도 뒤 작업이 앞 작업과 겹치면 안 된다 (코드 리뷰 지적)
class PushChainTest {
    @Test
    fun cancelledWaiterDoesNotLetNextRunBeforeCurrentFinishes() = runTest {
        val vaultApi = FakeVaultApi()
        val ctx = SessionContext(
            VaultSessionDeps(
                auth = AuthService(FakeAuthApi()),
                vaultSyncFor = vaultSyncFor(vaultApi),
                revisionMemory = RevisionMemory(),
                deviceStore = FakeDeviceStore(),
                tabs = SingleTab(),
                scope = backgroundScope,
            ),
        )
        val events = mutableListOf<String>()
        val releaseA = CompletableDeferred<Unit>()

        val a = launch(start = CoroutineStart.UNDISPATCHED) {
            ctx.inPushChain { events += "A start"; releaseA.await(); events += "A end" }
        }
        val b = launch(start = CoroutineStart.UNDISPATCHED) { ctx.inPushChain { events += "B" } }
        val c = launch(start = CoroutineStart.UNDISPATCHED) { ctx.inPushChain { events += "C" } }
        testScheduler.advanceUntilIdle()

        b.cancel()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("A start"), events)

        releaseA.complete(Unit)
        a.join()
        c.join()
        assertEquals(listOf("A start", "A end", "C"), events)
    }
}
