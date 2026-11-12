package com.keymanage.domain.tabs

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// 같은 기기에서 같은 앱을 연 탭끼리 맞추는 포트 (ADR-WEB-001). 원본: Apps/Web/src/domain/tabs/tabs.ts, inMemoryTabs.ts
// 메시지에는 "무엇이 바뀌었다"만 담는다. 키·평문·인증 해시·토큰·이메일은 탭 사이로 넘기지 않는다.
// 모바일 앱은 탭이 하나뿐이라 SingleTab을 쓴다. 세션 코드에 플랫폼 분기가 생기지 않는다

enum class TabMessage(val wire: String) {
    RECORD_CHANGED("record-changed"),
    LOCKED("locked"),
    LOGGED_OUT("logged-out"),
    ACTIVITY("activity"),
    ;

    companion object {
        private val byWire = entries.associateBy { it.wire }

        /** 형식이 틀린 메시지는 null. 받는 쪽이 버린다 */
        fun fromWire(value: String?): TabMessage? = value?.let(byWire::get)
    }
}

/** DEVICE: 기기 기록 읽기·병합·쓰기, SYNC: 대기 내용 재전송 */
enum class TabLockName { DEVICE, SYNC }

interface TabLock {
    /** 다른 탭과 겹치지 않게 [block]을 실행한다. [ifAvailable]이면 잠금을 바로 못 얻을 때 실행 없이 null */
    suspend fun <T> run(name: TabLockName, ifAvailable: Boolean = false, block: suspend () -> T): T?
}

interface TabChannel {
    fun post(message: TabMessage)

    /** 다른 탭이 보낸 메시지만 받는다 */
    fun subscribe(listener: (TabMessage) -> Unit): () -> Unit
}

interface Tabs {
    val lock: TabLock
    val channel: TabChannel
}

/** 이름마다 한 번에 하나씩 실행하는 잠금. 같은 잠금을 쓰는 모든 탭에 걸린다 */
private class SerialLock : TabLock {
    private val mutexes = TabLockName.entries.associateWith { Mutex() }
    private val waiting = TabLockName.entries.associateWith { 0 }.toMutableMap()

    override suspend fun <T> run(name: TabLockName, ifAvailable: Boolean, block: suspend () -> T): T? {
        val count = waiting.getValue(name)
        if (ifAvailable && count > 0) return null
        waiting[name] = count + 1
        try {
            // 실패해도 withLock이 잠금을 풀어 다음 실행이 잠금을 얻는다
            return mutexes.getValue(name).withLock { block() }
        } finally {
            waiting[name] = waiting.getValue(name) - 1
        }
    }
}

/** 같은 허브에 붙은 [Tabs]끼리 잠금·메시지를 공유한다. 테스트에서 여러 탭을 흉내 낼 때 쓴다 */
class InMemoryTabHub {
    private val lock = SerialLock()
    private val subscribers = mutableListOf<Pair<Int, (TabMessage) -> Unit>>()
    private var nextId = 0

    fun connect(): Tabs {
        val owner = nextId++
        val channel = object : TabChannel {
            override fun post(message: TabMessage) {
                subscribers.toList().forEach { (id, listener) -> if (id != owner) listener(message) }
            }

            override fun subscribe(listener: (TabMessage) -> Unit): () -> Unit {
                val subscriber = owner to listener
                subscribers += subscriber
                return { subscribers.remove(subscriber) }
            }
        }
        return object : Tabs {
            override val lock: TabLock = this@InMemoryTabHub.lock
            override val channel: TabChannel = channel
        }
    }
}

/** 탭 안 직렬화 잠금과 아무것도 보내지 않는 채널. 모바일 앱과 탭 간 기능이 없는 브라우저가 쓴다 */
class SingleTab : Tabs {
    override val lock: TabLock = SerialLock()
    override val channel: TabChannel = object : TabChannel {
        override fun post(message: TabMessage) {
            // 다른 탭에 알릴 수단이 없다
        }

        override fun subscribe(listener: (TabMessage) -> Unit): () -> Unit = {}
    }
}
