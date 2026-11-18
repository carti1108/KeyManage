package com.keymanage.android.feature.root.tests

import com.keymanage.android.domain.clipboard.Scheduler
import com.keymanage.android.domain.lock.AutoLockOption
import com.keymanage.android.domain.lock.AutoLockSettingStore
import com.keymanage.android.feature.root.impl.RootAutoLock
import kotlin.test.Test
import kotlin.test.assertEquals

class RootAutoLockTest {
    private var clock = 0L
    private var option = AutoLockOption.ONE_MINUTE
    private val store = object : AutoLockSettingStore {
        override fun read() = option
        override fun write(option: AutoLockOption) = Unit
    }
    private val delays = mutableListOf<Long>()
    private val scheduler = Scheduler { delay, _ -> delays += delay; {} }
    private val autoLock = RootAutoLock(store, scheduler) { clock }
    private var locks = 0

    @Test
    fun `저장된 시간으로 시작하고 앞으로 돌아왔을 때 지났으면 잠근다`() {
        autoLock.start { locks++ }
        assertEquals(listOf(60_000L), delays)
        autoLock.onBackground()
        clock = 60_000
        autoLock.onForeground()
        assertEquals(1, locks)
    }

    @Test
    fun `설정을 바꾸면 새 시간으로 다시 잰다`() {
        autoLock.start { locks++ }
        option = AutoLockOption.IMMEDIATELY
        autoLock.restart()
        autoLock.onBackground()
        assertEquals(1, locks)
    }

    @Test
    fun `멈춘 뒤에는 설정을 바꿔도 다시 시작하지 않는다`() {
        autoLock.start { locks++ }
        autoLock.stop()
        option = AutoLockOption.IMMEDIATELY
        autoLock.restart()
        autoLock.onBackground()
        assertEquals(0, locks)
    }

    @Test
    fun `백그라운드에 있는 동안 즉시 설정으로 시작하면 바로 잠근다`() {
        option = AutoLockOption.IMMEDIATELY
        autoLock.onBackground()
        autoLock.start { locks++ }
        assertEquals(1, locks)
    }
}
