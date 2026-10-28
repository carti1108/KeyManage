package com.keymanage.back.api.error

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * 요청 본문이 [MAX_BODY_BYTES]를 넘으면 해석하기 전에 413으로 거절한다 (api-spec 1장).
 *
 * Content-Length를 알면 바로 거절하고, 모르면(chunked) 읽는 도중 상한을 넘는 순간 [RequestBodyTooLargeException]으로 끊는다.
 * 필터는 컨트롤러 밖에서 동작하므로 오류 본문을 직접 쓴다.
 */
@Component
class RequestBodyLimitFilter : OncePerRequestFilter() {

    private val objectMapper = jacksonObjectMapper()

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        if (request.contentLengthLong > MAX_BODY_BYTES) {
            response.status = ApiError.VAULT_TOO_LARGE.status.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            objectMapper.writeValue(response.outputStream, ErrorBody.of(ApiError.VAULT_TOO_LARGE))
            return
        }
        filterChain.doFilter(LimitedBodyRequest(request), response)
    }

    private class LimitedBodyRequest(request: HttpServletRequest) : HttpServletRequestWrapper(request) {

        private val limited: ServletInputStream by lazy { LimitedInputStream(request.inputStream) }

        override fun getInputStream(): ServletInputStream = limited

        override fun getReader(): BufferedReader =
            BufferedReader(InputStreamReader(limited, characterEncoding ?: Charsets.UTF_8.name()))
    }

    private class LimitedInputStream(private val delegate: ServletInputStream) : ServletInputStream() {

        private var readBytes = 0L

        override fun read(): Int = delegate.read().also { if (it != -1) count(1) }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate.read(buffer, offset, length).also { if (it > 0) count(it) }

        override fun isFinished(): Boolean = delegate.isFinished

        override fun isReady(): Boolean = delegate.isReady

        override fun setReadListener(listener: ReadListener) = delegate.setReadListener(listener)

        private fun count(bytes: Int) {
            readBytes += bytes
            if (readBytes > MAX_BODY_BYTES) throw RequestBodyTooLargeException()
        }
    }

    companion object {
        /** 볼트 페이로드 상한 5MB에 요청의 나머지 필드를 위한 여유를 더한 6MB */
        const val MAX_BODY_BYTES = 6_291_456
    }
}

/** 읽는 도중 요청 본문이 상한을 넘음 */
class RequestBodyTooLargeException : IOException("요청 본문이 상한을 넘음")
