package com.keymanage.back.api.auth

import com.keymanage.back.api.error.ApiError
import com.keymanage.back.api.error.ErrorResponses
import com.keymanage.back.domain.auth.AuthenticateUseCase
import com.keymanage.back.domain.auth.UnauthorizedException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 기본은 거부: 인증 API 4개를 뺀 모든 요청에 유효한 Bearer 토큰을 요구한다 (SEC-002).
 * 경로는 [RequestPaths]로 정규화해 판단하므로 표기를 바꿔 우회할 수 없고, 새 API를 추가해도 자동으로 보호된다.
 * 통과하면 토큰의 계정을 요청 속성 [ACCOUNT_ATTRIBUTE]에 담아 컨트롤러가 그 계정의 볼트만 다루게 한다.
 */
class AuthTokenFilter(private val authenticate: AuthenticateUseCase) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = RequestPaths.normalized(request) in PUBLIC_PATHS

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val token = BearerTokens.extract(request.getHeader(HttpHeaders.AUTHORIZATION))
            ?: return ErrorResponses.write(response, ApiError.UNAUTHORIZED)
        val account = try {
            authenticate.execute(token)
        } catch (e: UnauthorizedException) {
            return ErrorResponses.write(response, ApiError.UNAUTHORIZED)
        } catch (e: Exception) {
            // 필터 밖으로 던지면 표준 오류 형식을 벗어나므로 여기서 처리한다. 토큰은 로그에 남기지 않는다
            log.error("토큰 확인 중 예상하지 못한 오류", e)
            return ErrorResponses.write(response, ApiError.INTERNAL_ERROR)
        }
        request.setAttribute(ACCOUNT_ATTRIBUTE, account)
        filterChain.doFilter(request, response)
    }

    companion object {
        const val ACCOUNT_ATTRIBUTE = "keymanage.account"

        // 로그아웃은 이미 폐기된 토큰으로도 204를 받아야 하므로 컨트롤러가 직접 토큰을 확인한다
        private val PUBLIC_PATHS = setOf(
            "/api/v1/auth/prelogin",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/logout",
        )

        private val log = LoggerFactory.getLogger(AuthTokenFilter::class.java)
    }
}
