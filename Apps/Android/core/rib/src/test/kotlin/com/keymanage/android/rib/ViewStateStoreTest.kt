package com.keymanage.android.rib

import kotlin.test.Test
import kotlin.test.assertEquals

class ViewStateStoreTest {
    private data class CounterState(val count: Int = 0)

    @Test
    fun `update는 이전 상태를 받아 새 상태로 바꾼다`() {
        // Given
        val store = ViewStateStore(CounterState())

        // When
        store.update { it.copy(count = it.count + 1) }
        store.update { it.copy(count = it.count + 1) }

        // Then
        assertEquals(CounterState(count = 2), store.current)
        assertEquals(store.current, store.state.value)
    }
}
