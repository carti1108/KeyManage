package com.keymanage.serverhttp

import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.server.UnexpectedResponseError
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

// Ktor 기반 API 클라이언트 (api-spec 1장, ADR-0026). 원본: Apps/Web/src/adapters/http/apiClient.ts

private const val API_PREFIX = "/api/v1"

// 응답이 없을 때 무한정 기다리지 않는다. 볼트 업로드(최대 5MB)도 이 안에 끝나야 한다
private const val TIMEOUT_MS = 30_000L
private val JSON_UTF8 = ContentType.parse("application/json; charset=UTF-8")
private val RETRY_AFTER = Regex("""^\d+$""")
private val REDIRECT = 300..399

private fun HttpClientConfig<*>.keyManageDefaults() {
    // 오류 응답은 직접 ServerError로 바꾼다
    expectSuccess = false
    // 리다이렉트를 따라가면 인증 해시가 담긴 본문이 다른 주소로 갈 수 있다
    followRedirects = false
    install(HttpTimeout) { requestTimeoutMillis = TIMEOUT_MS }
    // 쿠키 저장 플러그인(HttpCookies)은 설치하지 않는다. 인증은 Bearer 토큰만 쓴다
}

/** 테스트(MockEngine)와 엔진을 직접 고르는 곳에서 쓴다 */
fun keyManageHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) { keyManageDefaults() }

/** 대상마다 의존성에 든 엔진(JS fetch, OkHttp, Darwin, JVM CIO)을 쓴다 */
fun keyManageHttpClient(): HttpClient = HttpClient { keyManageDefaults() }

class ApiClient(
    /** resolveBaseUrl을 거친 값 */
    private val baseUrl: String,
    private val http: HttpClient,
) {
    /**
     * @return 성공 응답의 JSON. 204면 null
     * @throws ServerError, NetworkError, UnexpectedResponseError
     */
    suspend fun request(method: HttpMethod, path: String, body: JsonElement? = null, accessToken: String? = null): JsonElement? {
        val response: HttpResponse = try {
            http.request("$baseUrl$API_PREFIX$path") {
                this.method = method
                header(HttpHeaders.Accept, "application/json")
                if (!accessToken.isNullOrEmpty()) header(HttpHeaders.Authorization, "Bearer $accessToken")
                if (body != null) setBody(TextContent(body.toString(), JSON_UTF8))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw NetworkError()
        }

        // 원본 fetch는 redirect: 'error'라 리다이렉트를 연결 실패로 끝냈다
        if (response.status.value in REDIRECT) throw NetworkError()
        if (!response.status.isSuccess()) throw toServerError(response)
        if (response.status == HttpStatusCode.NoContent) return null
        val text = try {
            response.bodyAsText()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // 제한 시간은 본문을 받는 동안에도 적용된다. 느린 회선을 형식 오류로 오해하지 않게 구분한다
            throw NetworkError("응답을 받는 중 시간이 초과됨")
        }
        return parseStrictJson(text) ?: throw UnexpectedResponseError("JSON이 아닌 성공 응답: ${response.status.value}")
    }
}

// kotlinx Json은 따옴표 없는 글자(<html> 등)도 값으로 받는다. 원본 response.json()처럼 JSON 문법에 맞지 않으면 거절한다
private val JSON_LITERAL = Regex("""^(true|false|null|-?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?)$""")

private fun parseStrictJson(text: String): JsonElement? {
    val element = try {
        Json.parseToJsonElement(text)
    } catch (_: Exception) {
        return null
    }
    return element.takeIf(::isStrict)
}

private fun isStrict(element: JsonElement): Boolean = when (element) {
    is JsonObject -> element.values.all(::isStrict)
    is JsonArray -> element.all(::isStrict)
    is JsonPrimitive -> element.isString || JSON_LITERAL.matches(element.content)
}

private suspend fun toServerError(response: HttpResponse): ServerError {
    val code = try {
        val raw = (parseStrictJson(response.bodyAsText()) as? JsonObject)?.get("code") as? JsonPrimitive
        if (raw != null && raw.isString) ServerErrorCode.fromWire(raw.content) else ServerErrorCode.UNKNOWN
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // 프록시가 만든 HTML 오류 페이지 등. 코드 없이 상태만 알린다
        ServerErrorCode.UNKNOWN
    }
    return ServerError(response.status.value, code, parseRetryAfter(response.headers[HttpHeaders.RetryAfter]))
}

// 서버는 초 단위 정수만 보낸다 (api-spec 1장). 날짜 형식은 쓰지 않는다
private fun parseRetryAfter(value: String?): Int? = value?.takeIf(RETRY_AFTER::matches)?.toIntOrNull()
