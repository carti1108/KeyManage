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
import kotlin.test.assertEquals

/** 실제 파일 저장소·Argon2·필터까지 조립된 상태로 비밀번호 변경 흐름을 확인한다 (SEC-004, api-spec 2.5.5) */
@SpringBootTest
@AutoConfigureMockMvc
class ChangePasswordFlowIntegrationTests {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private val oldPayload = """{"version":1,"ciphertext":"old"}"""
    private val newPayload = """{"version":1,"ciphertext":"new"}"""

    private fun b64(fill: Int, size: Int) = Base64.getEncoder().encodeToString(ByteArray(size) { fill.toByte() })

    private fun postJson(path: String, body: String, token: String? = null) = mockMvc.perform(
        post(path).contentType(MediaType.APPLICATION_JSON).content(body)
            .apply { if (token != null) header("Authorization", "Bearer $token") },
    )

    private fun tokenOf(body: String) = objectMapper.readTree(body)["accessToken"].asText()

    private fun login(email: String, authHashFill: Int) = postJson("/api/v1/auth/login", """{"email":"$email","authHash":"${b64(authHashFill, 32)}"}""")

    @Test
    fun `비밀번호를 바꾸면 새 토큰으로 새 볼트를 받고 옛 토큰과 옛 인증 해시는 거절된다`() {
        val email = "change@example.com"
        val token = tokenOf(
            postJson("/api/v1/auth/register", """{"email":"$email","authHash":"${b64(1, 32)}","kdfSalt":"${b64(2, 16)}","kdfIterations":600000}""")
                .andExpect(status().isCreated).andReturn().response.contentAsString,
        )
        val otherDevice = tokenOf(login(email, 1).andExpect(status().isOk).andReturn().response.contentAsString)
        postJson("/api/v1/vault", """{"expectedRevision":0,"payload":$oldPayload}""", token).andExpect(status().isOk)

        val body = """{"currentAuthHash":"${b64(1, 32)}","newAuthHash":"${b64(3, 32)}",""" +
            """"kdf":{"iterations":600000,"salt":"${b64(4, 16)}"},"vault":{"expectedRevision":1,"payload":$newPayload}}"""
        val changed = postJson("/api/v1/auth/change-password", body, token)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.revision").value(2))
            .andReturn().response.contentAsString
        val newToken = tokenOf(changed)

        mockMvc.perform(get("/api/v1/vault").header("Authorization", "Bearer $newToken"))
            .andExpect(content().json("""{"revision":2,"payload":$newPayload}""", JsonCompareMode.STRICT))
        listOf(token, otherDevice).forEach {
            mockMvc.perform(get("/api/v1/vault").header("Authorization", "Bearer $it")).andExpect(status().isUnauthorized)
        }
        postJson("/api/v1/auth/prelogin", """{"email":"$email"}""").andExpect(jsonPath("$.salt").value(b64(4, 16)))
        login(email, 1).andExpect(status().isUnauthorized)
        login(email, 3).andExpect(status().isOk)
        // 옛 볼트 파일은 지워지고 새 볼트 파일 하나만 남는다
        assertEquals(1, Files.list(dataDir.resolve("vaults")).use { files -> files.count() })
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
