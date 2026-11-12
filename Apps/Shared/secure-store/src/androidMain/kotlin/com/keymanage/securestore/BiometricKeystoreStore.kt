package com.keymanage.securestore

import android.content.Context
import android.os.Build
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

// 생체 인증을 통과한 Cipher로만 쓸 수 있는 Keystore 키(매번 인증, 생체 등록이 바뀌면 무효화)로 값을 감싼다
class BiometricKeystoreStore(
    activity: FragmentActivity,
    service: String,
    private val authenticator: AndroidBiometricAuthenticator = AndroidBiometricAuthenticator(activity),
) : BiometricSecureStore {
    private val alias = "keymanage.biometric.$service"
    private val prefs = activity.getSharedPreferences(alias, Context.MODE_PRIVATE)

    override suspend fun saveProtected(account: String, value: ByteArray) {
        val cipher = authorize(KeystoreCipher.encryptCipher(protectedKey()), "보호된 키 저장")
        prefs.writeSealed(account, KeystoreCipher.seal(cipher, value))
    }

    override suspend fun loadProtected(account: String, reason: String): ByteArray? {
        val sealed = prefs.readSealed(account) ?: return null
        val cipher = authorize(KeystoreCipher.decryptCipher(protectedKey(), sealed), reason)
        return KeystoreCipher.open(cipher, sealed)
    }

    override fun delete(account: String) = prefs.writeSealed(account, null)

    fun deleteKey() = KeystoreCipher.deleteKey(alias)

    private suspend fun authorize(cipher: Cipher, reason: String): Cipher {
        val (result, crypto) = authenticator.prompt(reason, BiometricPrompt.CryptoObject(cipher))
        if (result != BiometricResult.Success) throw BiometricAuthenticationException(result)
        return crypto?.cipher ?: throw BiometricAuthenticationException(BiometricResult.Failed(-1, "CryptoObject 없음"))
    }

    private fun protectedKey() = KeystoreCipher.key(alias) {
        setUserAuthenticationRequired(true)
        setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            setUserAuthenticationValidityDurationSeconds(-1)
        }
    }
}
