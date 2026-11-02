package com.keymanage.back.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.keymanage.back.api.auth.AuthController
import com.keymanage.back.api.auth.AuthRateLimitFilter
import com.keymanage.back.api.auth.AuthTokenFilter
import com.keymanage.back.api.auth.ClientIpResolver
import com.keymanage.back.api.auth.IpRateLimiter
import com.keymanage.back.api.error.ApiExceptionHandler
import com.keymanage.back.api.error.RequestBodyLimitFilter
import com.keymanage.back.api.vault.VaultController
import com.keymanage.back.domain.auth.AuthenticateUseCase
import com.keymanage.back.domain.auth.ChangePasswordUseCase
import com.keymanage.back.domain.auth.DeleteAccountUseCase
import com.keymanage.back.domain.auth.FakePasswordHasher
import com.keymanage.back.domain.auth.InMemoryAccountRepository
import com.keymanage.back.domain.auth.InMemorySessionRepository
import com.keymanage.back.domain.auth.LoginAttemptLimiter
import com.keymanage.back.domain.auth.LoginUseCase
import com.keymanage.back.domain.auth.LogoutUseCase
import com.keymanage.back.domain.auth.MutableClock
import com.keymanage.back.domain.auth.PreloginUseCase
import com.keymanage.back.domain.auth.RegisterUseCase
import com.keymanage.back.domain.auth.ServerSecret
import com.keymanage.back.domain.auth.SessionIssuer
import com.keymanage.back.domain.vault.GetVaultUseCase
import com.keymanage.back.domain.vault.InMemoryVaultRepository
import com.keymanage.back.domain.vault.SaveVaultUseCase
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import java.util.Base64

/** 서버를 띄우지 않고 진짜 UseCase·필터에 가짜 저장소를 꽂아 API 전체를 조립한다 */
class ApiTestFixture(registrationEnabled: Boolean = true, trustedProxies: Set<String> = emptySet()) {

    val clock = MutableClock()
    val accounts = InMemoryAccountRepository()
    val sessions = InMemorySessionRepository()
    val vaults = InMemoryVaultRepository()

    private val hasher = FakePasswordHasher()
    private val issuer = SessionIssuer(sessions, clock)
    private val limiter = LoginAttemptLimiter(clock)

    val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(
            AuthController(
                PreloginUseCase(accounts, ServerSecret(ByteArray(32) { 1 })),
                RegisterUseCase(accounts, hasher, issuer, registrationEnabled),
                LoginUseCase(accounts, hasher, issuer, limiter),
                LogoutUseCase(sessions),
                ChangePasswordUseCase(accounts, vaults, sessions, hasher, issuer, limiter),
                DeleteAccountUseCase(accounts, vaults, sessions, hasher, limiter),
                jacksonObjectMapper(),
            ),
            VaultController(GetVaultUseCase(vaults), SaveVaultUseCase(vaults), jacksonObjectMapper()),
        )
        .setControllerAdvice(ApiExceptionHandler())
        .addFilters<StandaloneMockMvcBuilder>(
            RequestBodyLimitFilter(),
            AuthRateLimitFilter(IpRateLimiter(clock), ClientIpResolver(trustedProxies)),
            AuthTokenFilter(AuthenticateUseCase(sessions, accounts, clock)),
        )
        .build()

    fun postJson(path: String, body: String): ResultActions =
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))

    fun registerBody(email: String = EMAIL, authHash: ByteArray = AUTH_HASH, salt: ByteArray = SALT, iterations: Int = 600_000) =
        """{"email":"$email","authHash":"${b64(authHash)}","kdfSalt":"${b64(salt)}","kdfIterations":$iterations}"""

    fun loginBody(email: String = EMAIL, authHash: ByteArray = AUTH_HASH) =
        """{"email":"$email","authHash":"${b64(authHash)}"}"""

    /** 비밀번호 변경 요청 본문 (api-spec 2.5.5) */
    fun changePasswordBody(
        currentAuthHash: ByteArray = AUTH_HASH,
        newAuthHash: ByteArray = NEW_AUTH_HASH,
        salt: ByteArray = NEW_SALT,
        iterations: Int = 600_000,
        expectedRevision: Long = 0,
        payload: String = PAYLOAD,
    ) = """{"currentAuthHash":"${b64(currentAuthHash)}","newAuthHash":"${b64(newAuthHash)}",""" +
        """"kdf":{"iterations":$iterations,"salt":"${b64(salt)}"},"vault":{"expectedRevision":$expectedRevision,"payload":$payload}}"""

    /** 계정 삭제 요청 본문 (api-spec 2.5.6) */
    fun deleteAccountBody(authHash: ByteArray = AUTH_HASH) = """{"authHash":"${b64(authHash)}"}"""

    fun postJson(path: String, body: String, token: String): ResultActions =
        mockMvc.perform(post(path).header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(body))

    /** 가입하고 발급받은 토큰을 돌려준다 */
    fun registerAndGetToken(email: String = EMAIL): String {
        val response = postJson("/api/v1/auth/register", registerBody(email = email)).andReturn().response.contentAsString
        return jacksonObjectMapper().readTree(response)["accessToken"].asText()
    }

    companion object {
        const val EMAIL = "user@example.com"
        val AUTH_HASH = ByteArray(32) { 1 }
        val SALT = ByteArray(16) { 2 }
        val NEW_AUTH_HASH = ByteArray(32) { 3 }
        val NEW_SALT = ByteArray(16) { 4 }
        const val PAYLOAD = """{"version":1,"cipher":"AES-256-GCM","ciphertext":"re-encrypted"}"""

        fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    }
}
