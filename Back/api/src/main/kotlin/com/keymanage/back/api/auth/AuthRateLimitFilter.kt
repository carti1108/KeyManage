package com.keymanage.back.api.auth

import com.keymanage.back.api.error.ApiError
import com.keymanage.back.api.error.ErrorResponses
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

/** prelogin·가입·로그인 요청을 클라이언트 IP별로 제한한다. 넘으면 429와 Retry-After (SEC-002) */
class AuthRateLimitFilter(
    private val limiter: IpRateLimiter,
    private val clientIp: ClientIpResolver,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.requestURI !in LIMITED_PATHS

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val retryAfter = limiter.tryAcquire(clientIp.resolve(request))
        if (retryAfter != null) return ErrorResponses.write(response, ApiError.TOO_MANY_ATTEMPTS, retryAfter)
        filterChain.doFilter(request, response)
    }

    private companion object {
        val LIMITED_PATHS = setOf("/api/v1/auth/prelogin", "/api/v1/auth/register", "/api/v1/auth/login")
    }
}
