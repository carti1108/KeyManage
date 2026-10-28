package com.keymanage.back.domain.vault

import kotlin.test.Test
import kotlin.test.assertEquals

class EncryptedVaultTests {

    @Test
    fun `용량은 페이로드의 UTF-8 바이트 수로 잰다`() {
        // 한글 1자는 UTF-8로 3바이트다
        assertEquals(5, EncryptedVault("ab가").sizeInBytes)
    }
}
