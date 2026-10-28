package com.keymanage.back.api.auth

import com.keymanage.back.api.error.ApiError
import com.keymanage.back.api.error.ErrorResponses
import com.keymanage.back.domain.auth.AuthenticateUseCase
import com.keymanage.back.domain.auth.UnauthorizedException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 인증 API(`/api/v1/auth/`)를 뺀 모든 API에 유효한 Bearer 토큰을 요구한다 (SEC-002).
 * 통과하면 토큰의 계정을 요청 속성 [ACCOUNT_ATTRIBUTE]에 담아 컨트롤러가 그 계정의 볼트만 다루게 한다.
 */
class AuthTokenFilter(private val authenticate: AuthenticateUseCase) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return !path.startsWith(API_PREFIX) || path.startsWith(AUTH_PREFIX)
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val token = BearerTokens.extract(request.getHeader(HttpHeaders.AUTHORIZATION))
            ?: return ErrorResponses.write(response, ApiError.UNAUTHORIZED)
        val account = try {
            authenticate.execute(token)
        } catch (e: UnauthorizedException) {
            return ErrorResponses.write(response, ApiError.UNAUTHORIZED)
        }
        request.setAttribute(ACCOUNT_ATTRIBUTE, account)
        filterChain.doFilter(request, response)
    }

    companion object {
        const val ACCOUNT_ATTRIBUTE = "keymanage.account"
        private const val API_PREFIX = "/api/v1/"
        private const val AUTH_PREFIX = "/api/v1/auth/"
    }
}
