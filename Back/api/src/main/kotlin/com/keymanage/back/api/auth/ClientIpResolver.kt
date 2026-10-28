package com.keymanage.back.api.auth

import jakarta.servlet.http.HttpServletRequest

/**
 * 시도 제한에 쓸 클라이언트 IP를 정한다 (SEC-002).
 * 접속 IP가 신뢰 프록시일 때만 X-Forwarded-For를 보고, 클라이언트가 위조할 수 있는 왼쪽 대신
 * 오른쪽부터 신뢰 프록시가 아닌 첫 IP를 쓴다. 신뢰 프록시가 없으면 X-Forwarded-For는 무시한다.
 */
class ClientIpResolver(private val trustedProxies: Set<String>) {

    fun resolve(request: HttpServletRequest): String {
        val remote = request.remoteAddr
        if (remote !in trustedProxies) return remote
        val forwardedFor = request.getHeader(FORWARDED_FOR) ?: return remote
        return forwardedFor.split(",").map { it.trim() }.filter { it.isNotEmpty() }.lastOrNull { it !in trustedProxies } ?: remote
    }

    private companion object {
        const val FORWARDED_FOR = "X-Forwarded-For"
    }
}
