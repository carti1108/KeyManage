package com.keymanage.back.api.auth

import com.keymanage.back.api.ApiTestFixture
import com.keymanage.back.api.ApiTestFixture.Companion.b64
import com.keymanage.back.domain.auth.Email
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 인증 API의 응답과 오류 변환 (api-spec 2.5, SEC-002) */
class AuthApiTests {

    private val fixture = ApiTestFixture()

    @Test
    fun `가입하면 201과 토큰, 만료 시각을 돌려준다`() {
        fixture.postJson("/api/v1/auth/register", fixture.registerBody())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.expiresAt").value("2026-11-27T00:00:00Z"))
    }

    @Test
    fun `가입한 이메일의 prelogin은 등록한 KDF 값을 돌려준다`() {
        fixture.postJson("/api/v1/auth/register", fixture.registerBody())

        fixture.postJson("/api/v1/auth/prelogin", """{"email":" User@Example.com "}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.kdf").value("PBKDF2-HMAC-SHA256"))
            .andExpect(jsonPath("$.iterations").value(600_000))
            .andExpect(jsonPath("$.salt").value(b64(ApiTestFixture.SALT)))
    }

    @Test
    fun `없는 이메일의 prelogin도 200으로 같은 모양을 돌려준다`() {
        fixture.postJson("/api/v1/auth/prelogin", """{"email":"ghost@example.com"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.iterations").value(600_000))
            .andExpect(jsonPath("$.salt").isNotEmpty)
    }

    @Test
    fun `이미 가입된 이메일은 409 EMAIL_ALREADY_REGISTERED`() {
        fixture.postJson("/api/v1/auth/register", fixture.registerBody())

        fixture.postJson("/api/v1/auth/register", fixture.registerBody())
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
            .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."))
    }

    @Test
    fun `가입이 꺼져 있으면 403 REGISTRATION_DISABLED`() {
        ApiTestFixture(registrationEnabled = false).let {
            it.postJson("/api/v1/auth/register", it.registerBody())
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("REGISTRATION_DISABLED"))
                .andExpect(jsonPath("$.message").value("현재 새 계정을 만들 수 없습니다."))
        }
    }

    @Test
    fun `가입 입력 형식이 틀리면 400 INVALID_PARAMETER`() {
        listOf(
            fixture.registerBody(email = "not-an-email"),
            fixture.registerBody(authHash = ByteArray(31)),
            fixture.registerBody(salt = ByteArray(15)),
            fixture.registerBody(iterations = 599_999),
            """{"email":"user@example.com","authHash":"%%%","kdfSalt":"${b64(ApiTestFixture.SALT)}","kdfIterations":600000}""",
            """{"email":"user@example.com"}""",
        ).forEach { body ->
            fixture.postJson("/api/v1/auth/register", body)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
        }
        assertNull(fixture.accounts.find(Email.of("user@example.com")))
    }

    @Test
    fun `올바른 인증 해시로 로그인하면 토큰을 돌려준다`() {
        fixture.postJson("/api/v1/auth/register", fixture.registerBody())

        fixture.postJson("/api/v1/auth/login", fixture.loginBody())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
    }

    @Test
    fun `틀린 인증 해시와 없는 이메일은 같은 401 AUTH_FAILED 본문을 받는다`() {
        fixture.postJson("/api/v1/auth/register", fixture.registerBody())

        val wrong = fixture.postJson("/api/v1/auth/login", fixture.loginBody(authHash = ByteArray(32) { 9 }))
            .andExpect(status().isUnauthorized).andReturn().response.contentAsString
        val ghost = fixture.postJson("/api/v1/auth/login", fixture.loginBody(email = "ghost@example.com"))
            .andExpect(status().isUnauthorized).andReturn().response.contentAsString

        assertEquals(wrong.substringBefore("\"timestamp\""), ghost.substringBefore("\"timestamp\""))
        fixture.postJson("/api/v1/auth/login", fixture.loginBody(authHash = ByteArray(32) { 9 }))
            .andExpect(jsonPath("$.code").value("AUTH_FAILED"))
            .andExpect(jsonPath("$.message").value("이메일 또는 마스터 비밀번호가 올바르지 않습니다."))
    }

    @Test
    fun `5번 틀리면 429와 남은 초를 Retry-After로 알려준다`() {
        fixture.postJson("/api/v1/auth/register", fixture.registerBody())
        repeat(5) { fixture.postJson("/api/v1/auth/login", fixture.loginBody(authHash = ByteArray(32) { 9 })) }

        fixture.postJson("/api/v1/auth/login", fixture.loginBody())
            .andExpect(status().isTooManyRequests)
            .andExpect(header().string("Retry-After", "900"))
            .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
            .andExpect(jsonPath("$.message").value("로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요."))
    }

    @Test
    fun `로그아웃하면 204이고 같은 토큰으로 다시 해도 204다`() {
        val token = fixture.registerAndGetToken()

        repeat(2) {
            fixture.mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer $token"))
                .andExpect(status().isNoContent)
        }
    }

    @Test
    fun `토큰 없이 로그아웃하면 401 UNAUTHORIZED`() {
        fixture.mockMvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }
}
