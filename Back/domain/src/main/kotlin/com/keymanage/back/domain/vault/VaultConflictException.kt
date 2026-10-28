package com.keymanage.back.domain.vault

/** 기대 revision이 서버의 현재 revision과 다름 (VLT-001). API 계층에서 409 VAULT_CONFLICT로 응답한다 */
class VaultConflictException(
    val currentRevision: Revision,
) : RuntimeException("볼트가 먼저 변경됨: 현재 revision ${currentRevision.value}")
