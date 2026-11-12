package com.keymanage.serverhttp

import com.keymanage.domain.auth.LoginRequest
import com.keymanage.domain.auth.RegisterRequest
import com.keymanage.domain.auth.Session
import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.server.UnexpectedResponseError
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

// 원본: Apps/Web/src/adapters/http/apiClient.test.ts

class BaseUrlTest {
    @Test
    fun emptyMeansSameOrigin() {
        assertEquals("", resolveBaseUrl(null))
        assertEquals("", resolveBaseUrl(""))
    }

    @Test
    fun httpsAndLocalhostHttpKeepOriginOnly() {
        assertEquals("https://vault.example.com", resolveBaseUrl("https://vault.example.com/"))
        assertEquals("http://localhost:8080", resolveBaseUrl("http://localhost:8080"))
        assertEquals("http://127.0.0.1:8080", resolveBaseUrl("http://127.0.0.1:8080"))
    }

    @Test
    fun rejectionMessageDoesNotContainRawUrl() {
        val error = assertFailsWith<InvalidBaseUrlException> { resolveBaseUrl("https://user:secret@a.example") }
        assertFalse(error.message.orEmpty().contains("secret"))
    }

    @Test
    fun rejectsNonLocalHttpPathQueryAndBrokenUrls() {
        for (bad in listOf("http://vault.example.com", "https://vault.example.com/api", "https://a.example?x=1", "https://*.example.com", "https://user:pw@a.example", "ftp://a.example", "not a url")) {
            assertFailsWith<InvalidBaseUrlException>(bad) { resolveBaseUrl(bad) }
        }
    }
}

class ApiClientRequestTest {
    @Test
    fun sendsJsonBodyAndBearerTokenUnderApiV1() = runTest {
        val fake = Fake { json(200, """{"ok":true}""") }

        fake.client("https://vault.example.com").request(HttpMethod.Post, "/vault", JsonObject(mapOf("a" to JsonPrimitive(1))), "tok")

        val call = fake.calls[0]
        assertEquals("https://vault.example.com/api/v1/vault", call.url)
        assertEquals("POST", call.method)
        assertEquals("""{"a":1}""", call.bodyText)
        assertEquals("Bearer tok", call.header(HttpHeaders.Authorization))
        assertEquals("application/json; charset=UTF-8", call.header(HttpHeaders.ContentType))
        // 응답이 없을 때 무한정 기다리지 않는다
        assertEquals(30_000L, call.request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis)
    }

    @Test
    fun errorResponseBecomesServerErrorWithCodeAndStatus() = runTest {
        val cases = listOf(
            401 to ServerErrorCode.AUTH_FAILED,
            409 to ServerErrorCode.EMAIL_ALREADY_REGISTERED,
            403 to ServerErrorCode.REGISTRATION_DISABLED,
            403 to ServerErrorCode.ORIGIN_NOT_ALLOWED,
            500 to ServerErrorCode.INTERNAL_ERROR,
        )
        for ((status, code) in cases) {
            val fake = Fake { json(status, errorBody(code.name)) }

            val error = assertFailsWith<ServerError> { fake.client().request(HttpMethod.Post, "/auth/login") }

            assertEquals(status, error.status)
            assertEquals(code, error.code)
        }
    }

    @Test
    fun tooManyRequestsCarriesRetryAfterSeconds() = runTest {
        val fake = Fake { json(429, errorBody("TOO_MANY_ATTEMPTS"), mapOf(HttpHeaders.RetryAfter to "42")) }

        val error = assertFailsWith<ServerError> { fake.client().request(HttpMethod.Post, "/auth/login") }

        assertEquals(ServerErrorCode.TOO_MANY_ATTEMPTS, error.code)
        assertEquals(42, error.retryAfterSeconds)
    }

    @Test
    fun unknownCodeOrNonJsonErrorIsUnknown() = runTest {
        val responses = listOf(
            Fake { json(418, errorBody("TEAPOT")) },
            Fake { respond("<html>Bad Gateway</html>", HttpStatusCode.BadGateway) },
        )
        for (fake in responses) {
            val error = assertFailsWith<ServerError> { fake.client().request(HttpMethod.Get, "/vault") }
            assertEquals(ServerErrorCode.UNKNOWN, error.code)
        }
    }

    @Test
    fun unreachableServerBecomesNetworkError() = runTest {
        val fake = Fake { throw IllegalStateException("Failed to fetch") }

        assertFailsWith<NetworkError> { fake.client().request(HttpMethod.Get, "/vault") }
    }

    @Test
    fun timeoutWhileReadingBodyBecomesNetworkError() = runTest {
        val fake = Fake { request ->
            @Suppress("OPT_IN_USAGE")
            val body = GlobalScope.writer { throw HttpRequestTimeoutException(request.url.toString(), 30_000) }.channel
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        assertFailsWith<NetworkError> { fake.client().request(HttpMethod.Get, "/vault") }
    }

    @Test
    fun nonJsonSuccessBecomesUnexpectedResponse() = runTest {
        val fake = Fake { respond("<html></html>", HttpStatusCode.OK) }

        assertFailsWith<UnexpectedResponseError> { fake.client().request(HttpMethod.Get, "/vault") }
    }

    @Test
    fun noContentEndsWithoutBody() = runTest {
        val fake = Fake { respond(ByteReadChannel.Empty, HttpStatusCode.NoContent) }

        assertNull(fake.client().request(HttpMethod.Post, "/auth/logout"))
    }

    // 원본의 fetch는 redirect: 'error'라 리다이렉트를 따라가지 않고 연결 실패로 끝났다. 인증 해시가 담긴 본문이 다른 주소로 가지 않는다
    @Test
    fun redirectIsNotFollowedAndIsNetworkError() = runTest {
        val fake = Fake { respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://evil.example/api/v1/auth/login")) }

        assertFailsWith<NetworkError> { fake.client("https://vault.example.com").request(HttpMethod.Post, "/auth/login", JsonObject(emptyMap())) }
        assertEquals(1, fake.calls.size)
    }
}

class AuthAdapterBasicsTest {
    @Test
    fun preloginChecksResponseShape() = runTest {
        val fake = Fake { json(200, """{"kdf":"PBKDF2-HMAC-SHA256","iterations":"600000","salt":"AAAA"}""") }

        assertFailsWith<UnexpectedResponseError> { HttpAuthApi(fake.client()).prelogin("a@b.cd") }
    }

    @Test
    fun loginTakesOnlySession() = runTest {
        val fake = Fake { json(200, """{"accessToken":"tok","expiresAt":"2026-11-28T00:00:00Z","extra":1}""") }

        val session = HttpAuthApi(fake.client()).login(LoginRequest("a@b.cd", "h"))

        assertEquals("/api/v1/auth/login", fake.calls[0].path)
        assertEquals(Session("tok", "2026-11-28T00:00:00Z"), session)
    }

    @Test
    fun rejectsResponseWithoutToken() = runTest {
        val fake = Fake { json(201, """{"expiresAt":"2026-11-28T00:00:00Z"}""") }

        assertFailsWith<UnexpectedResponseError> { HttpAuthApi(fake.client()).register(RegisterRequest("a@b.cd", "h", "s", 600_000)) }
    }

    @Test
    fun logoutSendsTokenInAuthorizationHeader() = runTest {
        val fake = Fake { respond(ByteReadChannel.Empty, HttpStatusCode.NoContent) }

        HttpAuthApi(fake.client()).logout("tok")

        assertEquals("/api/v1/auth/logout", fake.calls[0].path)
        assertEquals("Bearer tok", fake.calls[0].header(HttpHeaders.Authorization))
    }
}
