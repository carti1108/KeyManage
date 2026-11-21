package com.keymanage.applesession

import com.keymanage.domain.lock.BiometricError
import com.keymanage.domain.lock.BiometricKeyStore
import com.keymanage.securestore.BiometricAuthenticationException
import com.keymanage.securestore.BiometricAvailability
import com.keymanage.securestore.BiometricKeychainStore
import com.keymanage.securestore.BiometricResult
import com.keymanage.securestore.LocalBiometricAuthenticator
import com.keymanage.securestore.SecureStoreException
import platform.Foundation.NSUserDefaults
import platform.LocalAuthentication.LAErrorBiometryLockout
import platform.LocalAuthentication.LAErrorBiometryNotAvailable
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAErrorPasscodeNotSet

/**
 * 볼트 암호화 키를 현재 등록된 생체 정보로만 열리는 키체인 항목에 둔다 (SEC-003 규칙 4·8, KeystoreBiometricKeyStore의 짝).
 * 생체 정보가 새로 등록되거나 기기 암호가 꺼지면 iOS가 항목을 열지 못하게 하므로, 그때는 [BiometricError.Invalidated]로 끝난다.
 * 항목을 읽으려면 인증 창이 떠야 해서, 감싼 키가 있는지는 UserDefaults 표시로 판단한다(비밀 값은 두지 않는다)
 */
class KeychainBiometricKeyStore(
    private val authenticator: LocalBiometricAuthenticator = LocalBiometricAuthenticator(),
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : BiometricKeyStore {
    private val keychain = BiometricKeychainStore(SERVICE, authenticator)

    override fun isAvailable(): Boolean = authenticator.availability() == BiometricAvailability.AVAILABLE

    override fun hasKey(): Boolean = defaults.boolForKey(SEALED_KEY)

    override suspend fun seal(key: ByteArray) {
        if (!isAvailable()) throw BiometricError.Unavailable()
        try {
            keychain.saveProtected(ACCOUNT, key)
        } catch (_: SecureStoreException) {
            throw BiometricError.Unavailable()
        }
        defaults.setBool(true, forKey = SEALED_KEY)
    }

    override suspend fun open(): ByteArray {
        if (!hasKey()) throw BiometricError.Invalidated()
        return try {
            keychain.loadProtected(ACCOUNT, PROMPT_REASON) ?: throw BiometricError.Invalidated()
        } catch (error: BiometricAuthenticationException) {
            throw promptError(error.result)
        } catch (_: SecureStoreException) {
            throw BiometricError.Invalidated()
        }
    }

    override fun clear() {
        defaults.removeObjectForKey(SEALED_KEY)
        runCatching { keychain.delete(ACCOUNT) }
    }

    /** 인증 창이 실패로 끝난 이유. 기기에서 생체 인증을 쓸 수 없는 경우만 가르고, 나머지(닫음·불일치)는 취소로 본다 */
    private fun promptError(result: BiometricResult): BiometricError = when (result) {
        is BiometricResult.Failed -> if (result.code in UNAVAILABLE_CODES) BiometricError.Unavailable() else BiometricError.Cancelled()
        BiometricResult.Cancelled, BiometricResult.Success -> BiometricError.Cancelled()
    }

    private companion object {
        const val SERVICE = "vaultKey"
        const val ACCOUNT = "encryptionKey"
        const val SEALED_KEY = "biometricKeySealed"

        /** 인증 창에 보일 이유 (SEC-003) */
        const val PROMPT_REASON = "생체 인증으로 잠금 해제"

        val UNAVAILABLE_CODES = setOf(LAErrorBiometryNotAvailable, LAErrorBiometryNotEnrolled, LAErrorBiometryLockout, LAErrorPasscodeNotSet)
    }
}
