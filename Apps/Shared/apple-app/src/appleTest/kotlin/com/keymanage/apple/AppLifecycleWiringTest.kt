package com.keymanage.apple

import com.keymanage.domain.clipboard.ClipboardBurner
import com.keymanage.domain.clipboard.ClipboardWriter
import com.keymanage.domain.lock.AutoLockSettingStore
import com.keymanage.domain.lock.Scheduler
import com.keymanage.domain.session.LockScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private class ManualClock(var now: Instant = Instant.fromEpochMilliseconds(1_000_000)) : Clock {
    override fun now(): Instant = now
}

/** 시간을 직접 흘리는 스케줄러. 예약 시각이 지난 것만 돌린다 */
private class ManualScheduler(private val clock: ManualClock) : Scheduler {
    private class Entry(val at: Instant, val run: () -> Unit) {
        var cancelled = false
    }

    private val entries = mutableListOf<Entry>()

    override fun schedule(delay: Duration, run: () -> Unit): () -> Unit {
        val entry = Entry(clock.now + delay, run)
        entries += entry
        return { entry.cancelled = true }
    }

    fun advance(by: Duration) {
        clock.now += by
        while (true) {
            val due = entries.firstOrNull { !it.cancelled && it.at <= clock.now } ?: return
            entries -= due
            due.run()
        }
    }

    val pending: Int get() = entries.count { !it.cancelled }
}

private class FakeSession : LifecycleSession {
    var unlocked = false
    val locks = mutableListOf<LockScope>()
    var retries = 0
    private var lockListeners = emptyList<() -> Unit>()
    private var unlockListeners = emptyList<() -> Unit>()

    override fun isUnlocked(): Boolean = unlocked

    override fun lock(scope: LockScope) {
        locks += scope
        if (!unlocked) return
        unlocked = false
        lockListeners.forEach { it() }
    }

    fun unlock() {
        unlocked = true
        unlockListeners.forEach { it() }
    }

    override fun onLock(listener: () -> Unit): () -> Unit {
        lockListeners = lockListeners + listener
        return { lockListeners = lockListeners - listener }
    }

    override fun onUnlock(listener: () -> Unit): () -> Unit {
        unlockListeners = unlockListeners + listener
        return { unlockListeners = unlockListeners - listener }
    }

    override suspend fun retryPendingSync() {
        retries += 1
    }

    val listenerCount: Int get() = lockListeners.size + unlockListeners.size
}

private class MemorySettingStore(var value: Duration = 5.minutes) : AutoLockSettingStore {
    override fun read(): Duration = value
    override fun write(timeout: Duration) {
        value = timeout
    }
    override fun subscribe(listener: (Duration) -> Unit): () -> Unit = {}
}

private class RecordingClipboard : ClipboardWriter {
    val writes = mutableListOf<String>()
    override suspend fun write(text: String) {
        writes += text
    }
}

private class FakeMonitor : NetworkMonitoring {
    var running = false
    override fun start() {
        running = true
    }
    override fun stop() {
        running = false
    }
}

private class Fixture(val scope: TestScope, timeout: Duration = 5.minutes) {
    val clock = ManualClock()
    val scheduler = ManualScheduler(clock)
    val session = FakeSession()
    val settings = MemorySettingStore(timeout)
    val clipboard = RecordingClipboard()
    val burner = ClipboardBurner(clipboard, clock, scheduler, scope)
    val network = NetworkStatus()
    val monitor = FakeMonitor()
    val wiring = AppLifecycleWiring(session, settings, clock, scheduler, burner, network, monitor, scope)
}

class AppLifecycleWiringTest {
    @Test
    fun locksAfterAutoLockTimeoutFromUnlock() = runTest {
        val f = Fixture(this)
        f.wiring.start()
        f.session.unlock()

        f.scheduler.advance(5.minutes - 1.seconds)
        assertTrue(f.session.unlocked)
        f.scheduler.advance(1.seconds)

        assertEquals(false, f.session.unlocked)
        assertEquals(LockScope.ALL_TABS, f.session.locks.single())
    }

    @Test
    fun activityPostponesAutoLock() = runTest {
        val f = Fixture(this)
        f.wiring.start()
        f.session.unlock()

        f.scheduler.advance(4.minutes)
        f.wiring.userActivity()
        f.scheduler.advance(4.minutes)

        assertTrue(f.session.unlocked)
    }

    @Test
    fun alreadyUnlockedSessionIsTimedFromStart() = runTest {
        val f = Fixture(this)
        f.session.unlocked = true
        f.wiring.start()

        f.scheduler.advance(5.minutes)

        assertEquals(false, f.session.unlocked)
    }

