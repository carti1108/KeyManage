package com.keymanage.back.api.auth

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.keymanage.back.api.ApiTestFixture
import com.keymanage.back.domain.auth.Email
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 계정 삭제 API의 응답과 오류 변환 (api-spec 2.5.6, SEC-005) */
class DeleteAccountApiTests {

    private val fixture = ApiTestFixture()
    private val token = fixture.registerAndGetToken()
    private val email = Email.of(ApiTestFixture.EMAIL)

    private fun delete(body: String = fixture.deleteAccountBody(), token: String = this.token) =
        fixture.postJson("/api/v1/auth/delete-account", body, token)

    private fun saveVault(token: String = this.token) =
        fixture.postJson("/api/v1/vault", """{"expectedRevision":0,"payload":{"ciphertext":"v"}}""", token)

    private fun getVault(token: String) = fixture.mockMvc.perform(get("/api/v1/vault").header("Authorization", "Bearer $token"))

    @Test
    fun `지우면 204이고 본문이 없다`() {
        delete()
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))
    }

    @Test
    fun `지우면 계정과 볼트가 없어지고 이 기기와 다른 기기의 토큰이 모두 401 UNAUTHORIZED`() {
        saveVault().andExpect(status().isOk)
        val vaultId = fixture.accounts.find(email)!!.vaultId
        val otherDevice = jacksonObjectMapper().readTree(
            fixture.postJson("/api/v1/auth/login", fixture.loginBody()).andReturn().response.contentAsString,
        )["accessToken"].asText()

        delete().andExpect(status().isNoContent)

        assertNull(fixture.accounts.find(email))
        assertNull(fixture.vaults.find(vaultId))
        listOf(token, otherDevice).forEach {
            getVault(it)
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        }
    }

    @Test
    fun `지운 뒤 같은 이메일로 로그인하면 401 AUTH_FAILED이고 다시 가입할 수 있다`() {
        delete().andExpect(status().isNoContent)

        fixture.postJson("/api/v1/auth/login", fixture.loginBody())
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTH_FAILED"))
        fixture.postJson("/api/v1/auth/register", fixture.registerBody())
            .andExpect(status().isCreated)
    }

    @Test
    fun `인증 해시가 틀리면 401 AUTH_FAILED이고 아무것도 지우지 않는다`() {
        saveVault().andExpect(status().isOk)
        val before = fixture.accounts.find(email)

        delete(fixture.deleteAccountBody(authHash = ByteArray(32) { 9 }))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTH_FAILED"))
            .andExpect(jsonPath("$.message").value("이메일 또는 마스터 비밀번호가 올바르지 않습니다."))

        assertEquals(before, fixture.accounts.find(email))
        assertNotNull(fixture.vaults.find(before!!.vaultId))
        getVault(token).andExpect(status().isOk)
    }

    @Test
    fun `5번 틀리면 429와 Retry-After를 돌려준다`() {
        repeat(5) { delete(fixture.deleteAccountBody(authHash = ByteArray(32) { 9 })) }

        delete()
            .andExpect(status().isTooManyRequests)
            .andExpect(header().string("Retry-After", "900"))
            .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
        assertNotNull(fixture.accounts.find(email))
    }

    @Test
    fun `입력 형식이 틀리면 400 INVALID_PARAMETER이고 계정은 그대로다`() {
        val before = fixture.accounts.find(email)
        listOf(
            fixture.deleteAccountBody(authHash = ByteArray(31)),
            fixture.deleteAccountBody(authHash = ByteArray(33)),
            """{"authHash":"%%%"}""",
            """{"authHash":null}""",
            "{}",
            "{",
        ).forEach { body ->
            delete(body)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
        }

        assertEquals(before, fixture.accounts.find(email))
    }

    @Test
    fun `토큰이 없으면 401 UNAUTHORIZED이고 계정은 그대로다`() {
        fixture.mockMvc.perform(
            post("/api/v1/auth/delete-account").contentType(MediaType.APPLICATION_JSON).content(fixture.deleteAccountBody()),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))

        assertNotNull(fixture.accounts.find(email))
    }

    @Test
    fun `이미 지운 계정의 토큰으로 다시 보내면 401 UNAUTHORIZED`() {
        delete().andExpect(status().isNoContent)

        delete()
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `IP별 요청 제한에 포함된다`() {
        repeat(30) { delete(fixture.deleteAccountBody(authHash = ByteArray(31))) }

        delete()
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
        assertNotNull(fixture.accounts.find(email))
    }
}
