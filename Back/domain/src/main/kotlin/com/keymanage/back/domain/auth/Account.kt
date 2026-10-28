package com.keymanage.back.domain.auth

import com.keymanage.back.domain.vault.VaultId

/** 가입한 계정. 계정당 볼트는 1개다 (ADR-0008). [passwordHash]는 인증 해시를 Argon2id로 다시 해시한 값이다 */
data class Account(
    val email: Email,
    val kdf: KdfParams,
    val passwordHash: String,
    val vaultId: VaultId,
)
