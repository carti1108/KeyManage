package com.keymanage.serverhttp

import com.keymanage.domain.auth.ChangePasswordRequest
import com.keymanage.domain.auth.ChangedPassword
import com.keymanage.domain.auth.DeleteAccountRequest
import com.keymanage.domain.auth.UnlockKdf
import com.keymanage.domain.auth.VaultReplacement
import com.keymanage.domain.crypto.EncryptedVaultPayload
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.server.UnexpectedResponseError
import com.keymanage.domain.vault.RemoteVault
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// 원본: Apps/Web/src/adapters/http/httpAuthApi.test.ts, httpVaultApi.test.ts

private val payload = EncryptedVaultPayload(1, "AES-256-GCM", "aXY=", "Y3Q=", "dGFn")
private const val PAYLOAD_JSON = """{"version":1,"cipher":"AES-256-GCM","iv":"aXY=","ciphertext":"Y3Q=","tag":"dGFn"}"""

private val changeRequest = ChangePasswordRequest(
    currentAuthHash = "Y3VycmVudA==",
    newAuthHash = "bmV3",
    kdf = UnlockKdf(salt = "c2FsdA==", iterations = 600_000),
    vault = VaultReplacement(expectedRevision = 7, payload = payload),
)

private fun path(call: Recorded) = call.path

class ChangePasswordAdapterTest {
    @Test
    fun sendsEverythingInOneRequestAndReceivesNewTokenAndRevision() = runTest {
        val fake = Fake { json(200, """{"accessToken":"new-token","expiresAt":"2026-12-02T11:20:00Z","revision":8}""") }

        assertEquals(ChangedPassword("new-token", "2026-12-02T11:20:00Z", 8), HttpAuthApi(fake.client()).changePassword("tok", changeRequest))
        val call = fake.calls[0]
        assertEquals("/api/v1/auth/change-password", path(call))
        assertEquals("POST", call.method)
        assertEquals("Bearer tok", call.header(HttpHeaders.Authorization))
        val expected = """{"currentAuthHash":"Y3VycmVudA==","newAuthHash":"bmV3","kdf":{"iterations":600000,"salt":"c2FsdA=="},"vault":{"expectedRevision":7,"payload":$PAYLOAD_JSON}}"""
        assertEquals(Json.parseToJsonElement(expected), call.bodyJson)
    }

    @Test
    fun authFailedConflictAndUnauthorizedAreServerErrors() = runTest {
        for ((status, code) in listOf(401 to ServerErrorCode.AUTH_FAILED, 409 to ServerErrorCode.VAULT_CONFLICT, 401 to ServerErrorCode.UNAUTHORIZED)) {
            val fake = Fake { json(status, """{"code":"${code.name}","message":"m","timestamp":"t"}""") }

            val error = assertFailsWith<ServerError> { HttpAuthApi(fake.client()).changePassword("tok", changeRequest) }

            assertEquals(status, error.status)
            assertEquals(code, error.code)
            assertNull(error.retryAfterSeconds)
        }
    }

    @Test
    fun tooManyAttemptsCarriesRetryAfter() = runTest {
        val fake = Fake { json(429, """{"code":"TOO_MANY_ATTEMPTS","message":"m","timestamp":"t"}""", mapOf(HttpHeaders.RetryAfter to "900")) }

        val error = assertFailsWith<ServerError> { HttpAuthApi(fake.client()).changePassword("tok", changeRequest) }

        assertEquals(ServerErrorCode.TOO_MANY_ATTEMPTS, error.code)
        assertEquals(900, error.retryAfterSeconds)
    }

    @Test
    fun rejectsResponseWithoutTokenOrNonNegativeIntegerRevision() = runTest {
        for (body in listOf(
            """{"accessToken":"","expiresAt":"e","revision":1}""",
            """{"accessToken":"t","expiresAt":"e"}""",
            """{"accessToken":"t","expiresAt":"e","revision":-1}""",
            """{"accessToken":"t","expiresAt":"e","revision":1.5}""",
        )) {
            val fake = Fake { json(200, body) }

            assertFailsWith<UnexpectedResponseError>(body) { HttpAuthApi(fake.client()).changePassword("tok", changeRequest) }
        }
    }
}

