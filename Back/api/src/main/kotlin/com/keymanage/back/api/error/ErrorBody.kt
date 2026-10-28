package com.keymanage.back.api.error

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 표준 오류 응답 본문 (api-spec 1장). `currentRevision`은 409 충돌일 때만 담는다 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorBody(
    val code: String,
    val message: String,
    val timestamp: String,
    val currentRevision: Long? = null,
) {
    companion object {
        fun of(error: ApiError, currentRevision: Long? = null) = ErrorBody(
            code = error.name,
            message = error.message,
            timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            currentRevision = currentRevision,
        )
    }
}
