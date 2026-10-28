package com.keymanage.back.api.vault

import com.keymanage.back.api.ApiTestFixture
import com.keymanage.back.api.error.RequestBodyLimitFilter
import com.keymanage.back.domain.vault.SaveVaultUseCase
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

/** 로그인한 계정의 볼트 조회·저장과 오류 변환 (api-spec 2.3·2.4) */
class VaultApiTests {

    private val payload = """{"version":1,"cipher":"AES-256-GCM","ciphertext":"k7Z9X","updatedAt":"2026-10-27T11:20:00Z"}"""

    private val fixture = ApiTestFixture()
    private val token = fixture.registerAndGetToken()

    private fun saveRequest(expectedRevision: Long, payload: String = this.payload) =
        """{"expectedRevision":$expectedRevision,"payload":$payload}"""

    private fun save(body: String, token: String = this.token) = fixture.mockMvc.perform(
        post("/api/v1/vault").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(body),
    )

    private fun load(token: String = this.token) =
        fixture.mockMvc.perform(get("/api/v1/vault").header("Authorization", "Bearer $token"))

    private fun ciphertextOf(bytes: Int) = """{"ciphertext":"${"a".repeat(bytes)}"}"""

    @Test
    fun `첫 저장은 revision 1을 돌려준다`() {
        save(saveRequest(0))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"revision":1}""", JsonCompareMode.STRICT))
    }

    @Test
    fun `저장한 볼트를 조회하면 revision과 같은 페이로드를 돌려준다`() {
        save(saveRequest(0))

        load()
            .andExpect(status().isOk)
            .andExpect(content().json("""{"revision":1,"payload":$payload}""", JsonCompareMode.STRICT))
    }

    @Test
    fun `다른 계정은 내 볼트를 볼 수도 덮어쓸 수도 없다`() {
        save(saveRequest(0))
        val other = fixture.registerAndGetToken("other@example.com")

        load(other).andExpect(status().isNotFound)
        save(saveRequest(0, """{"ciphertext":"other"}"""), other).andExpect(status().isOk)

        load().andExpect(content().json("""{"revision":1,"payload":$payload}""", JsonCompareMode.STRICT))
    }

    @Test
    fun `저장된 볼트가 없으면 404 VAULT_NOT_FOUND`() {
        load()
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("VAULT_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("저장된 볼트가 없습니다."))
            .andExpect(jsonPath("$.timestamp").isNotEmpty)
            .andExpect(jsonPath("$.currentRevision").doesNotExist())
    }

    @Test
    fun `기대 revision이 다르면 409와 현재 revision을 돌려준다`() {
        save(saveRequest(0))
        save(saveRequest(1))

        save(saveRequest(1))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("VAULT_CONFLICT"))
            .andExpect(jsonPath("$.message").value("다른 기기에서 볼트가 먼저 변경되었습니다."))
            .andExpect(jsonPath("$.currentRevision").value(2))
    }

    @Test
    fun `페이로드가 5MB를 넘으면 413 VAULT_TOO_LARGE`() {
        save(saveRequest(0, ciphertextOf(SaveVaultUseCase.MAX_PAYLOAD_BYTES)))
            .andExpect(status().isPayloadTooLarge)
            .andExpect(jsonPath("$.code").value("VAULT_TOO_LARGE"))
            .andExpect(jsonPath("$.message").value("볼트 허용 용량(5MB)을 초과했습니다."))
    }

    @Test
    fun `요청 본문이 6MB를 넘으면 해석하기 전에 413으로 거절한다`() {
        save(saveRequest(0, ciphertextOf(RequestBodyLimitFilter.MAX_BODY_BYTES)))
            .andExpect(status().isPayloadTooLarge)
            .andExpect(jsonPath("$.code").value("VAULT_TOO_LARGE"))

        load().andExpect(status().isNotFound)
    }

    @Test
    fun `Content-Length 없이 6MB를 넘는 본문은 읽는 도중 끊고 413으로 거절한다`() {
        fixture.mockMvc.perform(
            post("/api/v1/vault").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON)
                .content(saveRequest(0, ciphertextOf(RequestBodyLimitFilter.MAX_BODY_BYTES)))
                .with { request -> ChunkedRequest(request) },
        )
            .andExpect(status().isPayloadTooLarge)
            .andExpect(jsonPath("$.code").value("VAULT_TOO_LARGE"))

        load().andExpect(status().isNotFound)
    }

    @Test
    fun `저장 요청 형식이 틀리면 400 INVALID_PARAMETER`() {
        listOf(
            """{"payload":$payload}""",
            """{"expectedRevision":""",
            saveRequest(0, "\"not-an-object\""),
            saveRequest(-1),
        ).forEach { body ->
            save(body)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
        }
    }

    /** chunked 전송처럼 Content-Length를 알 수 없는 요청 */
    private class ChunkedRequest(original: MockHttpServletRequest) :
        MockHttpServletRequest(original.servletContext, original.method, original.requestURI) {

        init {
            contentType = original.contentType
            setContent(original.contentAsByteArray)
            original.headerNames.toList().forEach { name -> original.getHeaders(name).toList().forEach { addHeader(name, it) } }
        }

        override fun getContentLength(): Int = -1

        override fun getContentLengthLong(): Long = -1
    }
}
