package com.keymanage.domain.server

// 서버 통신 실패를 화면이 구분해 다룰 수 있도록 나눈 오류 (api-spec 1장)
// 원본: Apps/Web/src/domain/server/serverErrors.ts

/** api-spec에 정의된 오류 코드. 모르는 코드는 [UNKNOWN]으로 받는다 */
enum class ServerErrorCode {
    INVALID_PARAMETER,
    UNAUTHORIZED,
    TOO_MANY_ATTEMPTS,
    INTERNAL_ERROR,
    VAULT_TOO_LARGE,
    VAULT_NOT_FOUND,
    VAULT_CONFLICT,
    AUTH_FAILED,
    EMAIL_ALREADY_REGISTERED,
    REGISTRATION_DISABLED,
    ORIGIN_NOT_ALLOWED,
    UNKNOWN,
    ;

    companion object {
        private val byWire = entries.associateBy { it.name }

        /** 응답 본문의 `code` 문자열. 없거나 모르는 값이면 [UNKNOWN] */
        fun fromWire(code: String?): ServerErrorCode = code?.let(byWire::get) ?: UNKNOWN
    }
}

/** 서버와 주고받다 실패한 모든 경우. 화면은 이 타입으로 받고 종류별로 나눈다 */
sealed class ServerFailure(message: String) : Exception(message)

/** 서버가 오류 응답을 보냄 */
class ServerError(
    val status: Int,
    val code: ServerErrorCode,
    /** 429일 때 서버가 알려 준 대기 시간(초) */
    val retryAfterSeconds: Int? = null,
) : ServerFailure("서버 오류 $status ${code.name}")

/** 서버에 닿지 못함 (오프라인, 시간 초과, 연결 거부) */
class NetworkError(message: String = "서버에 연결할 수 없음") : ServerFailure(message)

/** 성공 응답이 약속한 형식이 아님. 서버와 클라이언트 버전이 어긋났을 수 있다 */
open class UnexpectedResponseError(message: String) : ServerFailure(message)
