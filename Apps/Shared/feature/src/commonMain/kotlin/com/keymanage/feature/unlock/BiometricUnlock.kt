package com.keymanage.feature.unlock

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

// 생체 해제 (SEC-003 4·8번). iOS만 넣는 선택 기능이고 웹은 넣지 않는다(포트가 없으면 화면이 기능을 숨긴다) (domain-migration-plan 12.3)

/** 생체 인증을 거쳐 꺼낸 결과 */
sealed interface BiometricRecall {
    /** 저장해 둔 생체 해제용 키(암호화 키 사본). 쓴 뒤 받은 쪽이 지운다 */
    class Key(val bytes: ByteArray) : BiometricRecall {
        override fun toString(): String = "Key(***)"
    }

    /** 사용자가 인증 창을 닫았다 */
    data object Cancelled : BiometricRecall

    /** 기기에 생체 정보가 새로 등록돼 저장한 비밀을 읽을 수 없다 (SEC-003 8번) */
    data object Invalidated : BiometricRecall

    /** 인증에 실패했다(여러 번 틀림, 잠김 등) */
    data object Failed : BiometricRecall
}

/**
 * 계정마다 생체 해제용 키(비밀번호로 연 직후 세션이 내보낸 암호화 키 사본)를 이 기기 전용·생체 보호 저장소에 두고 꺼낸다 (SEC-003 "생체 해제용 키", ADR-0027).
 * 마스터 비밀번호는 저장하지 않는다.
 * 14일 재요구 판단에 쓰는 마지막 비밀번호 해제 시각도 함께 둔다
 */
interface BiometricUnlock {
    /** 기기가 생체 인증을 지원하고 등록돼 있음 */
    fun isAvailable(): Boolean
    fun isEnrolled(email: String): Boolean
    fun passwordUnlockedAt(email: String): Instant?

    /** 비밀번호로 연 뒤 저장한다. 처음이면 생체 해제를 켜고, 이미 켰으면 키와 재요구 기준 시각을 새로 한다. [unlockKey]는 부른 쪽이 지운다 */
    suspend fun remember(email: String, unlockKey: ByteArray, passwordUnlockedAt: Instant)
    suspend fun recall(email: String): BiometricRecall

    /** 생체 해제를 끈다(로그아웃, 계정 삭제, 마스터 비밀번호 변경, 생체 정보 변경, 저장한 키가 맞지 않음) */
    fun forget(email: String)
}

/** 생체 해제에 쓰는 세션 기능 (ADR-0027). 실제 구현은 [com.keymanage.feature.SessionPortAdapter] */
interface UnlockKeySession {
    /** 비밀번호로 열고 생체 해제용 키 사본을 받는다. 받은 키는 저장소에 넘긴 뒤 지운다 */
    suspend fun unlockExportingKey(password: String, acceptRollback: Boolean): KeyedUnlock

    /**
     * 저장해 둔 키로 연다. @return 오프라인으로 열었으면 true
     * @throws com.keymanage.domain.session.StaleUnlockKeyException 키로 볼트를 열 수 없음
     */
    suspend fun unlockWithKey(unlockKey: ByteArray, acceptRollback: Boolean): Boolean
}

class KeyedUnlock(val offline: Boolean, val unlockKey: ByteArray) {
    override fun toString(): String = "KeyedUnlock(offline=$offline, unlockKey=***)"
}

/** 생체 해제가 켜져 있어도 이만큼 지나면 마스터 비밀번호를 다시 받는다 (SEC-003 경계값) */
val BIOMETRIC_PASSWORD_REPROMPT_INTERVAL: Duration = 14.days

/** 잠금 화면 생체 해제 문구. 원본: iOS LockError, VaultLockUIStrings */
object UnlockMessages {
    const val BIOMETRIC_BUTTON: String = "생체 인증으로 잠금 해제"
    const val BIOMETRIC_ENROLL: String = "다음부터 생체 인증으로 잠금 해제"
    const val BIOMETRIC_REASON: String = "볼트 잠금 해제"
    const val BIOMETRIC_REPROMPT: String = "보안을 위해 마스터 비밀번호를 다시 입력해 주세요."
    const val BIOMETRIC_INVALIDATED: String = "생체 정보가 변경되었습니다. 마스터 비밀번호를 입력해 주세요."
    const val BIOMETRIC_FAILED: String = "생체 인증에 실패했습니다. 마스터 비밀번호를 입력해 주세요."
}
