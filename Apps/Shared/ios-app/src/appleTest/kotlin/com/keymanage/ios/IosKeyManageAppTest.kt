package com.keymanage.ios

import com.carti.ribs.ViewHost
import com.keymanage.domain.auth.MasterPasswordChangedException
import com.keymanage.domain.device.DeviceStorageException
import com.keymanage.domain.session.SessionExpiredException
import com.keymanage.feature.messages.describeError
import com.keymanage.feature.root.RootViewControllable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class RecordingLifecycle(private val failOnStart: Boolean = false) : AppLifecycle {
    val calls = mutableListOf<String>()
    override fun start() {
        calls += "start"
        if (failOnStart) throw IllegalStateException("monitor")
    }
    override fun stop() { calls += "stop" }
    override fun didEnterBackground() { calls += "background" }
    override fun willEnterForeground() { calls += "foreground" }
    override fun userActivity() { calls += "activity" }
}

private object NoHost : ViewHost<RootViewControllable> {
    override fun show(view: RootViewControllable) = Unit
    override fun hide(view: RootViewControllable) = Unit
}

private class TreeLog {
    val events = mutableListOf<String>()
    var notice: String? = "unset"

    var failLaunch = false

    fun launcher(): (ViewHost<RootViewControllable>, String?) -> RunningTree = { _, notice ->
        if (failLaunch) throw IllegalStateException("tree")
        this.notice = notice
        events += "launch"
        RunningTree { events += "shutdown" }
    }
}

private fun TestScope.app(
    restore: suspend () -> Unit = {},
    tree: TreeLog = TreeLog(),
    lifecycle: RecordingLifecycle = RecordingLifecycle(),
) = IosKeyManageApp(backgroundScope, restore, tree.launcher(), lifecycle)

class IosKeyManageAppTest {
    @Test
    fun launchesTreeAfterRestoringDeviceRecord() = runTest {
        val restored = CompletableDeferred<Unit>()
        val tree = TreeLog()
        val lifecycle = RecordingLifecycle()
        val app = app(restore = { restored.await() }, tree = tree, lifecycle = lifecycle)

        app.start(NoHost) {}
        runCurrent()
        assertEquals(emptyList(), tree.events)

        restored.complete(Unit)
        runCurrent()

        assertEquals(listOf("launch"), tree.events)
        assertNull(tree.notice)
        assertEquals(listOf("start"), lifecycle.calls)
    }

    @Test
    fun expiredSessionDuringRestoreShowsSignInNotice() = runTest {
        val tree = TreeLog()
        app(restore = { throw SessionExpiredException() }, tree = tree).start(NoHost) {}
        runCurrent()

        assertEquals(describeError(SessionExpiredException()), tree.notice)
    }

    @Test
    fun passwordChangedElsewhereDuringRestoreShowsSignInNotice() = runTest {
        val tree = TreeLog()
        app(restore = { throw MasterPasswordChangedException() }, tree = tree).start(NoHost) {}
        runCurrent()

        assertEquals(describeError(MasterPasswordChangedException()), tree.notice)
    }

    @Test
    fun unreadableDeviceRecordStartsAtSignInWithoutNotice() = runTest {
        val tree = TreeLog()
        app(restore = { throw DeviceStorageException(IllegalStateException("io")) }, tree = tree).start(NoHost) {}
        runCurrent()

        assertEquals(listOf("launch"), tree.events)
        assertNull(tree.notice)
    }

    @Test
    fun startTwiceLaunchesOnce() = runTest {
        val tree = TreeLog()
        val app = app(tree = tree)
        app.start(NoHost) {}
        app.start(NoHost) {}
        runCurrent()

        assertEquals(listOf("launch"), tree.events)
    }

    @Test
    fun stopShutsDownTreeAndLifecycleAndCanStartAgain() = runTest {
        val tree = TreeLog()
        val lifecycle = RecordingLifecycle()
        val app = app(tree = tree, lifecycle = lifecycle)
        app.start(NoHost) {}
        runCurrent()

        app.stop()
        app.start(NoHost) {}
        runCurrent()

        assertEquals(listOf("launch", "shutdown", "launch"), tree.events)
        assertEquals(listOf("start", "stop", "start"), lifecycle.calls)
    }

    @Test
    fun stopDuringRestoreNeverLaunches() = runTest {
        val restored = CompletableDeferred<Unit>()
        val tree = TreeLog()
        val app = app(restore = { restored.await() }, tree = tree)
        app.start(NoHost) {}
        runCurrent()

        app.stop()
        restored.complete(Unit)
        runCurrent()

        assertTrue(tree.events.isEmpty())
    }

    @Test
    fun forwardsAppLifecycleEventsOnlyWhileRunning() = runTest {
        val lifecycle = RecordingLifecycle()
        val app = app(lifecycle = lifecycle)
        app.didEnterBackground()
        app.start(NoHost) {}
        runCurrent()

        app.didEnterBackground()
        app.willEnterForeground()
        app.userActivity()

        assertEquals(listOf("start", "background", "foreground", "activity"), lifecycle.calls)
    }

    @Test
    fun treeLaunchFailureReportsAndAllowsRetry() = runTest {
        val tree = TreeLog().apply { failLaunch = true }
        val app = app(tree = tree)
        val failures = mutableListOf<String>()

        app.start(NoHost) { failures += it }
        runCurrent()
        assertEquals(listOf(START_FAILED_MESSAGE), failures)

        tree.failLaunch = false
        app.start(NoHost) { failures += it }
        runCurrent()
        assertEquals(listOf("launch"), tree.events)
    }

    @Test
    fun lifecycleStartFailureShutsTreeDownAndReports() = runTest {
        val tree = TreeLog()
        val lifecycle = RecordingLifecycle(failOnStart = true)
        val app = app(tree = tree, lifecycle = lifecycle)
        val failures = mutableListOf<String>()

        app.start(NoHost) { failures += it }
        runCurrent()

        assertEquals(listOf("launch", "shutdown"), tree.events)
        assertEquals(1, failures.size)
        app.didEnterBackground()
        assertEquals(listOf("start"), lifecycle.calls)
    }
}
