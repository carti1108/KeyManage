package com.keymanage.apple

import com.keymanage.feature.unlock.BiometricRecall
import com.keymanage.feature.unlock.BiometricUnlock
import com.keymanage.feature.unlock.UnlockMessages
import com.keymanage.securestore.BiometricAuthenticationException
import com.keymanage.securestore.BiometricAvailability
import com.keymanage.securestore.BiometricKeychainStore
import com.keymanage.securestore.BiometricResult
import com.keymanage.securestore.BiometricSecureStore
import com.keymanage.securestore.LocalBiometricAuthenticator
import com.keymanage.securestore.SecureStoreException
import platform.Foundation.NSUserDefaults
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/** 생체 보호 키체인 항목의 서비스 이름 */
const val BIOMETRIC_KEYCHAIN_SERVICE = "com.keymanage.ios.biometric-unlock"

// 생체 해제 iOS 구현 (SEC-003, ADR-0027). 비밀번호로 연 직후 세션이 내보낸 암호화 키(32바이트)를 이 기기 전용·현재 등록된 생체만 허용하는 키체인 항목에 두고
// (BiometricKeychainStore, kSecAccessControlBiometryCurrentSet), 켰는지와 마지막 비밀번호 해제 시각은 비밀이 아니므로 UserDefaults에 둔다.
// 생체 정보가 바뀌면 키체인이 항목을 내주지 않으므로 Invalidated로 알린다 (SEC-003 8번)
class KeychainBiometricUnlock(
    private val authenticator: LocalBiometricAuthenticator = LocalBiometricAuthenticator(),
    private val store: BiometricSecureStore = BiometricKeychainStore(BIOMETRIC_KEYCHAIN_SERVICE, authenticator),
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : BiometricUnlock {
    override fun isAvailable(): Boolean = authenticator.availability() == BiometricAvailability.AVAILABLE

    override fun isEnrolled(email: String): Boolean = passwordUnlockedAt(email) != null

    override fun passwordUnlockedAt(email: String): Instant? =
        defaults.stringForKey(unlockedAtKey(email))?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)

    override suspend fun remember(email: String, unlockKey: ByteArray, passwordUnlockedAt: Instant) {
        store.saveProtected(email, unlockKey)
        defaults.setObject(passwordUnlockedAt.toEpochMilliseconds().toString(), unlockedAtKey(email))
    }

    override suspend fun recall(email: String): BiometricRecall = try {
        store.loadProtected(email, UnlockMessages.BIOMETRIC_REASON)?.let { BiometricRecall.Key(it) }
            ?: BiometricRecall.Invalidated
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: BiometricAuthenticationException) {
        if (error.result == BiometricResult.Cancelled) BiometricRecall.Cancelled else BiometricRecall.Failed
    } catch (_: SecureStoreException) {
        // 생체 등록이 바뀌었거나 기기 암호를 꺼 항목을 읽을 수 없다
        BiometricRecall.Invalidated
    }

    override fun forget(email: String) {
        defaults.removeObjectForKey(unlockedAtKey(email))
        try {
            store.delete(email)
        } catch (_: SecureStoreException) {
            // 표시를 지웠으므로 다시 켜기 전까지 쓰지 않는다. 남은 항목은 다음 remember가 덮어쓴다
        }
    }

    /** 이 기기에서 생체 해제를 켠 모든 계정의 표시와 키체인 항목을 지운다. UI 테스트 초기화가 쓴다 */
    fun forgetAll() {
        defaults.dictionaryRepresentation().keys
            .mapNotNull { (it as? String)?.removePrefix(UNLOCKED_AT_PREFIX)?.takeIf { email -> email != it } }
            .forEach(::forget)
    }

    private fun unlockedAtKey(email: String) = "$UNLOCKED_AT_PREFIX$email"
}

private const val UNLOCKED_AT_PREFIX = "keymanage.biometric.passwordUnlockedAt."
