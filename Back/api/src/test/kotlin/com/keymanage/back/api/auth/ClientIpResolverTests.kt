package com.keymanage.back.api.auth

import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientIpResolverTests {

    private fun request(remoteAddr: String, forwardedFor: String? = null) = MockHttpServletRequest().apply {
        this.remoteAddr = remoteAddr
        if (forwardedFor != null) addHeader("X-Forwarded-For", forwardedFor)
    }

    @Test
    fun `신뢰 프록시가 없으면 접속 IP를 쓴다`() {
        assertEquals("198.51.100.7", ClientIpResolver(emptySet()).resolve(request("198.51.100.7", "203.0.113.1")))
    }

    @Test
    fun `신뢰 프록시를 거친 요청은 오른쪽부터 신뢰하지 않는 첫 IP를 쓴다`() {
        val resolver = ClientIpResolver(setOf("10.0.0.1", "10.0.0.2"))

        // 클라이언트가 위조해 넣은 왼쪽 값은 건너뛰고, 신뢰 프록시가 덧붙인 오른쪽부터 본다
        assertEquals("203.0.113.9", resolver.resolve(request("10.0.0.1", "1.1.1.1, 203.0.113.9, 10.0.0.2")))
    }

    @Test
    fun `신뢰 프록시인데 X-Forwarded-For가 없거나 모두 신뢰 IP면 접속 IP를 쓴다`() {
        val resolver = ClientIpResolver(setOf("10.0.0.1"))

        assertEquals("10.0.0.1", resolver.resolve(request("10.0.0.1")))
        assertEquals("10.0.0.1", resolver.resolve(request("10.0.0.1", "10.0.0.1")))
    }
}
