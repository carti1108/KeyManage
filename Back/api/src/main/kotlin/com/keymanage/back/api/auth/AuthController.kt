package com.keymanage.back.api.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.keymanage.back.api.error.ApiError
import com.keymanage.back.api.error.ApiException
import com.keymanage.back.domain.auth.Account
import com.keymanage.back.domain.auth.AuthHash
import com.keymanage.back.domain.auth.ChangePasswordUseCase
import com.keymanage.back.domain.auth.DeleteAccountUseCase
import com.keymanage.back.domain.auth.Email
import com.keymanage.back.domain.auth.IssuedSession
import com.keymanage.back.domain.auth.KdfParams
import com.keymanage.back.domain.auth.LoginUseCase
import com.keymanage.back.domain.auth.LogoutUseCase
import com.keymanage.back.domain.auth.PasswordChange
import com.keymanage.back.domain.auth.PreloginUseCase
import com.keymanage.back.domain.auth.RegisterUseCase
import com.keymanage.back.domain.auth.UnauthorizedException
import com.keymanage.back.domain.vault.EncryptedVault
import com.keymanage.back.domain.vault.Revision
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.Base64

/**
 * 계정 인증 API (api-spec 2.5, SEC-002).
 * 요청 필드는 null로 받아 빠진 값을 400으로 거절하고, Base64·길이·형식 검사는 도메인 값 객체가 맡는다.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val prelogin: PreloginUseCase,
    private val register: RegisterUseCase,
    private val login: LoginUseCase,
    private val logout: LogoutUseCase,
    private val changePassword: ChangePasswordUseCase,
    private val deleteAccount: DeleteAccountUseCase,
    private val objectMapper: ObjectMapper,
) {

    @PostMapping("/prelogin")
    fun prelogin(@RequestBody request: PreloginRequest): PreloginResponse {
        val kdf = prelogin.execute(Email.of(request.email ?: invalid()))
        return PreloginResponse(KdfParams.ALGORITHM, kdf.iterations, encode(kdf.salt))
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody request: RegisterRequest): TokenResponse {
        val email = Email.of(request.email ?: invalid())
        val authHash = AuthHash.of(decode(request.authHash))
        val kdf = KdfParams(decode(request.kdfSalt), request.kdfIterations ?: invalid())
        return TokenResponse.of(register.execute(email, authHash, kdf))
    }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): TokenResponse {
        val email = Email.of(request.email ?: invalid())
        return TokenResponse.of(login.execute(email, AuthHash.of(decode(request.authHash))))
    }

    // 이미 폐기된 토큰이어도 204로 응답하도록 인증 필터를 거치지 않고 토큰만 꺼내 지운다
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?) {
        logout.execute(BearerTokens.extract(authorization) ?: throw UnauthorizedException())
    }

    // 토큰은 AuthTokenFilter가 확인하고 그 계정을 넘긴다. 볼트 페이로드는 볼트 저장처럼 해석하지 않고 문자열로 바꾼다 (api-spec 2.5.5)
    @PostMapping("/change-password")
    fun changePassword(
        @RequestAttribute(AuthTokenFilter.ACCOUNT_ATTRIBUTE) account: Account,
        @RequestBody request: ChangePasswordRequest,
    ): ChangePasswordResponse {
        val kdf = request.kdf ?: invalid()
        val vault = request.vault ?: invalid()
        val expectedRevision = vault.expectedRevision
        if (expectedRevision == null || expectedRevision < 0 || vault.payload?.isObject != true) invalid()
        val change = PasswordChange(
            currentAuthHash = AuthHash.of(decode(request.currentAuthHash)),
            newAuthHash = AuthHash.of(decode(request.newAuthHash)),
            newKdf = KdfParams(decode(kdf.salt), kdf.iterations ?: invalid()),
            expectedRevision = Revision(expectedRevision),
            vault = EncryptedVault(objectMapper.writeValueAsString(vault.payload)),
        )
        val changed = changePassword.execute(account.email, change)
        return ChangePasswordResponse(changed.session.token, changed.session.expiresAt.toString(), changed.revision.value)
    }

    // 토큰은 AuthTokenFilter가 확인하고 그 계정을 넘긴다. 계정을 지우면 이 계정의 모든 토큰이 무효다 (api-spec 2.5.6)
    @PostMapping("/delete-account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAccount(
        @RequestAttribute(AuthTokenFilter.ACCOUNT_ATTRIBUTE) account: Account,
        @RequestBody request: DeleteAccountRequest,
    ) {
        deleteAccount.execute(account.email, AuthHash.of(decode(request.authHash)))
    }

    private fun decode(value: String?): ByteArray =
        value?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() } ?: invalid()

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun invalid(): Nothing = throw ApiException(ApiError.INVALID_PARAMETER)
}

/** prelogin 요청 (api-spec 2.5.1) */
data class PreloginRequest(val email: String?)

/** prelogin 응답 (api-spec 2.5.1) */
data class PreloginResponse(val kdf: String, val iterations: Int, val salt: String)

/** 가입 요청 (api-spec 2.5.2) */
data class RegisterRequest(val email: String?, val authHash: String?, val kdfSalt: String?, val kdfIterations: Int?)

/** 로그인 요청 (api-spec 2.5.3) */
data class LoginRequest(val email: String?, val authHash: String?)

/** 가입·로그인 응답 (api-spec 2.5.2·2.5.3) */
data class TokenResponse(val accessToken: String, val expiresAt: String) {
    companion object {
        fun of(session: IssuedSession) = TokenResponse(session.token, session.expiresAt.toString())
    }
}

/** 비밀번호 변경 요청의 새 KDF 값 (api-spec 2.5.5) */
data class KdfRequest(val iterations: Int?, val salt: String?)

/** 비밀번호 변경 요청의 다시 암호화한 볼트. 볼트 저장과 같은 규칙이다 (api-spec 2.4·2.5.5) */
data class ChangePasswordVaultRequest(val expectedRevision: Long?, val payload: JsonNode?)

/** 비밀번호 변경 요청 (api-spec 2.5.5) */
data class ChangePasswordRequest(
    val currentAuthHash: String?,
    val newAuthHash: String?,
    val kdf: KdfRequest?,
    val vault: ChangePasswordVaultRequest?,
)

/** 계정 삭제 요청 (api-spec 2.5.6) */
data class DeleteAccountRequest(val authHash: String?)

/** 비밀번호 변경 응답. 새 토큰과 교체한 볼트의 revision (api-spec 2.5.5) */
data class ChangePasswordResponse(val accessToken: String, val expiresAt: String, val revision: Long)
