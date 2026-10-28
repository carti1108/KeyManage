package com.keymanage.back.api.error

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.time.Duration

/** 컨트롤러 밖(필터)에서 표준 오류 본문을 직접 쓴다 (api-spec 1장) */
object ErrorResponses {

    private val objectMapper = jacksonObjectMapper()

    fun write(response: HttpServletResponse, error: ApiError, retryAfter: Duration? = null) {
        response.status = error.status.value()
        if (retryAfter != null) response.setHeader(HttpHeaders.RETRY_AFTER, retryAfterSeconds(retryAfter).toString())
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(response.outputStream, ErrorBody.of(error))
    }

    /** Retry-After는 초 단위 정수다. 남은 시간이 1초 미만이어도 바로 재시도하지 않도록 올림한다 */
    fun retryAfterSeconds(retryAfter: Duration): Long = maxOf(1, (retryAfter.toMillis() + 999) / 1000)
}
