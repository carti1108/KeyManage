package com.keymanage.securestore

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG

/**
 * [BiometricKeystoreStore]가 둔 값에 대해 인증 창 없이 할 수 있는 일: 있는지 보기, 지우기, 생체 인증을 쓸 수 있는 기기인지 보기.
 * 화면(Activity)이 없어도 쓸 수 있어, 잠금 화면에 생체 해제를 보일지 정하거나 로그아웃 때 지울 때 쓴다
 */
class BiometricKeystoreEntries(context: Context, service: String) {
    private val appContext = context.applicationContext
    private val alias = "keymanage.biometric.$service"
    private val prefs = appContext.getSharedPreferences(alias, Context.MODE_PRIVATE)

    /** 감싼 값과 그것을 풀 Keystore 키가 모두 있는지. 기기 암호를 끄면 키가 사라진다 */
    fun has(account: String): Boolean = prefs.contains(account) && KeystoreCipher.existingKey(alias) != null

    fun delete(account: String) = prefs.writeSealed(account, null)

    fun deleteKey() = KeystoreCipher.deleteKey(alias)

    /** 강한 생체 인증이 등록돼 있어 지금 인증 창을 띄울 수 있는지 */
    fun isBiometricAvailable(): Boolean =
        BiometricManager.from(appContext).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
}
