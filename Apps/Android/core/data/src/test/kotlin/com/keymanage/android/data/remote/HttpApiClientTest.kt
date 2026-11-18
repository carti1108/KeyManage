package com.keymanage.android.data.remote

import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import com.keymanage.android.domain.remote.ApiError
import com.keymanage.android.domain.remote.ServerErrorCode
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** JDK 내장 HTTP 서버로 어댑터의 요청·응답 형식을 확인한다. */
class HttpApiClientTest {
    private data class Recorded(val method: String, val path: String, val auth: String?, val body: String)

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val requests = mutableListOf<Recorded>()
    private var status = 200
    private var responseBody = "{}"
    private val responseHeaders = mutableMapOf<String, String>()

    init {
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            requests += Recorded(exchange.requestMethod, exchange.requestURI.path, exchange.requestHeaders.getFirst("Authorization"), body)
            responseHeaders.forEach { (k, v) -> exchange.responseHeaders.add(k, v) }
            val bytes = responseBody.toByteArray(Charsets.UTF_8)
            if (status == 204) {
                exchange.sendResponseHeaders(204, -1)
            } else {
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        server.start()
    }

    private val client = HttpApiClient("http://127.0.0.1:${server.address.port}")
    private val payload = EncryptedVaultPayload(1, "AES-256-GCM", "aXY=", "Y3Q=", "dGFn")

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun `볼트 조회는 토큰을 붙여 GET하고 revision과 페이로드를 읽는다`() {
        // Given
        responseBody = """{"revision":7,"payload":${payload.canonicalJson()}}"""

        // When
        val vault = client.fetchVault("tok")

        // Then
        assertEquals(Recorded("GET", "/api/v1/vault", "Bearer tok", ""), requests.single())
        assertEquals(7, vault?.revision)
        assertEquals(payload, vault?.payload)
    }

    @Test
    fun `볼트가 없으면(404 VAULT_NOT_FOUND) null`() {
        status = 404
        responseBody = """{"code":"VAULT_NOT_FOUND","message":"저장된 볼트가 없습니다."}"""
        assertNull(client.fetchVault("tok"))
    }

    @Test
    fun `볼트 저장은 expectedRevision과 페이로드 객체를 보낸다`() {
        // Given
        responseBody = """{"revision":8}"""

        // When
        val revision = client.saveVault("tok", 7, payload)

        // Then
        val sent = JSONObject(requests.single().body)
        assertEquals(8, revision)
        assertEquals(7, sent.getInt("expectedRevision"))
        assertEquals("Y3Q=", sent.getJSONObject("payload").getString("ciphertext"))
    }

    @Test
    fun `409는 VAULT_CONFLICT 서버 오류로 올린다`() {
        status = 409
        responseBody = """{"code":"VAULT_CONFLICT","message":"다른 기기에서 볼트가 먼저 변경되었습니다.","currentRevision":9}"""
        val error = assertFailsWith<ApiError.Server> { client.saveVault("tok", 7, payload) }
        assertEquals(409, error.status)
        assertEquals(ServerErrorCode.VAULT_CONFLICT, error.code)
    }

    @Test
    fun `429는 Retry-After 초를 담는다`() {
        status = 429
        responseHeaders["Retry-After"] = "30"
        responseBody = """{"code":"TOO_MANY_ATTEMPTS","message":"로그인 시도가 너무 많습니다."}"""
        val error = assertFailsWith<ApiError.Server> { client.login("a@b.c", ByteArray(32)) }
        assertEquals(ServerErrorCode.TOO_MANY_ATTEMPTS, error.code)
        assertEquals(30L, error.retryAfterSeconds)
    }

    @Test
    fun `로그인은 토큰 없이 인증 해시를 Base64로 보내고 세션을 읽는다`() {
        // Given
        responseBody = """{"accessToken":"new","expiresAt":"2026-11-27T11:20:00Z"}"""

        // When
        val session = client.login("a@b.c", ByteArray(32) { 1 })

        // Then
        val request = requests.single()
        assertNull(request.auth)
        assertEquals("/api/v1/auth/login", request.path)
        assertEquals("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=", JSONObject(request.body).getString("authHash"))
        assertEquals(Instant.parse("2026-11-27T11:20:00Z"), session.expiresAt)
    }

    @Test
    fun `prelogin 응답의 salt를 바이트로 읽는다`() {
        responseBody = """{"kdf":"PBKDF2-HMAC-SHA256","iterations":600000,"salt":"AAECAwQFBgcICQoLDA0ODw=="}"""
        val info = client.prelogin("a@b.c")
        assertEquals(600000, info.iterations)
        assertContentEquals(ByteArray(16) { it.toByte() }, info.salt)
    }

    @Test
    fun `로그아웃 204 응답은 본문 없이 끝난다`() {
        status = 204
        client.logout("tok")
        assertEquals(Recorded("POST", "/api/v1/auth/logout", "Bearer tok", ""), requests.single())
    }

    @Test
    fun `명세 형식이 아닌 응답은 InvalidResponse`() {
        responseBody = """{"revision":"8"}"""
        assertFailsWith<ApiError.InvalidResponse> { client.saveVault("tok", 7, payload) }
    }

    @Test
    fun `원격 서버는 HTTPS만 허용한다`() {
        assertFailsWith<ApiError.InsecureServer> { HttpApiClient("http://example.com") }
        HttpApiClient("https://example.com")
        HttpApiClient("http://localhost:8080")
    }

    @Test
    fun `연결할 수 없으면 Network 오류`() {
        val port = server.address.port
        server.stop(0)
        assertFailsWith<ApiError.Network> { HttpApiClient("http://127.0.0.1:$port", timeoutMillis = 1_000).fetchVault("tok") }
    }
}
