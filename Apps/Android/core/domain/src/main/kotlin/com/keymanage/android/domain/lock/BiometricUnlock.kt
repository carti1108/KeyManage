package com.keymanage.android.domain.lock

import java.time.Duration
import java.time.Instant

/** 생체 해제 실패(SEC-003 규칙 4·8). */
sealed class BiometricError(message: String) : Exception(message) {
    /** 사용자가 생체 인증 창을 닫았거나 여러 번 실패했다. 설정은 그대로 둔다. */
    class Cancelled : BiometricError("생체 인증 취소")

    /** 기기에 강한 생체 인증이나 기기 암호가 없다. */
    class Unavailable : BiometricError("생체 인증을 쓸 수 없음")

    /** 생체 정보가 새로 등록됐거나 기기 암호를 꺼서 키가 무효다. 마스터 비밀번호를 요구한다. */
    class Invalidated : BiometricError("생체 해제 키가 무효")

    /** 마지막 마스터 비밀번호 해제 뒤 14일이 지났다. 키는 남긴다. */
    class Expired : BiometricError("마스터 비밀번호 재요구")

    /** 생체 해제를 켜지 않았다. */
    class Disabled : BiometricError("생체 해제 꺼짐")
}

/**
 * 볼트 암호화 키를 생체 인증으로 보호해 이 기기에만 둔다(ADR-AND-002).
 * [seal]·[open]은 생체 인증 창을 띄우고, 실패는 [BiometricError]로 던진다.
 */
interface BiometricKeyStore {
    fun hasKey(): Boolean

    suspend fun seal(key: ByteArray)

    suspend fun open(): ByteArray

    /** 감싼 키와 보호 키를 지운다. 실패해도 던지지 않는다. */
    fun clear()
}

/** 생체 해제 설정. 비밀이 아니라 일반 저장소에 둔다. */
data class BiometricSetting(val enabled: Boolean = false, val lastPasswordUnlock: Instant? = null)

/** 읽지 못하면 기본값(꺼짐)을 돌려준다. */
interface BiometricSettingStore {
    fun read(): BiometricSetting

    fun write(setting: BiometricSetting)
}

/**
 * 생체 해제 판정(SEC-003 규칙 4·8, 값 표 14일 재요구). Apple `VaultLockManager`와 같은 규칙이다.
 * 생체 정보 변경 감지는 Keystore 키 무효화가 맡는다.
 */
class BiometricUnlock(
    private val keys: BiometricKeyStore,
    private val settings: BiometricSettingStore,
    private val clock: () -> Instant = Instant::now,
) {
    val isEnabled: Boolean get() = settings.read().enabled

    /** 잠금 화면에 생체 해제를 보일지. */
    fun canUseBiometrics(): Boolean {
        val setting = settings.read()
        return setting.enabled && keys.hasKey() && isFresh(setting)
    }

    /** 마스터 비밀번호로 잠금을 풀거나 로그인한 시각을 남긴다. */
    fun onPasswordUnlock() {
        settings.write(settings.read().copy(lastPasswordUnlock = clock()))
    }

    /** 생체 인증을 거쳐 [encryptionKey]를 감싸 두고 켠다. 실패하면 켜지 않는다. */
    suspend fun enable(encryptionKey: ByteArray) {
        keys.seal(encryptionKey)
        settings.write(settings.read().copy(enabled = true))
    }

    /** 끄고 감싼 키를 지운다. 로그아웃·계정 삭제·비밀번호 변경 때도 부른다. */
    fun disable() {
        keys.clear()
        settings.write(settings.read().copy(enabled = false))
    }

    /** 생체 인증으로 볼트 암호화 키를 꺼낸다. 키가 무효면 지우고 끈다. */
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
        return Duration.between(last, clock()) <= PASSWORD_INTERVAL
    }

    companion object {
        val PASSWORD_INTERVAL: Duration = Duration.ofDays(14)
    }
}
