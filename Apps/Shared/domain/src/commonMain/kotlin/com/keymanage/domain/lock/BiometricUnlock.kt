package com.keymanage.domain.lock

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

// 생체 해제 판정 (SEC-003 규칙 4·8, ADR-AND-002). 웹은 생체 인증이 없어 쓰지 않는다
// 원본: Apps/Android/core/domain/src/main/.../lock/BiometricUnlock.kt

/** 생체 해제 실패 */
sealed class BiometricError(message: String) : Exception(message) {
    /** 사용자가 생체 인증 창을 닫았거나 여러 번 실패했다. 설정은 그대로 둔다 */
    class Cancelled : BiometricError("생체 인증 취소")

    /** 기기에 강한 생체 인증이나 기기 암호가 없다 */
    class Unavailable : BiometricError("생체 인증을 쓸 수 없음")

    /** 생체 정보가 새로 등록됐거나 기기 암호를 꺼서 키가 무효다. 마스터 비밀번호를 요구한다 */
    class Invalidated : BiometricError("생체 해제 키가 무효")

    /** 마지막 마스터 비밀번호 해제 뒤 14일이 지났다. 키는 남긴다 */
    class Expired : BiometricError("마스터 비밀번호 재요구")

    /** 생체 해제를 켜지 않았다 */
    class Disabled : BiometricError("생체 해제 꺼짐")
}

/**
 * 볼트 암호화 키를 생체 인증으로 보호해 이 기기에만 두는 포트(Android Keystore, iOS 키체인).
 * [seal]·[open]은 생체 인증 창을 띄우고, 실패는 [BiometricError]로 던진다
 */
interface BiometricKeyStore {
    /** 이 기기에서 지금 생체 인증을 쓸 수 있는지(강한 생체 인증이 등록돼 있고 기기 암호가 켜져 있다) */
    fun isAvailable(): Boolean

    fun hasKey(): Boolean

    suspend fun seal(key: ByteArray)

    suspend fun open(): ByteArray

    /** 감싼 키와 보호 키를 지운다. 실패해도 던지지 않는다 */
    fun clear()
}

/** 생체 해제 설정. 비밀이 아니라 일반 저장소에 둔다 */
data class BiometricSetting(val enabled: Boolean = false, val lastPasswordUnlock: Instant? = null)

/** 읽지 못하면 기본값(꺼짐)을 돌려준다 */
interface BiometricSettingStore {
    fun read(): BiometricSetting

    fun write(setting: BiometricSetting)
}

/** 마지막 마스터 비밀번호 해제 뒤 생체 해제를 허용하는 기간 (SEC-003 값 표) */
val BIOMETRIC_PASSWORD_INTERVAL: Duration = 14.days

/** 생체 정보 변경 감지는 플랫폼 키 무효화(Keystore, 키체인 접근 제어)가 맡는다 */
class BiometricUnlock(
    private val keys: BiometricKeyStore,
    private val settings: BiometricSettingStore,
    private val clock: Clock,
) {
    /** 생체 해제를 켤 수 있는 기기인지. 설정 화면이 켜기 스위치를 보일지 정한다 */
    val isAvailable: Boolean get() = keys.isAvailable()

    val isEnabled: Boolean get() = settings.read().enabled

    /** 잠금 화면에 생체 해제를 보일지 */
    fun canUseBiometrics(): Boolean {
        val setting = settings.read()
        return setting.enabled && keys.hasKey() && isFresh(setting)
    }

    /** 마스터 비밀번호로 잠금을 풀거나 로그인한 시각을 남긴다 */
    fun onPasswordUnlock() {
        settings.write(settings.read().copy(lastPasswordUnlock = clock.now()))
    }

    /** 생체 인증을 거쳐 [encryptionKey]를 감싸 두고 켠다. 실패하면 켜지 않는다 */
    suspend fun enable(encryptionKey: ByteArray) {
        keys.seal(encryptionKey)
        settings.write(settings.read().copy(enabled = true))
    }

    /** 끄고 감싼 키를 지운다. 로그아웃·계정 삭제·비밀번호 변경 때도 부른다 */
    fun disable() {
        keys.clear()
        settings.write(settings.read().copy(enabled = false))
    }

    /** 생체 인증으로 볼트 암호화 키를 꺼낸다. 키가 무효면 지우고 끈다 */
    suspend fun recoverKey(): ByteArray {
        val setting = settings.read()
        if (!setting.enabled) throw BiometricError.Disabled()
        if (!keys.hasKey()) {
            disable()
            throw BiometricError.Invalidated()
        }
        if (!isFresh(setting)) throw BiometricError.Expired()
        return try {
            keys.open()
        } catch (error: BiometricError.Invalidated) {
            disable()
            throw error
        }
    }

    private fun isFresh(setting: BiometricSetting): Boolean {
        val last = setting.lastPasswordUnlock ?: return false
        return clock.now() - last <= BIOMETRIC_PASSWORD_INTERVAL
    }
}
