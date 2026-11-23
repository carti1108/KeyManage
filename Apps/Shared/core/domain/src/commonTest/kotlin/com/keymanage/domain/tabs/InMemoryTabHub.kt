package com.keymanage.domain.tabs

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
