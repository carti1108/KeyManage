package com.keymanage.back.domain.vault

/** 볼트 페이로드가 허용 용량을 넘음 (VLT-001). API 계층에서 413 VAULT_TOO_LARGE로 응답한다 */
class VaultTooLargeException(
    val sizeInBytes: Int,
) : RuntimeException("볼트 용량 초과: ${sizeInBytes}바이트")
