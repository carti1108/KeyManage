package com.keymanage.back.api.vault

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.keymanage.back.api.error.ApiExceptionHandler
import com.keymanage.back.api.error.RequestBodyLimitFilter
import com.keymanage.back.domain.vault.GetVaultUseCase
import com.keymanage.back.domain.vault.SaveVaultUseCase
import com.keymanage.back.domain.vault.VaultId
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import kotlin.test.Test
import kotlin.test.assertNull

/** 서버를 띄우지 않고 진짜 UseCase에 가짜 저장소를 꽂아 볼트 API의 응답과 오류 변환을 확인한다 */
class VaultApiTests {

    private val vaultId = "550e8400-e29b-41d4-a716-446655440000"
    private val payload = """{"version":1,"kdf":"PBKDF2-HMAC-SHA256","ciphertext":"k7Z9X","updatedAt":"2026-10-27T11:20:00Z"}"""

    private val repository = InMemoryVaultRepository()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(VaultController(GetVaultUseCase(repository), SaveVaultUseCase(repository), jacksonObjectMapper()))
        .setControllerAdvice(ApiExceptionHandler())
        .addFilters<StandaloneMockMvcBuilder>(RequestBodyLimitFilter())
        .build()

    private fun saveRequest(expectedRevision: Long, payload: String = this.payload, id: String = vaultId) =
        """{"vaultId":"$id","expectedRevision":$expectedRevision,"payload":$payload}"""

    private fun save(body: String) = mockMvc.perform(post("/api/v1/vault").contentType(MediaType.APPLICATION_JSON).content(body))

    private fun load(id: String = vaultId) = mockMvc.perform(get("/api/v1/vault").header("X-Vault-ID", id))

    private fun ciphertextOf(bytes: Int) = """{"ciphertext":"${"a".repeat(bytes)}"}"""

    @Test
    fun `첫 저장은 revision 1을 돌려준다`() {
        save(saveRequest(0))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"revision":1}""", JsonCompareMode.STRICT))
    }

    @Test
    fun `저장한 볼트를 조회하면 볼트ID, revision, 같은 페이로드를 돌려준다`() {
        save(saveRequest(0))

        load()
            .andExpect(status().isOk)
            .andExpect(content().json("""{"vaultId":"$vaultId","revision":1,"payload":$payload}""", JsonCompareMode.STRICT))
    }

    @Test
    fun `대문자 볼트ID로 조회해도 같은 볼트를 소문자 ID로 돌려준다`() {
        save(saveRequest(0))

        load(vaultId.uppercase())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.vaultId").value(vaultId))
    }

    @Test
    fun `저장된 볼트가 없으면 404 VAULT_NOT_FOUND`() {
        load()
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("VAULT_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("저장된 볼트가 없습니다."))
            .andExpect(jsonPath("$.timestamp").isNotEmpty)
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
    fun `충돌이 아닌 오류 응답에는 currentRevision이 없다`() {
        load().andExpect(jsonPath("$.currentRevision").doesNotExist())
    }

    @Test
    fun `페이로드가 5MB를 넘으면 413 VAULT_TOO_LARGE`() {
        // 본문 상한(6MB)보다는 작고 볼트 상한(5MB)보다는 큰 경우: 도메인이 거절한다
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

        assertNull(repository.find(VaultId.of(vaultId)))
    }

    @Test
    fun `Content-Length 없이 6MB를 넘는 본문은 읽는 도중 끊고 413으로 거절한다`() {
        val huge = saveRequest(0, ciphertextOf(RequestBodyLimitFilter.MAX_BODY_BYTES))

        mockMvc.perform(
            post("/api/v1/vault").contentType(MediaType.APPLICATION_JSON).content(huge)
                .with { request -> ChunkedRequest(request) },
        )
            .andExpect(status().isPayloadTooLarge)
            .andExpect(jsonPath("$.code").value("VAULT_TOO_LARGE"))

        assertNull(repository.find(VaultId.of(vaultId)))
    }

    @Test
    fun `UUID 형식이 아닌 볼트ID는 400 INVALID_PARAMETER`() {
        load("../../etc/passwd")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
            .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."))
    }

    @Test
    fun `X-Vault-ID 헤더가 없으면 400`() {
        mockMvc.perform(get("/api/v1/vault"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
    }

    @Test
    fun `저장 요청에 필수 필드가 없으면 400`() {
        save("""{"vaultId":"$vaultId","payload":$payload}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
    }

    @Test
    fun `깨진 JSON은 400`() {
        save("""{"vaultId":""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
    }

    @Test
    fun `페이로드가 JSON 객체가 아니면 400`() {
        save(saveRequest(0, "\"not-an-object\""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
    }

    @Test
    fun `기대 revision이 음수면 400`() {
        save(saveRequest(-1))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
    }

    @Test
    fun `저장 요청의 볼트ID가 UUID 형식이 아니면 400`() {
        save(saveRequest(0, id = "not-a-uuid"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
    }

    /** chunked 전송처럼 Content-Length를 알 수 없는 요청 */
    private class ChunkedRequest(original: MockHttpServletRequest) :
        MockHttpServletRequest(original.servletContext, original.method, original.requestURI) {

        init {
            contentType = original.contentType
            setContent(original.contentAsByteArray)
        }

        override fun getContentLength(): Int = -1

        override fun getContentLengthLong(): Long = -1
    }
}
