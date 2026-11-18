package com.keymanage.android.rib

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * View가 보내는 사용자 동작. View는 [send]만 호출하고, 그 RIB의 Interactor 하나가 [actions]를 구독해 처리한다.
 * 구독 전에 보낸 동작은 버퍼에 남아 있다가 순서대로 전달된다. 구독자는 하나만 둔다.
 */
class ViewActionStream<A : Any> {
    private val channel = Channel<A>(capacity = BUFFER_CAPACITY)

    val actions: Flow<A> = channel.receiveAsFlow()

    /** 버퍼가 가득 차면 false를 돌려준다. 메인 스레드를 막지 않는다. */
    fun send(action: A): Boolean = channel.trySend(action).isSuccess

    private companion object {
        const val BUFFER_CAPACITY = 64
    }
}
