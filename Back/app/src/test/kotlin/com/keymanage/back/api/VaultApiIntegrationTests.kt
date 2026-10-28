package com.keymanage.back.api

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
import kotlin.test.Test
import kotlin.test.assertTrue

/** 실제 파일 저장소까지 조립된 상태로 저장 -> 조회 -> 오래된 revision 저장 흐름을 확인한다 */
@SpringBootTest
@AutoConfigureMockMvc
class VaultApiIntegrationTests {

    @Autowired
    lateinit var mockMvc: MockMvc

    private val vaultId = "550e8400-e29b-41d4-a716-446655440000"
    private val payload = """{"version":1,"ciphertext":"k7Z9X"}"""

    private fun save(expectedRevision: Long) = mockMvc.perform(
        post("/api/v1/vault").contentType(MediaType.APPLICATION_JSON)
            .content("""{"vaultId":"$vaultId","expectedRevision":$expectedRevision,"payload":$payload}"""),
    )

    @Test
    fun `저장한 볼트가 파일로 남고 조회되며 오래된 revision으로 저장하면 409`() {
        save(0).andExpect(status().isOk).andExpect(jsonPath("$.revision").value(1))
        assertTrue(Files.exists(storageDir.resolve("$vaultId.json")))

        mockMvc.perform(get("/api/v1/vault").header("X-Vault-ID", vaultId))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"vaultId":"$vaultId","revision":1,"payload":$payload}""", JsonCompareMode.STRICT))

        save(0).andExpect(status().isConflict).andExpect(jsonPath("$.currentRevision").value(1))
    }

    companion object {
        @TempDir
        @JvmStatic
        lateinit var storageDir: Path

        @DynamicPropertySource
        @JvmStatic
        fun storage(registry: DynamicPropertyRegistry) {
            registry.add("keymanage.vault.storage-dir") { storageDir.toString() }
        }
    }
}
