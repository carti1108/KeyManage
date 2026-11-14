package com.keymanage.apple

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkStatusTest {
    @Test
    fun isOnlineUntilFirstPathArrives() {
        assertFalse(NetworkStatus().isOffline())
    }

    @Test
    fun notifiesOnlyWhenOfflineStateChanges() {
        val status = NetworkStatus()
        var calls = 0
        status.subscribe { calls += 1 }

        status.update(reachable = true)
        status.update(reachable = false)
        status.update(reachable = false)

        assertTrue(status.isOffline())
        assertEquals(1, calls)
    }

    @Test
    fun unsubscribedListenerIsNotCalled() {
        val status = NetworkStatus()
        var calls = 0
        val unsubscribe = status.subscribe { calls += 1 }

        unsubscribe()
        status.update(reachable = false)

        assertEquals(0, calls)
    }
}
