package com.keymanage.back.api.error

import org.springframework.http.HttpStatus

/** 응답 오류 코드와 사용자 문구 (api-spec 1장·2.3·2.4, VLT-001) */
enum class ApiError(val status: HttpStatus, val message: String) {
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    VAULT_NOT_FOUND(HttpStatus.NOT_FOUND, "저장된 볼트가 없습니다."),
    VAULT_CONFLICT(HttpStatus.CONFLICT, "다른 기기에서 볼트가 먼저 변경되었습니다."),
    VAULT_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "볼트 허용 용량(5MB)을 초과했습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 서버 오류입니다. 잠시 후 다시 시도해 주세요."),
}
