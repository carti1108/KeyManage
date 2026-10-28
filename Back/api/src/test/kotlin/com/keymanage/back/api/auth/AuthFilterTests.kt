package com.keymanage.back.api.auth

import com.keymanage.back.api.ApiTestFixture
import com.keymanage.back.domain.auth.AuthenticateUseCase
import com.keymanage.back.domain.auth.Session
import com.keymanage.back.domain.auth.SessionRepository
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Bearer 토큰 필터와 IP 시도 제한 (SEC-002) */
class AuthFilterTests {

    private val fixture = ApiTestFixture()

    private fun loadVault(authorization: String?) = fixture.mockMvc.perform(
        get("/api/v1/vault").apply { if (authorization != null) header("Authorization", authorization) },
    )

    @Test
    fun `토큰이 없거나 형식이 틀리거나 모르는 토큰이면 볼트 API는 401 UNAUTHORIZED`() {
        listOf(null, "token-without-scheme", "Basic abc", "Bearer unknown").forEach { authorization ->
            loadVault(authorization)
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("로그인이 만료되었습니다. 다시 로그인해 주세요."))
        }
    }

    @Test
    fun `경로 표기를 바꿔도 인증 API가 아니면 토큰을 요구한다`() {
        listOf("/api/v1;x=1/vault", "/api/v1//vault", "/api/v1/auth/../vault", "/api/v1/auth/..;/vault", "/unknown").forEach { path ->
            fixture.mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        }
    }

    @Test
    fun `토큰 확인 중 예상하지 못한 오류가 나면 표준 형식의 500을 돌려준다`() {
        val broken = object : SessionRepository {
            override fun save(session: Session) = Unit
            override fun find(tokenHash: String): Session? = throw IllegalStateException("저장소 손상")
            override fun delete(tokenHash: String) = Unit
        }
        val filter = AuthTokenFilter(AuthenticateUseCase(broken, fixture.accounts, fixture.clock))
        val request = MockHttpServletRequest("GET", "/api/v1/vault").apply { addHeader("Authorization", "Bearer x") }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(500, response.status)
        assertTrue(response.contentAsString.contains("\"code\":\"INTERNAL_ERROR\""))
    }

    @Test
    fun `유효한 토큰이면 통과하고 Bearer는 대소문자를 가리지 않는다`() {
        val token = fixture.registerAndGetToken()

        loadVault("Bearer $token").andExpect(status().isNotFound)
        loadVault("bearer $token").andExpect(status().isNotFound)
    }

    @Test
    fun `30일이 지난 토큰은 401`() {
        val token = fixture.registerAndGetToken()
        fixture.clock.advance(Duration.ofDays(30))

        loadVault("Bearer $token").andExpect(status().isUnauthorized)
    }

    @Test
    fun `로그아웃한 토큰은 401`() {
        val token = fixture.registerAndGetToken()
        fixture.mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer $token"))

        loadVault("Bearer $token").andExpect(status().isUnauthorized)
    }

    @Test
    fun `같은 IP에서 인증 요청이 1분에 30번을 넘으면 429와 다음 분까지 남은 초를 돌려준다`() {
        fixture.clock.advance(Duration.ofSeconds(20))
        repeat(30) { fixture.postJson("/api/v1/auth/prelogin", """{"email":"a@example.com"}""").andExpect(status().isOk) }

        fixture.postJson("/api/v1/auth/login", fixture.loginBody())
            .andExpect(status().isTooManyRequests)
            .andExpect(header().string("Retry-After", "40"))
            .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))

        fixture.clock.advance(Duration.ofSeconds(40))
        fixture.postJson("/api/v1/auth/prelogin", """{"email":"a@example.com"}""").andExpect(status().isOk)
    }

    @Test
    fun `경로 표기를 바꿔도 인증 API는 같은 IP 제한을 받는다`() {
        val variants = listOf("/api/v1/auth/prelogin;x=1", "/api/v1//auth/prelogin", "/api/v1/auth/./prelogin")
        repeat(30) { i ->
            fixture.mockMvc.perform(post(variants[i % variants.size]).contentType("application/json").content("""{"email":"a@example.com"}"""))
        }

        fixture.postJson("/api/v1/auth/prelogin", """{"email":"a@example.com"}""").andExpect(status().isTooManyRequests)
    }

    @Test
    fun `IP 제한은 인증 API에만 걸고 볼트 API에는 걸지 않는다`() {
        val token = fixture.registerAndGetToken()
        repeat(29) { fixture.postJson("/api/v1/auth/prelogin", """{"email":"a@example.com"}""") }

        repeat(5) { loadVault("Bearer $token").andExpect(status().isNotFound) }
    }

    @Test
    fun `신뢰하지 않는 접속이 보낸 X-Forwarded-For는 무시한다`() {
        repeat(30) { i ->
            fixture.mockMvc.perform(
                post("/api/v1/auth/prelogin").contentType("application/json").content("""{"email":"a@example.com"}""")
                    .header("X-Forwarded-For", "203.0.113.$i"),
            )
        }

        fixture.postJson("/api/v1/auth/prelogin", """{"email":"a@example.com"}""").andExpect(status().isTooManyRequests)
    }

    @Test
    fun `신뢰하는 프록시가 보낸 X-Forwarded-For는 실제 클라이언트 IP로 쓴다`() {
        val behindProxy = ApiTestFixture(trustedProxies = setOf("127.0.0.1"))
        repeat(30) {
            behindProxy.mockMvc.perform(
                post("/api/v1/auth/prelogin").contentType("application/json").content("""{"email":"a@example.com"}""")
                    .header("X-Forwarded-For", "203.0.113.1"),
            ).andExpect(status().isOk)
        }

        behindProxy.mockMvc.perform(
            post("/api/v1/auth/prelogin").contentType("application/json").content("""{"email":"a@example.com"}""")
                .header("X-Forwarded-For", "203.0.113.2"),
        ).andExpect(status().isOk)
    }
}
