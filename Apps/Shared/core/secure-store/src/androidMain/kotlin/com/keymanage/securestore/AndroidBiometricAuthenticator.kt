package com.keymanage.securestore

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

// BiometricPrompt는 인증 창을 띄울 FragmentActivity가 필요하다. 앱 진입점이 넘기는 유일한 화면 정보다
class AndroidBiometricAuthenticator(private val activity: FragmentActivity) : BiometricAuthenticator {

    override fun availability(): BiometricAvailability =
        when (BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
            else -> BiometricAvailability.UNAVAILABLE
        }

    override suspend fun authenticate(reason: String): BiometricResult = prompt(reason, null).first

    // 성공하면 인증된 CryptoObject를 돌려준다. 인증 필요 키는 이 CryptoObject의 Cipher로만 쓸 수 있다
    internal suspend fun prompt(
        reason: String,
        crypto: BiometricPrompt.CryptoObject?,
    ): Pair<BiometricResult, BiometricPrompt.CryptoObject?> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    continuation.resume(BiometricResult.Success to result.cryptoObject)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    continuation.resume(toResult(errorCode, errString) to null)
                }
                // onAuthenticationFailed는 불일치 1회이고 창이 그대로 다시 시도를 받으므로 끝내지 않는다
            }
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(reason)
                .setNegativeButtonText("취소")
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .build()
            if (crypto == null) prompt.authenticate(info) else prompt.authenticate(info, crypto)
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }

    private fun toResult(code: Int, message: CharSequence): BiometricResult = when (code) {
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        BiometricPrompt.ERROR_CANCELED,
        -> BiometricResult.Cancelled
        else -> BiometricResult.Failed(code.toLong(), message.toString())
    }
}
