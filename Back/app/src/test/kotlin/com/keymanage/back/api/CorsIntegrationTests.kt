package com.keymanage.back.api

import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Path
import kotlin.test.Test

private const val DESKTOP_ORIGIN = "http://tauri.localhost"

/** 설정한 출처(데스크톱 앱)만 브라우저 교차 출처 요청을 허용한다 (ADR-0013) */
@SpringBootTest(properties = ["keymanage.http.allowed-origins=$DESKTOP_ORIGIN"])
@AutoConfigureMockMvc
class CorsIntegrationTests {

    @Autowired
    lateinit var mockMvc: MockMvc

    private fun preflight(origin: String, method: String = "POST") = mockMvc.perform(
        options("/api/v1/vault")
            .header("Origin", origin)
            .header("Access-Control-Request-Method", method)
            .header("Access-Control-Request-Headers", "authorization,content-type"),
    )

    @Test
    fun `허용한 출처의 사전 요청은 토큰 없이도 통과한다`() {
        preflight(DESKTOP_ORIGIN)
            .andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Allow-Origin", DESKTOP_ORIGIN))
    }

    @Test
    fun `허용하지 않은 출처의 사전 요청은 거절한다`() {
        preflight("https://evil.example").andExpect(status().isForbidden)
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
    }

    @Test
    fun `API에서 쓰지 않는 메서드는 사전 요청에서 거절한다`() {
        preflight(DESKTOP_ORIGIN, method = "DELETE").andExpect(status().isForbidden)
    }

    @Test
    fun `오류 응답에도 허용 출처 헤더와 Retry-After 노출 헤더가 붙는다`() {
        mockMvc.perform(
            post("/api/v1/auth/logout").header("Origin", DESKTOP_ORIGIN).contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(header().string("Access-Control-Allow-Origin", DESKTOP_ORIGIN))
            .andExpect(header().string("Access-Control-Expose-Headers", "Retry-After"))
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

/** 기본 설정(허용 출처 없음)에서는 어떤 교차 출처 요청도 허용하지 않는다 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsDefaultIntegrationTests {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `허용 출처를 설정하지 않으면 데스크톱 출처도 거절한다`() {
        mockMvc.perform(
            options("/api/v1/vault")
                .header("Origin", DESKTOP_ORIGIN)
                .header("Access-Control-Request-Method", "POST"),
        )
            .andExpect(status().isForbidden)
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
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
