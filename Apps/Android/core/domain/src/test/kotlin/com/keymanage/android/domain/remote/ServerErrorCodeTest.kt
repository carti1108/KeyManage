package com.keymanage.android.domain.remote

import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerErrorCodeTest {
    @Test
    fun `명세의 코드는 같은 이름으로, 모르는 코드와 null은 UNKNOWN으로 읽는다`() {
        assertEquals(ServerErrorCode.VAULT_CONFLICT, ServerErrorCode.of("VAULT_CONFLICT"))
        assertEquals(ServerErrorCode.UNKNOWN, ServerErrorCode.of("NEW_CODE"))
        assertEquals(ServerErrorCode.UNKNOWN, ServerErrorCode.of(null))
        assertEquals(ServerErrorCode.UNKNOWN, ServerErrorCode.of("UNKNOWN"))
    }

    @Test
    fun `페이로드 정규 JSON은 vault-spec 3장 필드 순서를 따른다`() {
        val payload = EncryptedVaultPayload(1, "AES-256-GCM", "aXY=", "Y3Q=", "dGFn")
        assertEquals(
            """{"version":1,"cipher":"AES-256-GCM","iv":"aXY=","ciphertext":"Y3Q=","tag":"dGFn"}""",
            payload.canonicalJson(),
        )
    }
}
