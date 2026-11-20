package com.keymanage.feature.main.testing

import com.keymanage.feature.ClipboardPort
import kotlin.time.Instant

/** 복사한 값을 기록하고, 비울 시각을 테스트가 정하는 클립보드. [emit]으로 듣는 쪽에 알린다 */
class FakeBurningClipboard : ClipboardPort {
    val copied = mutableListOf<String>()
    override var burnAt: Instant? = null
    private val listeners = mutableListOf<(Instant?) -> Unit>()
    val listenerCount: Int get() = listeners.size

    override suspend fun copy(text: String) {
        copied += text
    }

    override fun subscribeBurn(listener: (Instant?) -> Unit): () -> Unit {
        listeners += listener
        return { listeners.remove(listener) }
    }

    /** 복사했으면 비울 시각을, 비웠거나 다른 것을 복사했으면 null을 알린다 */
    fun emit(at: Instant?) {
        burnAt = at
        listeners.toList().forEach { it(at) }
    }
}
