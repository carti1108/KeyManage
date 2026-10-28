package com.keymanage.back.domain.vault

/** 볼트 ID가 UUID 형식이 아님 (ADR-0007). API 계층에서 400으로 응답한다 */
class InvalidVaultIdException(
    val raw: String,
) : RuntimeException("볼트 ID는 UUID 형식이어야 함")