    @Test
    fun backgroundLocksOnlyWithImmediateSetting() = runTest {
        val immediate = Fixture(this, Duration.ZERO)
        immediate.wiring.start()
        immediate.session.unlock()
        immediate.wiring.didEnterBackground()
        assertEquals(false, immediate.session.unlocked)

        val fiveMinutes = Fixture(this)
        fiveMinutes.wiring.start()
        fiveMinutes.session.unlock()
        fiveMinutes.wiring.didEnterBackground()
        assertTrue(fiveMinutes.session.unlocked)
    }

    @Test
    fun privacyCoverCoversInBackgroundAndWhileInactiveAndLiftsWhenActiveEvenIfLocked() = runTest {
        // SEC-003 3번: 백그라운드로 갈 때 가림 화면. 옛 Swift VaultLockTests.testBackgroundActivatesPrivacyCover·
        // testWillEnterForegroundDeactivatesPrivacyCoverEvenWhenLocked에서 옮겼다
        val f = Fixture(this, Duration.ZERO)
        val changes = mutableListOf<Boolean>()
        f.wiring.onPrivacyCoverChanged { changes += it }
        f.wiring.start()
        f.session.unlock()
        assertFalse(f.wiring.isPrivacyCoverActive)

        f.wiring.didEnterBackground()
        assertTrue(f.wiring.isPrivacyCoverActive)
        assertEquals(false, f.session.unlocked)

        f.wiring.willEnterForeground()
        assertFalse(f.wiring.isPrivacyCoverActive)

        // iOS 비활성(제어 센터·앱 전환 화면)은 가리기만 하고 잠그지 않는다
        f.session.unlock()
        f.wiring.didResignActive()
        assertTrue(f.wiring.isPrivacyCoverActive)
        assertTrue(f.session.unlocked)
        f.wiring.didBecomeActive()
        assertFalse(f.wiring.isPrivacyCoverActive)

        assertEquals(listOf(true, false, true, false), changes)
    }

    @Test
    fun foregroundLocksWhenTimeoutPassedWhileTimerWasDelayed() = runTest {
        val f = Fixture(this)
        f.wiring.start()
        f.session.unlock()
        f.wiring.didEnterBackground()

        // 백그라운드에서 타이머가 돌지 못한 채 시간만 지남
        f.clock.now += 6.minutes
        f.wiring.willEnterForeground()

        assertEquals(false, f.session.unlocked)
    }

    @Test
    fun foregroundBeforeTimeoutKeepsVaultOpen() = runTest {
        val f = Fixture(this)
        f.wiring.start()
        f.session.unlock()
        f.wiring.didEnterBackground()
        f.clock.now += 2.minutes
        f.wiring.willEnterForeground()

        assertTrue(f.session.unlocked)
    }

    @Test
    fun changedAutoLockSettingAppliesFromNow() = runTest {
        val f = Fixture(this)
        f.wiring.start()
        f.session.unlock()
        f.scheduler.advance(30.seconds)

        f.wiring.autoLockSetting.write(1.minutes)
        f.scheduler.advance(59.seconds)
        assertTrue(f.session.unlocked)
        f.scheduler.advance(1.seconds)

        assertEquals(false, f.session.unlocked)
        assertEquals(1.minutes, f.settings.value)
    }

    @Test
    fun lockBurnsClipboardRightAway() = runTest {
        val f = Fixture(this)
        f.wiring.start()
        f.session.unlock()
        f.burner.copy("secret")

        f.session.lock(LockScope.ALL_TABS)
        runCurrent()

        assertEquals(listOf("secret", ""), f.clipboard.writes)
    }

    @Test
    fun retriesPendingSyncEveryThirtySecondsAndOnReconnect() = runTest {
        val f = Fixture(this)
        f.wiring.start()

        f.scheduler.advance(30.seconds)
        runCurrent()
        assertEquals(1, f.session.retries)
        f.scheduler.advance(30.seconds)
        runCurrent()
        assertEquals(2, f.session.retries)

        f.network.update(reachable = false)
        runCurrent()
        assertEquals(2, f.session.retries)
        f.network.update(reachable = true)
        runCurrent()
        assertEquals(3, f.session.retries)
    }

    @Test
    fun startAndStopControlNetworkMonitorAndRelease() = runTest {
        val f = Fixture(this)
        f.wiring.start()
        assertTrue(f.monitor.running)
        f.session.unlock()

        f.wiring.stop()

        assertEquals(false, f.monitor.running)
        assertEquals(0, f.session.listenerCount)
        assertEquals(0, f.scheduler.pending)
        f.scheduler.advance(10.minutes)
        assertTrue(f.session.unlocked)
    }

    @Test
    fun startTwiceWiresOnce() = runTest {
        val f = Fixture(this)
        f.wiring.start()
        f.wiring.start()

        assertEquals(2, f.session.listenerCount)
    }
}
