package com.keymanage.serverhttp

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

// 원본 테스트의 fakeFetch·json·errorBody. 요청을 기록하고 정해 둔 응답(또는 예외)을 돌려준다

class Recorded(val request: HttpRequestData) {
    val url: String get() = request.url.toString()
    val method: String get() = request.method.value

    /** 같은 출처("") 요청은 대상마다 호스트가 다르게 풀린다(브라우저는 페이지 출처). 경로만 비교한다 */
    val path: String get() = request.url.encodedPath

    fun header(name: String): String? = request.headers[name] ?: request.body.contentType?.takeIf { name == HttpHeaders.ContentType }?.toString()

    val bodyText: String?
        get() = (request.body as? TextContent)?.text ?: (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()

    val bodyJson: JsonElement get() = Json.parseToJsonElement(bodyText ?: error("본문 없음"))
}

class Fake(respond: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
    val calls = mutableListOf<Recorded>()
    val engine = MockEngine { request ->
        calls += Recorded(request)
        respond(request)
    }

    fun client(baseUrl: String = "") = ApiClient(baseUrl, keyManageHttpClient(engine))
}

fun MockRequestHandleScope.json(status: Int, body: String, headers: Map<String, String> = emptyMap()): HttpResponseData =
    respond(
        body,
        HttpStatusCode.fromValue(status),
        headersOf(*(mapOf(HttpHeaders.ContentType to "application/json") + headers).map { (k, v) -> k to listOf(v) }.toTypedArray()),
    )

fun errorBody(code: String) = """{"code":"$code","message":"서버 문구","timestamp":"2026-10-29T00:00:00Z"}"""
