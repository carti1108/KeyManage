package com.keymanage.androidsession

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.keymanage.domain.lock.BiometricError
import com.keymanage.domain.lock.BiometricKeyStore
import com.keymanage.securestore.BiometricAuthenticationException
import com.keymanage.securestore.BiometricKeystoreEntries
import com.keymanage.securestore.BiometricKeystoreStore
import com.keymanage.securestore.BiometricResult
import java.security.GeneralSecurityException
import java.security.InvalidKeyException

/**
 * 볼트 암호화 키를 Android Keystore의 생체 전용 키로 감싸 이 기기에만 둔다 (SEC-003 규칙 4·8, ADR-AND-002).
 * 감싸고 푸는 일은 secure-store의 [BiometricKeystoreStore]가 하고, 여기서는 그 실패를 공유 도메인의 [BiometricError]로 바꾼다.
 *
 * @param activity 생체 인증 창을 띄울 화면. 화면이 없으면(백그라운드) null을 돌려주고, 그때는 인증 창을 띄우지 않는다
 */
class KeystoreBiometricKeyStore(
    context: Context,
    private val service: String = SERVICE,
    private val activity: () -> FragmentActivity?,
) : BiometricKeyStore {
    private val entries = BiometricKeystoreEntries(context, service)

    override fun isAvailable(): Boolean = entries.isBiometricAvailable()

    override fun hasKey(): Boolean = entries.has(ACCOUNT)

    override suspend fun seal(key: ByteArray) {
        val store = store() ?: throw BiometricError.Unavailable()
        try {
            store.saveProtected(ACCOUNT, key)
        } catch (_: InvalidKeyException) {
            // 생체 정보가 바뀌어 예전 보호 키가 무효다. 지우고 새 키로 한 번 더 한다
            entries.deleteKey()
            try {
                store.saveProtected(ACCOUNT, key)
            } catch (error: BiometricAuthenticationException) {
                throw promptError(error.result)
            } catch (_: GeneralSecurityException) {
                throw BiometricError.Unavailable()
            }
        } catch (error: BiometricAuthenticationException) {
            throw promptError(error.result)
        } catch (_: GeneralSecurityException) {
            throw BiometricError.Unavailable()
        }
    }

    override suspend fun open(): ByteArray {
        if (!hasKey()) throw BiometricError.Invalidated()
        val store = store() ?: throw BiometricError.Unavailable()
        return try {
            store.loadProtected(ACCOUNT, PROMPT_TITLE) ?: throw BiometricError.Invalidated()
        } catch (error: BiometricAuthenticationException) {
            throw promptError(error.result)
        } catch (_: GeneralSecurityException) {
            // 생체 정보가 새로 등록돼 키가 무효이거나(KeyPermanentlyInvalidatedException) 감싼 값이 이 키의 것이 아니다
            throw BiometricError.Invalidated()
        }
    }

    override fun clear() {
        runCatching { entries.delete(ACCOUNT) }
        runCatching { entries.deleteKey() }
    }

    private fun store(): BiometricKeystoreStore? = activity()?.let { BiometricKeystoreStore(it, service) }

    /** 인증 창이 실패로 끝난 이유. 기기에서 생체 인증을 쓸 수 없는 경우만 가르고, 나머지(닫음·여러 번 불일치)는 취소로 본다 */
    private fun promptError(result: BiometricResult): BiometricError = when (result) {
        is BiometricResult.Failed -> if (result.code.toInt() in UNAVAILABLE_CODES) BiometricError.Unavailable() else BiometricError.Cancelled()
        BiometricResult.Cancelled, BiometricResult.Success -> BiometricError.Cancelled()
    }

    private companion object {
        const val SERVICE = "vaultKey"
        const val ACCOUNT = "encryptionKey"

        /** 생체 인증 창의 제목 (SEC-003) */
        const val PROMPT_TITLE = "생체 인증으로 잠금 해제"

        val UNAVAILABLE_CODES = setOf(
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
        )
    }
}
