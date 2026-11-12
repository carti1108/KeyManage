package com.keymanage.domain.tabs

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

// 원본: Apps/Web/src/domain/tabs/inMemoryTabs.test.ts (ADR-WEB-001)
@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryTabHubTest {
    @Test
    fun twoTabsLockSectionsDoNotOverlap() = runTest(UnconfinedTestDispatcher()) {
        val hub = InMemoryTabHub()
        val a = hub.connect()
        val b = hub.connect()
        val events = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()

        val first = async {
            a.lock.run(TabLockName.DEVICE) {
                events += "a:start"
                gate.await()
                events += "a:end"
                "a"
            }
        }
        val second = async {
            b.lock.run(TabLockName.DEVICE) {
                events += "b:start"
                "b"
            }
        }
        gate.complete(Unit)

        assertEquals(listOf("a", "b"), awaitAll(first, second))
        assertEquals(listOf("a:start", "a:end", "b:start"), events)
    }

    @Test
    fun differentLockNamesDoNotWaitForEachOther() = runTest(UnconfinedTestDispatcher()) {
        val hub = InMemoryTabHub()
        val gate = CompletableDeferred<Unit>()
        val held = async { hub.connect().lock.run(TabLockName.DEVICE) { gate.await() } }

        assertEquals("sync", hub.connect().lock.run(TabLockName.SYNC) { "sync" })
        gate.complete(Unit)
        held.await()
    }

    @Test
    fun ifAvailableReturnsNullWithoutRunningWhileAnotherTabHoldsLock() = runTest(UnconfinedTestDispatcher()) {
        val hub = InMemoryTabHub()
        val gate = CompletableDeferred<Unit>()
        val held = async { hub.connect().lock.run(TabLockName.SYNC) { gate.await() } }
        var ran = false

        val result = hub.connect().lock.run(TabLockName.SYNC, ifAvailable = true) { ran = true }

        assertNull(result)
        assertFalse(ran)
        gate.complete(Unit)
        held.await()
    }

    @Test
    fun failedRunStillReleasesLock() = runTest(UnconfinedTestDispatcher()) {
        val tabs = InMemoryTabHub().connect()

        assertFailsWith<IllegalStateException> { tabs.lock.run(TabLockName.DEVICE) { error("x") } }
        assertEquals(1, tabs.lock.run(TabLockName.DEVICE) { 1 })
    }

    @Test
    fun postedMessagesReachOnlyOtherTabs() {
        val hub = InMemoryTabHub()
        val a = hub.connect()
        val b = hub.connect()
        val toA = mutableListOf<TabMessage>()
        val toB = mutableListOf<TabMessage>()
        a.channel.subscribe { toA += it }
        b.channel.subscribe { toB += it }

        a.channel.post(TabMessage.LOCKED)

        assertEquals(emptyList(), toA)
        assertEquals(listOf(TabMessage.LOCKED), toB)
    }

    @Test
    fun unsubscribeStopsDelivery() {
        val hub = InMemoryTabHub()
        val a = hub.connect()
        val received = mutableListOf<TabMessage>()
        val unsubscribe = hub.connect().channel.subscribe { received += it }

        unsubscribe()
        a.channel.post(TabMessage.ACTIVITY)

        assertEquals(emptyList(), received)
    }

    @Test
    fun wireNamesMatchWeb() {
        assertEquals(listOf("record-changed", "locked", "logged-out", "activity"), TabMessage.entries.map { it.wire })
        assertEquals(TabMessage.LOCKED, TabMessage.fromWire("locked"))
        assertNull(TabMessage.fromWire("unknown"))
        assertNull(TabMessage.fromWire(null))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SingleTabTest {
    @Test
    fun runsLockSectionsInOrderAndSendsNothing() = runTest(UnconfinedTestDispatcher()) {
        val tabs = SingleTab()
        val events = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val received = mutableListOf<TabMessage>()
        tabs.channel.subscribe { received += it }

        val first = async {
            tabs.lock.run(TabLockName.DEVICE) {
                events += "1:start"
                gate.await()
                events += "1:end"
            }
        }
        val second = async { tabs.lock.run(TabLockName.DEVICE) { events += "2:start" } }
        tabs.channel.post(TabMessage.LOCKED)
        gate.complete(Unit)
        awaitAll(first, second)

        assertEquals(listOf("1:start", "1:end", "2:start"), events)
        assertEquals(emptyList(), received)
    }
}
