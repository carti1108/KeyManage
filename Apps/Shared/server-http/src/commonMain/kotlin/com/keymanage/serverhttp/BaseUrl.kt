package com.keymanage.serverhttp

// 빌드할 때 정한 서버 주소 검사 (ADR-0013, SEC-002). 원본: Apps/Web/src/adapters/http/baseUrl.ts
// 원본은 브라우저 URL 파서를 썼다. 공통 코드에는 같은 파서가 없어 출처 형식(scheme://host[:port][/])만 받는 규칙을 직접 적었다

private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1")

// 호스트에 *가 들어가 CSP 와일드카드가 되지 않게 문자를 제한한다. IPv6는 대괄호 표기
private val ORIGIN = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*)://([a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*|\[[0-9a-fA-F:.]+\])(?::(\d{1,5}))?/?$""")
private val DEFAULT_PORTS = mapOf("http" to 80, "https" to 443)
private const val MAX_PORT = 65_535

/** 주소 원문은 메시지에 넣지 않는다. 사용자 정보(비밀번호)가 섞여 있을 수 있다 */
class InvalidBaseUrlException : IllegalArgumentException("서버 주소가 올바르지 않음: localhost가 아니면 HTTPS 출처(scheme://host[:port])만 쓸 수 있음")

/**
 * 빌드할 때 정한 서버 주소를 출처(scheme://host[:port])로 바꾼다. 비어 있으면 같은 출처("")를 쓴다.
 * 인증 해시와 토큰이 오가므로 localhost가 아니면 HTTPS만 허용한다. 경로·쿼리·사용자 정보가 붙은 주소는 받지 않는다
 * @throws InvalidBaseUrlException
 */
fun resolveBaseUrl(raw: String?): String {
    if (raw.isNullOrEmpty()) return ""
    val match = ORIGIN.matchEntire(raw) ?: throw InvalidBaseUrlException()
    val scheme = match.groupValues[1].lowercase()
    val host = match.groupValues[2].lowercase()
    val port = match.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
    val secure = scheme == "https" || (scheme == "http" && host in LOOPBACK_HOSTS)
    if (!secure || (port != null && port > MAX_PORT)) throw InvalidBaseUrlException()
    // 원본 URL.origin처럼 기본 포트는 지운다
    val portPart = if (port == null || port == DEFAULT_PORTS[scheme]) "" else ":$port"
    return "$scheme://$host$portPart"
}
