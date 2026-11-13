package com.keymanage.websession

import com.keymanage.domain.device.DeviceRecordStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// 메모리 서버. 가입 → 볼트 저장 → 잠금 해제에 필요한 요청(api-spec 2.3·2.4·2.5)만 받아 준다. HTTP는 Ktor MockEngine이 막아 준다

internal const val TEST_BASE_URL = "http://localhost:8080"

internal class MemoryDeviceStore : DeviceRecordStore {
    var stored: String? = null

    override suspend fun load(): String? = stored

    override suspend fun save(recordJson: String) {
        stored = recordJson
    }

    override suspend fun clear() {
        stored = null
    }
}

internal class FakeServer {
    var vaultRevision = 0
    var vaultPayload: JsonObject? = null
    var kdf: JsonObject? = null

    /** 설정하면 prelogin이 이 상태로 실패한다 (상태, 코드, Retry-After) */
    var preloginFailure: Triple<Int, String, Int?>? = null
    val calls = mutableListOf<String>()

    val engine = MockEngine { request ->
        calls += "${request.method.value} ${request.url.encodedPath}"
        handle(request)
    }

    private fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath.removePrefix("/api/v1")
        return when {
            request.method == HttpMethod.Post && path == "/auth/register" -> {
                val body = bodyOf(request)
                kdf = JsonObject(mapOf("salt" to body.getValue("kdfSalt"), "iterations" to body.getValue("kdfIterations")))
                reply(201, SESSION)
            }
            request.method == HttpMethod.Post && path == "/auth/prelogin" -> preloginFailure?.let { (status, code, retryAfter) ->
                reply(status, errorBody(code), retryAfter?.let { mapOf(HttpHeaders.RetryAfter to it.toString()) } ?: emptyMap())
            } ?: reply(200, """{"kdf":"PBKDF2-HMAC-SHA256","iterations":${kdf?.get("iterations")},"salt":${kdf?.get("salt")}}""")
            request.method == HttpMethod.Post && path == "/auth/login" -> reply(200, SESSION)
            request.method == HttpMethod.Post && path == "/auth/logout" -> respond("", HttpStatusCode.NoContent)
            request.method == HttpMethod.Get && path == "/vault" -> {
                val payload = vaultPayload
                if (payload == null) reply(404, errorBody("VAULT_NOT_FOUND")) else reply(200, """{"revision":$vaultRevision,"payload":$payload}""")
            }
            request.method == HttpMethod.Post && path == "/vault" -> {
                val body = bodyOf(request)
                if (body.getValue("expectedRevision").jsonPrimitive.int != vaultRevision) {
                    reply(409, errorBody("VAULT_CONFLICT"))
                } else {
                    vaultRevision += 1
                    vaultPayload = body.getValue("payload").jsonObject
                    reply(200, """{"revision":$vaultRevision}""")
                }
            }
            else -> reply(404, errorBody("UNKNOWN"))
        }
    }

    private fun bodyOf(request: HttpRequestData): JsonObject =
        Json.parseToJsonElement((request.body as TextContent).text).jsonObject

    private fun MockRequestHandleScope.reply(status: Int, body: String, headers: Map<String, String> = emptyMap()): HttpResponseData =
        respond(
            body,
            HttpStatusCode.fromValue(status),
            headersOf(*(mapOf(HttpHeaders.ContentType to "application/json") + headers).map { (k, v) -> k to listOf(v) }.toTypedArray()),
        )

    private fun errorBody(code: String) = """{"code":"$code","message":"서버 문구","timestamp":"2026-10-29T00:00:00Z"}"""

    private companion object {
        const val SESSION = """{"accessToken":"token-1","expiresAt":"2999-01-01T00:00:00Z"}"""
    }
}

