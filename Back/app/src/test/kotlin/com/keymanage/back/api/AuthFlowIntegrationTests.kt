package com.keymanage.back.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertTrue

/** 실제 파일 저장소·Argon2·필터까지 조립된 상태로 가입 -> 볼트 저장·조회 -> 로그아웃 흐름을 확인한다 (SEC-002) */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTests {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private val payload = """{"version":1,"ciphertext":"k7Z9X"}"""

    private fun b64(fill: Int, size: Int) = Base64.getEncoder().encodeToString(ByteArray(size) { fill.toByte() })

    private fun postJson(path: String, body: String, token: String? = null) = mockMvc.perform(
        post(path).contentType(MediaType.APPLICATION_JSON).content(body)
            .apply { if (token != null) header("Authorization", "Bearer $token") },
    )

    private fun register(email: String): String {
        val body = """{"email":"$email","authHash":"${b64(1, 32)}","kdfSalt":"${b64(2, 16)}","kdfIterations":600000}"""
        val response = postJson("/api/v1/auth/register", body).andExpect(status().isCreated).andReturn().response.contentAsString
        return objectMapper.readTree(response)["accessToken"].asText()
    }

    @Test
    fun `가입하고 볼트를 저장·조회한 뒤 로그아웃하면 그 토큰은 더 쓸 수 없다`() {
        val token = register("flow@example.com")

        postJson("/api/v1/vault", """{"expectedRevision":0,"payload":$payload}""", token)
            .andExpect(status().isOk).andExpect(jsonPath("$.revision").value(1))
        mockMvc.perform(get("/api/v1/vault").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"revision":1,"payload":$payload}""", JsonCompareMode.STRICT))

        // 다시 로그인해도 같은 볼트다
        val again = objectMapper.readTree(
            postJson("/api/v1/auth/login", """{"email":"flow@example.com","authHash":"${b64(1, 32)}"}""")
                .andExpect(status().isOk).andReturn().response.contentAsString,
        )["accessToken"].asText()
        mockMvc.perform(get("/api/v1/vault").header("Authorization", "Bearer $again")).andExpect(jsonPath("$.revision").value(1))

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer $token")).andExpect(status().isNoContent)
        mockMvc.perform(get("/api/v1/vault").header("Authorization", "Bearer $token")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `볼트가 파일로 남고 오래된 revision으로 저장하면 409와 현재 revision을 돌려준다`() {
        val token = register("conflict@example.com")
        postJson("/api/v1/vault", """{"expectedRevision":0,"payload":$payload}""", token).andExpect(status().isOk)

        postJson("/api/v1/vault", """{"expectedRevision":0,"payload":$payload}""", token)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("VAULT_CONFLICT"))
            .andExpect(jsonPath("$.currentRevision").value(1))
        assertTrue(Files.list(dataDir.resolve("vaults")).use { files -> files.anyMatch { it.fileName.toString().endsWith(".json") } })
    }

    @Test
    fun `다른 계정은 자기 볼트만 본다`() {
        val mine = register("mine@example.com")
        val other = register("other@example.com")
        postJson("/api/v1/vault", """{"expectedRevision":0,"payload":$payload}""", mine).andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/vault").header("Authorization", "Bearer $other")).andExpect(status().isNotFound)
    }

    @Test
    fun `토큰 없이 볼트 API를 부르면 401`() {
        mockMvc.perform(get("/api/v1/vault")).andExpect(status().isUnauthorized)
    }

    companion object {
        @TempDir
        @JvmStatic
        lateinit var dataDir: Path

        @DynamicPropertySource
        @JvmStatic
        fun storage(registry: DynamicPropertyRegistry) {
            registry.add("keymanage.vault.storage-dir") { dataDir.resolve("vaults").toString() }
            registry.add("keymanage.auth.storage-dir") { dataDir.resolve("auth").toString() }
        }
    }
}
