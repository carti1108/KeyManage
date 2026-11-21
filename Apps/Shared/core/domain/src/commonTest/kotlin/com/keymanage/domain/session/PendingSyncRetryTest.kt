package com.keymanage.domain.session

import com.keymanage.domain.testing.FakeTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class PendingSyncRetryTest {
    private val time = FakeTime()
    private var offline = false
    private var networkListener: (() -> Unit)? = null
    private var retries = 0

    private fun retry() = PendingSyncRetry(
        retry = { retries += 1 },
        isOffline = { offline },
        subscribeNetwork = { listener ->
            networkListener = listener
            { networkListener = null }
        },
        scheduler = time,
    )

    @Test
    fun 시작하면_30초마다_다시_보낸다() {
        retry().start()

        time.advance(29.seconds)
        assertEquals(0, retries)
        time.advance(1.seconds)
        assertEquals(1, retries)
        time.advance(30.seconds)
        assertEquals(2, retries)
    }

    @Test
    fun 네트워크가_다시_연결되면_바로_보낸다() {
        retry().start()
        offline = true
        networkListener?.invoke()

        offline = false
        networkListener?.invoke()

        assertEquals(1, retries)
    }

    @Test
    fun 네트워크가_끊길_때는_보내지_않는다() {
        retry().start()

        offline = true
        networkListener?.invoke()

        assertEquals(0, retries)
    }

    @Test
    fun 멈추면_주기와_네트워크_알림을_모두_끊는다() {
        val subject = retry()
        subject.start()

        subject.stop()
        time.advance(60.seconds)

        assertEquals(0, retries)
        assertEquals(null, networkListener)
    }

    @Test
    fun 두_번_시작해도_주기는_하나다() {
        val subject = retry()
        subject.start()
        subject.start()

        time.advance(30.seconds)

        assertEquals(1, retries)
    }

    @Test
    fun 재전송_간격은_30초다() {
        assertEquals(30.seconds, PENDING_SYNC_RETRY_INTERVAL)
    }
}
