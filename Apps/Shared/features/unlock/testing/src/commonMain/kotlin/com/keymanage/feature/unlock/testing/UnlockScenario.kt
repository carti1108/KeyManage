package com.keymanage.feature.unlock.testing

import com.keymanage.domain.auth.MasterPasswordChangedException
import com.keymanage.domain.device.DeviceStorageException
import com.keymanage.domain.lock.BiometricError
import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.session.SessionExpiredException
import com.keymanage.domain.session.WrongMasterPasswordException
import com.keymanage.domain.vault.RevisionRollbackException
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * 잠금 화면을 한 가지 상황으로 띄우는 시나리오. Android·iOS·웹의 Example 앱이 같은 목록을 보여 주고,
 * 고른 시나리오의 [session]으로 Unlock RIB을 만든다
 */
enum class UnlockScenario(val title: String) {
    SUCCESS("성공 (잠금 해제)"),
    OFFLINE("오프라인 (기기에 저장한 볼트로 엶)"),
    WRONG_PASSWORD("틀린 비밀번호"),
    SLOW("지연 (2초 뒤 잠금 해제)"),
    NETWORK_ERROR("서버 오류 문구"),
    ROLLBACK("되돌림 경고 (받아들이면 열림)"),
    SESSION_EXPIRED("세션 만료 (로그인 화면으로)"),
    PASSWORD_CHANGED("다른 기기에서 비밀번호 변경 (로그인 화면으로)"),
    LOGOUT_WITH_PENDING("로그아웃 확인 (올리지 못한 변경 있음)"),
    LOGOUT_CLEAR_FAILED("로그아웃 (기기 기록을 지우지 못함)"),
    BIOMETRIC("생체 해제 (성공)"),
    BIOMETRIC_CANCELLED("생체 해제 (인증 창 취소)"),
    BIOMETRIC_EXPIRED("생체 해제 (14일 지나 비밀번호 요구)"),
    BIOMETRIC_INVALIDATED("생체 해제 (생체 정보가 바뀌어 꺼짐)"),
    BIOMETRIC_PASSWORD_REQUIRED("생체 해제 (세션 만료로 비밀번호 요구)"),
    ;

    /** 이 상황을 만드는 가짜 세션 */
    fun session(): FakeUnlockSession = FakeUnlockSession().also { session ->
        session.email = "user@example.com"
        when (this) {
            SUCCESS -> Unit
            OFFLINE -> session.unlockResult = true
            WRONG_PASSWORD -> session.unlockHandler = { _, _ -> throw WrongMasterPasswordException() }
            SLOW -> session.unlockHandler = { _, _ ->
                delay(2.seconds)
                false
            }
            NETWORK_ERROR -> session.unlockHandler = { _, _ -> throw NetworkError() }
            ROLLBACK -> session.unlockHandler = { _, acceptRollback ->
                if (!acceptRollback) throw RevisionRollbackException(lastSeenRevision = 7, serverRevision = 5)
                false
            }
            SESSION_EXPIRED -> session.unlockHandler = { _, _ -> throw SessionExpiredException() }
            PASSWORD_CHANGED -> session.unlockHandler = { _, _ -> throw MasterPasswordChangedException() }
            LOGOUT_WITH_PENDING -> session.pendingSync = true
            LOGOUT_CLEAR_FAILED -> session.logoutHandler = { throw DeviceStorageException() }
            BIOMETRIC -> session.biometricsUsable = true
            BIOMETRIC_CANCELLED -> {
                session.biometricsUsable = true
                session.biometricUnlockHandler = { throw BiometricError.Cancelled() }
            }
            // 세션은 14일이 지났거나 키가 무효면 생체 해제를 더 제안하지 않는다
            BIOMETRIC_EXPIRED -> {
                session.biometricsUsable = true
                session.biometricUnlockHandler = {
                    session.biometricsUsable = false
                    throw BiometricError.Expired()
                }
            }
            BIOMETRIC_PASSWORD_REQUIRED -> session.biometricPasswordRequired = true
            BIOMETRIC_INVALIDATED -> {
                session.biometricsUsable = true
                session.biometricUnlockHandler = {
                    session.biometricsUsable = false
                    throw BiometricError.Invalidated()
                }
            }
        }
    }
}
