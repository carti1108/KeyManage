package com.keymanage.back.api.auth

import jakarta.servlet.http.HttpServletRequest
import java.net.InetAddress

/**
 * 시도 제한에 쓸 클라이언트 IP를 정한다 (SEC-002).
 * 접속 IP가 신뢰 프록시일 때만 X-Forwarded-For를 보고, 클라이언트가 위조할 수 있는 왼쪽 대신
 * 오른쪽부터 신뢰 프록시가 아닌 첫 IP를 쓴다. 신뢰 프록시가 없으면 X-Forwarded-For는 무시한다.
 * IPv6는 표기가 여러 가지(`::1`, `0:0:0:0:0:0:0:1`)이므로 비교 전에 정규화한다.
 */
class ClientIpResolver(trustedProxies: Set<String>) {

    private val trusted = trustedProxies.map(::canonical).toSet()

    fun resolve(request: HttpServletRequest): String {
        val remote = canonical(request.remoteAddr)
        if (remote !in trusted) return remote
        // 헤더가 여러 줄로 오면 getHeader는 첫 줄만 주므로, 모든 줄을 이어 붙인 뒤 오른쪽부터 본다
        val forwarded = request.getHeaders(FORWARDED_FOR).toList()
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map(::canonical)
        return forwarded.lastOrNull { it !in trusted } ?: remote
    }

    private companion object {
        const val FORWARDED_FOR = "X-Forwarded-For"

        // IP 표기만 정규화한다. 숫자·콜론·점으로 된 리터럴만 넘겨 DNS 조회가 일어나지 않게 한다
        val IP_LITERAL = Regex("[0-9a-fA-F:.]+")

        fun canonical(ip: String): String {
            if (!IP_LITERAL.matches(ip)) return ip
            return runCatching { InetAddress.getByName(ip).hostAddress }.getOrDefault(ip)
        }
    }
}
