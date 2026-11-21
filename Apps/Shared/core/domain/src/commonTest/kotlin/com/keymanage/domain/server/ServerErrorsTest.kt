package com.keymanage.domain.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

// 원본: Apps/Web/src/domain/server/serverErrors.ts (api-spec 1장)
class ServerErrorsTest {
    @Test
    fun knownWireCodesMapToTheirCode() {
        val wire = listOf(
            "INVALID_PARAMETER", "UNAUTHORIZED", "TOO_MANY_ATTEMPTS", "INTERNAL_ERROR", "VAULT_TOO_LARGE",
            "VAULT_NOT_FOUND", "VAULT_CONFLICT", "AUTH_FAILED", "EMAIL_ALREADY_REGISTERED",
            "REGISTRATION_DISABLED", "ORIGIN_NOT_ALLOWED",
        )
        wire.forEach { assertEquals(it, ServerErrorCode.fromWire(it).name) }
    }

    @Test
    fun unknownOrMissingWireCodeBecomesUnknown() {
        assertEquals(ServerErrorCode.UNKNOWN, ServerErrorCode.fromWire("SOMETHING_NEW"))
        assertEquals(ServerErrorCode.UNKNOWN, ServerErrorCode.fromWire(null))
        // 서버가 보낸 "UNKNOWN"도 그대로 UNKNOWN이다
        assertEquals(ServerErrorCode.UNKNOWN, ServerErrorCode.fromWire("UNKNOWN"))
    }

    @Test
    fun serverErrorKeepsStatusCodeAndRetryAfter() {
        val error = ServerError(429, ServerErrorCode.TOO_MANY_ATTEMPTS, retryAfterSeconds = 30)
        assertEquals(429, error.status)
        assertEquals(30, error.retryAfterSeconds)
        assertEquals("서버 오류 429 TOO_MANY_ATTEMPTS", error.message)
        assertNull(ServerError(500, ServerErrorCode.INTERNAL_ERROR).retryAfterSeconds)
    }

    @Test
    fun allServerFailuresShareOneBaseType() {
        // 화면은 ServerFailure 하나로 받고 종류별로 나눈다
        val failures: List<ServerFailure> = listOf(
            ServerError(401, ServerErrorCode.UNAUTHORIZED),
            NetworkError(),
            UnexpectedResponseError("revision 없음"),
        )
        assertIs<NetworkError>(failures[1])
        assertEquals("서버에 연결할 수 없음", failures[1].message)
    }
}
