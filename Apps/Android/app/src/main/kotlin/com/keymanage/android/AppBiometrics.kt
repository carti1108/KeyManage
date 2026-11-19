package com.keymanage.android

import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.keymanage.android.data.lock.AndroidKeystoreKeys
import com.keymanage.android.data.lock.BiometricAuthenticator
import com.keymanage.android.data.lock.KeystoreBiometricKeyStore
import com.keymanage.android.data.lock.PreferencesBiometricSettingStore
import com.keymanage.android.domain.lock.BiometricError
import com.keymanage.android.domain.lock.BiometricUnlock
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.OpenedVault
import com.keymanage.android.domain.session.VaultSessionUseCase
import com.keymanage.android.feature.root.BiometricGateway
import com.keymanage.android.feature.unlock.BiometricUnlocker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 생체 해제 조립(ADR-AND-002). 판정은 `BiometricUnlock`, 키 보관은 Keystore, 인증 창은 `BiometricPrompt`다. */
internal class AppBiometrics(private val activity: FragmentActivity, preferences: SharedPreferences) {
    val unlock = BiometricUnlock(
        KeystoreBiometricKeyStore(File(activity.noBackupFilesDir, "biometric-key.json"), AndroidKeystoreKeys(), PromptAuthenticator(activity)),
        PreferencesBiometricSettingStore(preferences),
    )

    /** 강한 생체 인증이 등록돼 있고 기기 암호가 설정돼 있는지(SEC-003 예외 1, 값 표). */
    fun isSupported(): Boolean {
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        return keyguard?.isDeviceSecure == true &&
            BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    val gateway = object : BiometricGateway {
        override val isAvailable get() = isSupported()
        override val isEnabled get() = unlock.isEnabled
        override suspend fun enable(encryptionKey: ByteArray) = unlock.enable(encryptionKey)
        override fun disable() = unlock.disable()
    }

    /** 생체 인증은 메인 스레드에서, 볼트 열기는 [workDispatcher]에서 한다. 키로 열리지 않으면 끈다. */
    fun unlocker(sessions: VaultSessionUseCase, workDispatcher: CoroutineDispatcher) = object : BiometricUnlocker {
        override fun isAvailable() = isSupported() && unlock.canUseBiometrics()

        override suspend fun unlock(session: Session?): OpenedVault {
            val key = this@AppBiometrics.unlock.recoverKey()
            return try {
                withContext(workDispatcher) { sessions.unlockWithKey(key, session) }
            } catch (error: BiometricError.Invalidated) {
                this@AppBiometrics.unlock.disable()
                throw error
            }
        }
    }
}

/** `BiometricPrompt`로 [Cipher]를 인증한다. 메인 스레드에서 부른다. */
private class PromptAuthenticator(private val activity: FragmentActivity) : BiometricAuthenticator {
    override suspend fun authenticate(cipher: Cipher): Cipher = suspendCancellableCoroutine { continuation ->
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authed = result.cryptoObject?.cipher
                    if (authed == null) continuation.resumeWithException(BiometricError.Unavailable()) else continuation.resume(authed)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    continuation.resumeWithException(errorOf(errorCode))
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(TITLE)
            .setNegativeButtonText(USE_PASSWORD)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }

    private fun errorOf(code: Int): BiometricError = when (code) {
        BiometricPrompt.ERROR_HW_NOT_PRESENT, BiometricPrompt.ERROR_HW_UNAVAILABLE, BiometricPrompt.ERROR_NO_BIOMETRICS,
        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL, BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
        -> BiometricError.Unavailable()
        else -> BiometricError.Cancelled()
    }

    private companion object {
        const val TITLE = "KeyManage 잠금 해제"
        const val USE_PASSWORD = "마스터 비밀번호 사용"
    }
}

/** 설정 파일 이름. 자동 잠금과 같은 기기 설정에 둔다. */
internal fun Context.settingsPreferences() = getSharedPreferences("settings", Context.MODE_PRIVATE)
