package com.keymanage.back.config

import com.keymanage.back.api.auth.AuthRateLimitFilter
import com.keymanage.back.api.auth.AuthTokenFilter
import com.keymanage.back.api.auth.ClientIpResolver
import com.keymanage.back.api.auth.IpRateLimiter
import com.keymanage.back.api.error.ApiError
import com.keymanage.back.api.error.ErrorResponses
import com.keymanage.back.api.error.RequestBodyLimitFilter
import com.keymanage.back.domain.auth.AuthenticateUseCase
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.DefaultCorsProcessor
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter
import java.time.Clock

// 요청 필터를 순서대로 조립한다: CORS -> 본문 크기 -> 인증 API IP 제한 -> Bearer 토큰 (api-spec 1장, SEC-002, ADR-0013)
@Configuration
class WebConfiguration {

    @Bean
    fun ipRateLimiter(clock: Clock) = IpRateLimiter(clock)

    // 쉼표로 구분한 신뢰 프록시 IP. 비어 있으면 X-Forwarded-For를 무시하고 접속 IP를 쓴다
    @Bean
    fun clientIpResolver(@Value("\${keymanage.http.trusted-proxies:}") trustedProxies: String) =
        ClientIpResolver(trustedProxies.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet())

    // 쉼표로 구분한 허용 출처(데스크톱 앱). 비어 있으면 교차 출처 요청을 모두 거절한다.
    // 사전 요청(OPTIONS)에는 토큰이 없으므로 토큰 필터보다 앞에서 처리한다
    @Bean
    fun corsFilter(@Value("\${keymanage.http.allowed-origins:}") allowedOrigins: String): FilterRegistrationBean<CorsFilter> {
        val config = CorsConfiguration().apply {
            this.allowedOrigins = allowedOrigins.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            allowedMethods = listOf("GET", "POST")
            allowedHeaders = listOf("Authorization", "Content-Type")
            exposedHeaders = listOf("Retry-After")
            maxAge = 600
        }
        val source = UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/api/**", config) }
        val filter = CorsFilter(source).apply { setCorsProcessor(StandardErrorCorsProcessor()) }
        return FilterRegistrationBean(filter).apply { order = 0 }
    }

    @Bean
    fun requestBodyLimitFilter() = FilterRegistrationBean(RequestBodyLimitFilter()).apply { order = 1 }

    @Bean
    fun authRateLimitFilter(limiter: IpRateLimiter, clientIp: ClientIpResolver) =
        FilterRegistrationBean(AuthRateLimitFilter(limiter, clientIp)).apply { order = 2 }

    @Bean
    fun authTokenFilter(authenticate: AuthenticateUseCase) =
        FilterRegistrationBean(AuthTokenFilter(authenticate)).apply { order = 3 }
}

// 거절 응답도 다른 4xx처럼 표준 오류 본문으로 보낸다 (api-spec 1장). 기본 구현은 일반 텍스트를 쓴다
private class StandardErrorCorsProcessor : DefaultCorsProcessor() {
    override fun rejectRequest(response: ServerHttpResponse) {
        ErrorResponses.write((response as ServletServerHttpResponse).servletResponse, ApiError.ORIGIN_NOT_ALLOWED)
    }
}
