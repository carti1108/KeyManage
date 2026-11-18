package com.keymanage.android.domain.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

class EncryptedVaultPayloadTest {
    @Test
    fun `AAD는 버전과 암호 이름을 vault-spec 형식으로 잇는다`() {
        // Given
        val payload = EncryptedVaultPayload(version = 1, cipher = "AES-256-GCM", iv = "", ciphertext = "", tag = "")

        // When
        val aad = payload.additionalAuthenticatedData

        // Then
        assertEquals("keymanage-vault|1|AES-256-GCM", aad)
    }
}
