package com.keymanage.back.domain.vault

import kotlin.test.Test
import kotlin.test.assertEquals

/** 목록 없이 조립할 때 쓰는 기본 구현은 아무것도 남기지 않는다 (ADR-BE-002) */
class PendingVaultDeletionsTests {

    @Test
    fun `기본 구현은 추가해도 비어 있다`() {
        val none = PendingVaultDeletions.NONE
        none.add(VaultId.of("550e8400-e29b-41d4-a716-446655440000"))
        none.removeAll(setOf(VaultId.of("550e8400-e29b-41d4-a716-446655440000")))

        assertEquals(emptySet(), none.all())
    }
}
