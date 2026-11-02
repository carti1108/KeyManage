package com.keymanage.back.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/** 실제 파일 저장소·Argon2·필터까지 조립된 상태로 계정 삭제 흐름을 확인한다 (SEC-005, api-spec 2.5.6) */
@SpringBootTest
@AutoConfigureMockMvc
class DeleteAccountFlowIntegrationTests {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private fun b64(fill: Int, size: Int) = Base64.getEncoder().encodeToString(ByteArray(size) { fill.toByte() })

    private fun postJson(path: String, body: String, token: String? = null) = mockMvc.perform(
        post(path).contentType(MediaType.APPLICATION_JSON).content(body)
            .apply { if (token != null) header("Authorization", "Bearer $token") },
    )

    private fun tokenOf(body: String) = objectMapper.readTree(body)["accessToken"].asText()

    private fun register(email: String, authHashFill: Int) = postJson(
        "/api/v1/auth/register",
        """{"email":"$email","authHash":"${b64(authHashFill, 32)}","kdfSalt":"${b64(2, 16)}","kdfIterations":600000}""",
    )

    private fun login(email: String, authHashFill: Int) = postJson("/api/v1/auth/login", """{"email":"$email","authHash":"${b64(authHashFill, 32)}"}""")

    private fun filesIn(dir: Path): Long = if (Files.notExists(dir)) 0 else Files.list(dir).use { files -> files.count() }

    @Test
    fun `계정을 지우면 파일이 사라지고 토큰은 거절되며 같은 이메일로 다시 가입할 수 있다`() {
        val email = "delete@example.com"
        val token = tokenOf(register(email, 1).andExpect(status().isCreated).andReturn().response.contentAsString)
        val otherDevice = tokenOf(login(email, 1).andExpect(status().isOk).andReturn().response.contentAsString)
        postJson("/api/v1/vault", """{"expectedRevision":0,"payload":{"ciphertext":"v"}}""", token).andExpect(status().isOk)
        val vaultsBefore = filesIn(dataDir.resolve("vaults"))

        postJson("/api/v1/auth/delete-account", """{"authHash":"${b64(9, 32)}"}""", token)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTH_FAILED"))
        postJson("/api/v1/auth/delete-account", """{"authHash":"${b64(1, 32)}"}""", token)
            .andExpect(status().isNoContent)

        listOf(token, otherDevice).forEach {
            mockMvc.perform(get("/api/v1/vault").header("Authorization", "Bearer $it"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        }
        login(email, 1).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH_FAILED"))
        assertEquals(vaultsBefore - 1, filesIn(dataDir.resolve("vaults")))

        val newToken = tokenOf(register(email, 3).andExpect(status().isCreated).andReturn().response.contentAsString)
        mockMvc.perform(get("/api/v1/vault").header("Authorization", "Bearer $newToken"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("VAULT_NOT_FOUND"))
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
