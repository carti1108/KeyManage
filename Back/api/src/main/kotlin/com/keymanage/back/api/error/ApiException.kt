package com.keymanage.back.api.error

/** 도메인 오류가 아닌, API 계층에서 판단한 실패 (요청 형식 오류, 볼트 없음 등) */
class ApiException(val error: ApiError) : RuntimeException(error.name)