class DeleteAccountAdapterTest {
    private val deletion = DeleteAccountRequest("YXV0aA==")

    @Test
    fun sendsAuthHashWithTokenAndEndsOnNoContent() = runTest {
        val fake = Fake { respond(ByteReadChannel.Empty, HttpStatusCode.NoContent) }

        HttpAuthApi(fake.client()).deleteAccount("tok", deletion)

        val call = fake.calls[0]
        assertEquals("/api/v1/auth/delete-account", path(call))
        assertEquals("POST", call.method)
        assertEquals("Bearer tok", call.header(HttpHeaders.Authorization))
        assertEquals(Json.parseToJsonElement("""{"authHash":"YXV0aA=="}"""), call.bodyJson)
    }

    @Test
    fun authFailedAndUnauthorizedAreServerErrors() = runTest {
        for (code in listOf(ServerErrorCode.AUTH_FAILED, ServerErrorCode.UNAUTHORIZED)) {
            val fake = Fake { json(401, """{"code":"${code.name}","message":"m","timestamp":"t"}""") }

            val error = assertFailsWith<ServerError> { HttpAuthApi(fake.client()).deleteAccount("tok", deletion) }

            assertEquals(401, error.status)
            assertEquals(code, error.code)
        }
    }

    @Test
    fun tooManyAttemptsCarriesRetryAfter() = runTest {
        val fake = Fake { json(429, """{"code":"TOO_MANY_ATTEMPTS","message":"m","timestamp":"t"}""", mapOf(HttpHeaders.RetryAfter to "900")) }

        val error = assertFailsWith<ServerError> { HttpAuthApi(fake.client()).deleteAccount("tok", deletion) }

        assertEquals(ServerErrorCode.TOO_MANY_ATTEMPTS, error.code)
        assertEquals(900, error.retryAfterSeconds)
    }
}

class VaultAdapterTest {
    @Test
    fun getSendsTokenAndReceivesRevisionAndPayload() = runTest {
        val fake = Fake { json(200, """{"revision":7,"payload":$PAYLOAD_JSON}""") }

        assertEquals(RemoteVault(7, payload), HttpVaultApi(fake.client(), "tok").get())
        val call = fake.calls[0]
        assertEquals("/api/v1/vault", path(call))
        assertEquals("GET", call.method)
        assertEquals("Bearer tok", call.header(HttpHeaders.Authorization))
    }

    @Test
    fun vaultNotFoundIsNull() = runTest {
        val fake = Fake { json(404, """{"code":"VAULT_NOT_FOUND","message":"m","timestamp":"t"}""") }

        assertNull(HttpVaultApi(fake.client(), "tok").get())
    }

    @Test
    fun saveSendsExpectedRevisionAndPayloadAndReceivesNewRevision() = runTest {
        val fake = Fake { json(200, """{"revision":8}""") }

        assertEquals(8, HttpVaultApi(fake.client(), "tok").save(7, payload))
        assertEquals(Json.parseToJsonElement("""{"expectedRevision":7,"payload":$PAYLOAD_JSON}"""), fake.calls[0].bodyJson)
    }

    @Test
    fun conflictIsServerError() = runTest {
        val fake = Fake { json(409, """{"code":"VAULT_CONFLICT","message":"m","currentRevision":9,"timestamp":"t"}""") }

        val error = assertFailsWith<ServerError> { HttpVaultApi(fake.client(), "tok").save(7, payload) }
        assertEquals(ServerErrorCode.VAULT_CONFLICT, error.code)
    }

    @Test
    fun rejectsBadRevisionOrNonObjectPayload() = runTest {
        for (body in listOf(
            """{"revision":"7","payload":$PAYLOAD_JSON}""",
            """{"revision":-1,"payload":$PAYLOAD_JSON}""",
            """{"revision":1.5,"payload":$PAYLOAD_JSON}""",
            """{"revision":7,"payload":"x"}""",
        )) {
            val fake = Fake { json(200, body) }

            assertFailsWith<UnexpectedResponseError>(body) { HttpVaultApi(fake.client(), "tok").get() }
        }
        val fake = Fake { json(200, """{"revision":"x"}""") }
        assertFailsWith<UnexpectedResponseError> { HttpVaultApi(fake.client(), "tok").save(0, payload) }
    }
}
