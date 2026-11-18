package com.keymanage.android.domain.remote

/**
 * 서버 오류 코드(api-spec 1장·2장 오류 표). 목록은 Scripts/check-api-contract.sh가 명세와 대조한다.
 * 표에 없는 코드를 받으면 [UNKNOWN]으로 본다.
 */
enum class ServerErrorCode {
    INVALID_PARAMETER,
    UNAUTHORIZED,
    TOO_MANY_ATTEMPTS,
    INTERNAL_ERROR,
    VAULT_NOT_FOUND,
    VAULT_CONFLICT,
    VAULT_TOO_LARGE,
    EMAIL_ALREADY_REGISTERED,
    REGISTRATION_DISABLED,
    AUTH_FAILED,

    /** 브라우저 출처 검사(api-spec 1장 CORS). 앱은 Origin 헤더를 보내지 않아 받을 일이 없지만 목록은 명세와 맞춘다. */
    ORIGIN_NOT_ALLOWED,
    UNKNOWN,
    ;

    companion object {
        fun of(code: String?): ServerErrorCode = entries.firstOrNull { it.name == code && it != UNKNOWN } ?: UNKNOWN
    }
}

/** 원격 호출 실패. 화면 문구는 COM-002에 따라 상위 계층이 정한다. */
sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** 서버가 오류 응답을 줬다. [retryAfterSeconds]는 429의 Retry-After. */
    class Server(
        val status: Int,
        val code: ServerErrorCode,
        val serverMessage: String?,
        val retryAfterSeconds: Long? = null,
    ) : ApiError("서버 오류 $status $code")

    /** 연결 실패·제한 시간 초과 등 응답을 받지 못했다(VLT-002 오프라인 처리 대상). */
    class Network(cause: Throwable) : ApiError("네트워크 오류", cause)

    /** 응답을 받았지만 명세 형식이 아니다. */
    class InvalidResponse(message: String) : ApiError(message)

    /** HTTPS가 아닌 원격 서버 주소(SEC-002). localhost·127.0.0.1만 HTTP를 허용한다. */
    class InsecureServer(url: String) : ApiError("HTTPS가 아닌 서버 주소: $url")
}
