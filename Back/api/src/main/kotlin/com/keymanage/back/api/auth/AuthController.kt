package com.keymanage.back.api.auth

import com.keymanage.back.api.error.ApiError
import com.keymanage.back.api.error.ApiException
import com.keymanage.back.domain.auth.AuthHash
import com.keymanage.back.domain.auth.Email
import com.keymanage.back.domain.auth.IssuedSession
import com.keymanage.back.domain.auth.KdfParams
import com.keymanage.back.domain.auth.LoginUseCase
import com.keymanage.back.domain.auth.LogoutUseCase
import com.keymanage.back.domain.auth.PreloginUseCase
import com.keymanage.back.domain.auth.RegisterUseCase
import com.keymanage.back.domain.auth.UnauthorizedException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
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
