package com.keymanage.back.config

import com.keymanage.back.api.auth.AuthRateLimitFilter
import com.keymanage.back.api.auth.AuthTokenFilter
import com.keymanage.back.api.auth.ClientIpResolver
import com.keymanage.back.api.auth.IpRateLimiter
import com.keymanage.back.api.error.RequestBodyLimitFilter
import com.keymanage.back.domain.auth.AuthenticateUseCase
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

// 요청 필터를 순서대로 조립한다: 본문 크기 -> 인증 API IP 제한 -> Bearer 토큰 (api-spec 1장, SEC-002)
@Configuration
class WebConfiguration {

    @Bean
    fun ipRateLimiter(clock: Clock) = IpRateLimiter(clock)

    // 쉼표로 구분한 신뢰 프록시 IP. 비어 있으면 X-Forwarded-For를 무시하고 접속 IP를 쓴다
    @Bean
    fun clientIpResolver(@Value("\${keymanage.http.trusted-proxies:}") trustedProxies: String) =
        ClientIpResolver(trustedProxies.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet())

    @Bean
    fun requestBodyLimitFilter() = FilterRegistrationBean(RequestBodyLimitFilter()).apply { order = 1 }

    @Bean
    fun authRateLimitFilter(limiter: IpRateLimiter, clientIp: ClientIpResolver) =
        FilterRegistrationBean(AuthRateLimitFilter(limiter, clientIp)).apply { order = 2 }

    @Bean
    fun authTokenFilter(authenticate: AuthenticateUseCase) =
        FilterRegistrationBean(AuthTokenFilter(authenticate)).apply { order = 3 }
}
