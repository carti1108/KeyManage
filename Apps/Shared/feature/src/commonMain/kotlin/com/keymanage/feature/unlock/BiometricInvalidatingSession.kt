package com.keymanage.feature.unlock

import com.keymanage.domain.session.SessionState
import com.keymanage.feature.SessionPort

/**
 * 생체 해제용 키를 지워야 하는 세션 작업을 감싼다 (SEC-003, ADR-0027).
 * 마스터 비밀번호를 바꾸면 옛 키로 새 볼트를 열 수 없으므로 지우고, 계정 삭제·로그아웃으로 이 기기에서 나가면 지운다.
 * 작업이 실패해도 세션이 로그인 전 상태로 바뀌었으면(세션 만료, 다른 곳의 비밀번호 변경) 지운다. 생체 정보 변경은 키체인이 항목을 내주지 않아 잠금 화면이 지운다
 */
class BiometricInvalidatingSession(
    private val inner: SessionPort,
    private val biometric: BiometricUnlock,
) : SessionPort by inner {
    override suspend fun changeMasterPassword(currentPassword: String, newPassword: String) =
        forgetAfter { inner.changeMasterPassword(currentPassword, newPassword) }

    override suspend fun deleteAccount(password: String) = forgetAfter { inner.deleteAccount(password) }

    override suspend fun logout() = forgetAfter { inner.logout() }

    private suspend fun forgetAfter(action: suspend () -> Unit) {
        val email = emailOf(inner.state()) ?: return action()
        try {
            action()
        } catch (error: Throwable) {
            if (inner.state() is SessionState.SignedOut) biometric.forget(email)
            throw error
        }
        biometric.forget(email)
    }

    private fun emailOf(state: SessionState): String? = when (state) {
        is SessionState.Locked -> state.email
        is SessionState.Unlocked -> state.email
        SessionState.SignedOut -> null
    }
}
