package com.keymanage.back.domain.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RevisionTests {

    @Test
    fun `볼트가 없을 때의 revision은 0이다`() {
        assertEquals(0L, Revision.NONE.value)
    }

    @Test
    fun `다음 revision은 1 증가한다`() {
        assertEquals(Revision(8), Revision(7).next())
    }

    @Test
    fun `음수 revision은 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { Revision(-1) }
    }
}
