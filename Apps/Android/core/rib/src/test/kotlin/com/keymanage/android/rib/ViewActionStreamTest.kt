package com.keymanage.android.rib

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ViewActionStreamTest {
    @Test
    fun `구독 전에 보낸 동작도 순서대로 전달된다`() = runTest {
        // Given
        val stream = ViewActionStream<String>()

        // When
        stream.send("first")
        stream.send("second")
        val received = stream.actions.take(2).toList()

        // Then
        assertEquals(listOf("first", "second"), received)
    }
}
