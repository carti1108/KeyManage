package com.keymanage.back.domain.auth

import com.keymanage.back.domain.vault.VaultId
import java.time.Instant

/**
 * 저장되는 세션. 토큰 원문 대신 [tokenHash]만 가진다.
 * [vaultId]는 발급할 때 계정이 가리키던 볼트다. 비밀번호 변경은 볼트 ID를 바꾸므로, 그 전에 발급된 세션은 인증 때 거절된다 (SEC-004 규칙 7)
 */
data class Session(val tokenHash: String, val email: Email, val expiresAt: Instant, val vaultId: VaultId)

/** 클라이언트에 한 번만 돌려주는 토큰 원문과 만료 시각 */
data class IssuedSession(val token: String, val expiresAt: Instant)
