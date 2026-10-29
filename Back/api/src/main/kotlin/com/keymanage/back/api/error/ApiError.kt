package com.keymanage.back.api.error

import org.springframework.http.HttpStatus

/** 응답 오류 코드와 사용자 문구 (api-spec 1장·2.3~2.5, ADR-0013, VLT-001, SEC-002) */
enum class ApiError(val status: HttpStatus, val message: String) {
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),
    AUTH_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 마스터 비밀번호가 올바르지 않습니다."),
    REGISTRATION_DISABLED(HttpStatus.FORBIDDEN, "현재 새 계정을 만들 수 없습니다."),
    ORIGIN_NOT_ALLOWED(HttpStatus.FORBIDDEN, "허용되지 않은 출처의 요청입니다."),
    VAULT_NOT_FOUND(HttpStatus.NOT_FOUND, "저장된 볼트가 없습니다."),
    VAULT_CONFLICT(HttpStatus.CONFLICT, "다른 기기에서 볼트가 먼저 변경되었습니다."),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    VAULT_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "볼트 허용 용량(5MB)을 초과했습니다."),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 서버 오류입니다. 잠시 후 다시 시도해 주세요."),
}
