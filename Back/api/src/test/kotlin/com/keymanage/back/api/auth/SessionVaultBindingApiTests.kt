package com.keymanage.back.api.auth

import com.keymanage.back.api.ApiTestFixture
import com.keymanage.back.domain.auth.Email
import com.keymanage.back.domain.auth.Session
import com.keymanage.back.domain.auth.SessionIssuer
import com.keymanage.back.domain.auth.SessionTokens
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertNull

/** 비밀번호 변경과 겹쳐 옛 볼트 ID로 발급된 세션은 볼트 API에서 401이다 (SEC-004 규칙 7) */
class SessionVaultBindingApiTests {

    private val fixture = ApiTestFixture()
    private val token = fixture.registerAndGetToken()
    private val email = Email.of(ApiTestFixture.EMAIL)

    @Test
    fun `변경 전 볼트 ID로 발급된 세션은 변경 뒤 볼트 조회·저장에서 401을 받고 지워진다`() {
        val oldVaultId = fixture.accounts.find(email)!!.vaultId
        fixture.postJson("/api/v1/auth/change-password", fixture.changePasswordBody(), token).andExpect(status().isOk)
        // 변경과 겹친 옛 비밀번호 로그인이 세션 폐기 뒤에 저장한 세션을 흉내 낸다
        val lateToken = "late-login-token"
        val late = Session(SessionTokens.hash(lateToken), email, fixture.clock.instant().plus(SessionIssuer.TTL), oldVaultId)
        fixture.sessions.save(late)

        fixture.mockMvc.perform(get("/api/v1/vault").header("Authorization", "Bearer $lateToken"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        fixture.sessions.save(late)
        fixture.postJson("/api/v1/vault", """{"expectedRevision":1,"payload":{"ciphertext":"x"}}""", lateToken)
            .andExpect(status().isUnauthorized)

        assertNull(fixture.sessions.find(late.tokenHash))
    }
}
