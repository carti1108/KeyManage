package com.keymanage.back.domain.auth

import java.time.Instant

/** 저장되는 세션. 토큰 원문 대신 [tokenHash]만 가진다 */
data class Session(val tokenHash: String, val email: Email, val expiresAt: Instant)

/** 클라이언트에 한 번만 돌려주는 토큰 원문과 만료 시각 */
data class IssuedSession(val token: String, val expiresAt: Instant)
