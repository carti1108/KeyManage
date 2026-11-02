package com.keymanage.back.api.auth

import com.keymanage.back.api.error.ApiError
import com.keymanage.back.api.error.ErrorResponses
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

/**
 * prelogin·가입·로그인·비밀번호 변경·계정 삭제 요청을 클라이언트 IP별로 제한한다. 넘으면 429와 Retry-After (SEC-002).
 * 고정 구간 방식이라 구간 경계 직전·직후를 합치면 짧은 순간에 최대 2배까지 허용될 수 있다.
 */
class AuthRateLimitFilter(
    private val limiter: IpRateLimiter,
    private val clientIp: ClientIpResolver,
) : OncePerRequestFilter() {

    // 표기를 바꿔 제한을 우회하지 못하도록 정규화한 경로로 판단한다
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = RequestPaths.normalized(request) !in LIMITED_PATHS

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val retryAfter = limiter.tryAcquire(clientIp.resolve(request))
        if (retryAfter != null) return ErrorResponses.write(response, ApiError.TOO_MANY_ATTEMPTS, retryAfter)
        filterChain.doFilter(request, response)
    }

    private companion object {
        // 비밀번호 변경·계정 삭제도 인증 해시를 확인하므로 로그인과 같이 제한한다 (SEC-004 규칙 6, SEC-005 규칙 3)
        val LIMITED_PATHS = setOf(
            "/api/v1/auth/prelogin",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/change-password",
            "/api/v1/auth/delete-account",
        )
    }
}
