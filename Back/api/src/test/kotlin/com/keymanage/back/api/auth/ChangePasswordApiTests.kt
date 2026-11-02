package com.keymanage.back.api.auth

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.keymanage.back.api.ApiTestFixture
import com.keymanage.back.api.ApiTestFixture.Companion.PAYLOAD
import com.keymanage.back.api.ApiTestFixture.Companion.b64
import com.keymanage.back.domain.auth.Email
import com.keymanage.back.domain.vault.SaveVaultUseCase
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertEquals

/** 마스터 비밀번호 변경 API의 응답과 오류 변환 (api-spec 2.5.5, SEC-004) */
class ChangePasswordApiTests {

    private val fixture = ApiTestFixture()
    private val token = fixture.registerAndGetToken()
    private val email = Email.of(ApiTestFixture.EMAIL)

    private fun change(body: String = fixture.changePasswordBody(), token: String = this.token) =
        fixture.postJson("/api/v1/auth/change-password", body, token)

    private fun saveVault(expectedRevision: Long, token: String = this.token) =
        fixture.postJson("/api/v1/vault", """{"expectedRevision":$expectedRevision,"payload":{"ciphertext":"v"}}""", token)

    private fun accessTokenOf(response: String): String = jacksonObjectMapper().readTree(response)["accessToken"].asText()

    @Test
    fun `바꾸면 200과 새 토큰, 만료 시각, 교체한 볼트 revision을 돌려준다`() {
        saveVault(0)
        saveVault(1)

        change(fixture.changePasswordBody(expectedRevision = 2))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.expiresAt").value("2026-11-27T00:00:00Z"))
            .andExpect(jsonPath("$.revision").value(3))
    }

    @Test
    fun `새 토큰으로 다시 암호화한 볼트를 받고 요청에 쓴 토큰은 폐기된다`() {
        val response = change().andExpect(status().isOk).andReturn().response.contentAsString
        val newToken = accessTokenOf(response)

        fixture.mockMvc.perform(get("/api/v1/vault").header("Authorization", "Bearer $newToken"))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"revision":1,"payload":$PAYLOAD}""", JsonCompareMode.STRICT))
        fixture.mockMvc.perform(get("/api/v1/vault").header("Authorization", "Bearer $token"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `다른 기기의 세션도 폐기된다`() {
        val otherDevice = accessTokenOf(
            fixture.postJson("/api/v1/auth/login", fixture.loginBody()).andReturn().response.contentAsString,
        )

        change().andExpect(status().isOk)

        fixture.mockMvc.perform(get("/api/v1/vault").header("Authorization", "Bearer $otherDevice"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `바꾼 뒤 prelogin은 새 KDF 값을 돌려주고 새 인증 해시로만 로그인한다`() {
        change().andExpect(status().isOk)

        fixture.postJson("/api/v1/auth/prelogin", """{"email":"${ApiTestFixture.EMAIL}"}""")
            .andExpect(jsonPath("$.salt").value(b64(ApiTestFixture.NEW_SALT)))
        fixture.postJson("/api/v1/auth/login", fixture.loginBody())
            .andExpect(status().isUnauthorized)
        fixture.postJson("/api/v1/auth/login", fixture.loginBody(authHash = ApiTestFixture.NEW_AUTH_HASH))
            .andExpect(status().isOk)
    }

    @Test
    fun `현재 인증 해시가 틀리면 401 AUTH_FAILED이고 계정은 그대로다`() {
        val before = fixture.accounts.find(email)

        change(fixture.changePasswordBody(currentAuthHash = ByteArray(32) { 9 }))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTH_FAILED"))
            .andExpect(jsonPath("$.message").value("이메일 또는 마스터 비밀번호가 올바르지 않습니다."))

        assertEquals(before, fixture.accounts.find(email))
    }

    @Test
    fun `5번 틀리면 429와 Retry-After를 돌려준다`() {
        repeat(5) { change(fixture.changePasswordBody(currentAuthHash = ByteArray(32) { 9 })) }

        change()
            .andExpect(status().isTooManyRequests)
            .andExpect(header().string("Retry-After", "900"))
            .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
    }

    @Test
    fun `revision이 다르면 409 VAULT_CONFLICT와 현재 revision을 돌려주고 계정은 그대로다`() {
        saveVault(0)
        val before = fixture.accounts.find(email)

        change(fixture.changePasswordBody(expectedRevision = 0))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("VAULT_CONFLICT"))
            .andExpect(jsonPath("$.currentRevision").value(1))

        assertEquals(before, fixture.accounts.find(email))
    }

    @Test
    fun `볼트가 5MB를 넘으면 413 VAULT_TOO_LARGE`() {
        val big = """{"ciphertext":"${"a".repeat(SaveVaultUseCase.MAX_PAYLOAD_BYTES)}"}"""

        change(fixture.changePasswordBody(payload = big))
            .andExpect(status().isPayloadTooLarge)
            .andExpect(jsonPath("$.code").value("VAULT_TOO_LARGE"))
    }

    @Test
    fun `입력 형식이 틀리면 400 INVALID_PARAMETER이고 계정은 그대로다`() {
        val before = fixture.accounts.find(email)
        val valid = fixture.changePasswordBody()
        listOf(
            fixture.changePasswordBody(currentAuthHash = ByteArray(31)),
            fixture.changePasswordBody(newAuthHash = ByteArray(33)),
            fixture.changePasswordBody(salt = ByteArray(15)),
            fixture.changePasswordBody(iterations = 599_999),
            fixture.changePasswordBody(iterations = 10_000_001),
            fixture.changePasswordBody(expectedRevision = -1),
            fixture.changePasswordBody(payload = "\"not-an-object\""),
            valid.replace(b64(ApiTestFixture.NEW_AUTH_HASH), "%%%"),
            valid.replace(""""kdf":{"iterations":600000,""", """"kdf":{"""),
            valid.replace(""""vault":{"expectedRevision":0,""", """"vault":{"""),
            """{"currentAuthHash":"${b64(ApiTestFixture.AUTH_HASH)}"}""",
            """{"currentAuthHash":"${b64(ApiTestFixture.AUTH_HASH)}","newAuthHash":"${b64(ApiTestFixture.NEW_AUTH_HASH)}","kdf":{"iterations":600000,"salt":"${b64(ApiTestFixture.NEW_SALT)}"}}""",
        ).forEach { body ->
            change(body)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
        }

        assertEquals(before, fixture.accounts.find(email))
    }

    @Test
    fun `반복 횟수 상한 10,000,000은 받는다`() {
        change(fixture.changePasswordBody(iterations = 10_000_000)).andExpect(status().isOk)
    }

    @Test
    fun `토큰이 없으면 401 UNAUTHORIZED`() {
        fixture.mockMvc.perform(
            post("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON).content(fixture.changePasswordBody()),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `IP별 요청 제한에 포함된다`() {
        repeat(30) { change(fixture.changePasswordBody(currentAuthHash = ByteArray(31))) }

        change()
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
    }
}
