package com.keymanage.android.data.lock

import com.keymanage.android.domain.lock.BiometricError
import com.keymanage.android.domain.lock.BiometricKeyStore
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 생체 인증 창을 띄워 [cipher]를 쓸 수 있게 한다. 실패는 [BiometricError]로 던진다. */
fun interface BiometricAuthenticator {
    suspend fun authenticate(cipher: Cipher): Cipher
}

/** 볼트 키를 감쌀 보호 키. 실제 구현은 [AndroidKeystoreKeys]. */
interface BiometricSecretKeys {
    fun existing(): SecretKey?

    fun create(): SecretKey

    fun delete()

    /** 키가 무효면 [InvalidKeyException](`KeyPermanentlyInvalidatedException` 포함)을 던진다. */
    fun init(cipher: Cipher, mode: Int, key: SecretKey, iv: ByteArray?) {
        if (iv == null) cipher.init(mode, key) else cipher.init(mode, key, GCMParameterSpec(TAG_BITS, iv))
    }

    companion object {
        const val TAG_BITS = 128
    }
}

/**
 * 볼트 암호화 키를 생체 전용 보호 키(AES-256-GCM)로 감싸 파일에 둔다(ADR-AND-002).
 * 앱은 백업에서 빠지는 `noBackupFilesDir` 아래 파일을 넘긴다.
 */
class KeystoreBiometricKeyStore(
    private val file: File,
    private val keys: BiometricSecretKeys,
    private val authenticator: BiometricAuthenticator,
) : BiometricKeyStore {
    override fun hasKey(): Boolean = file.exists() && keys.existing() != null

    override suspend fun seal(key: ByteArray) {
        val cipher = encryptCipher()
        val sealed = try {
            authenticator.authenticate(cipher).doFinal(key)
        } catch (error: GeneralSecurityException) {
            throw BiometricError.Unavailable()
        }
        write(JSONObject().put(IV, encode(cipher.iv)).put(CIPHERTEXT, encode(sealed)))
    }

    override suspend fun open(): ByteArray {
        val key = keys.existing() ?: throw BiometricError.Invalidated()
        val (iv, sealed) = read()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            keys.init(cipher, Cipher.DECRYPT_MODE, key, iv)
        } catch (error: InvalidKeyException) {
            throw BiometricError.Invalidated()
        }
        return try {
            authenticator.authenticate(cipher).doFinal(sealed)
        } catch (error: GeneralSecurityException) {
            throw BiometricError.Invalidated()
        }
    }

    override fun clear() {
        file.delete()
        keys.delete()
    }

    /** 무효가 된 보호 키는 지우고 한 번 새로 만든다. */
    private fun encryptCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val current = keys.existing() ?: keys.create()
        try {
            keys.init(cipher, Cipher.ENCRYPT_MODE, current, null)
        } catch (error: InvalidKeyException) {
            keys.delete()
            try {
                keys.init(cipher, Cipher.ENCRYPT_MODE, keys.create(), null)
            } catch (again: InvalidKeyException) {
                throw BiometricError.Unavailable()
            }
        }
        return cipher
    }

    private fun read(): Pair<ByteArray, ByteArray> = try {
        val json = JSONObject(file.readText(Charsets.UTF_8))
        decode(json.getString(IV)) to decode(json.getString(CIPHERTEXT))
    } catch (error: IOException) {
        throw BiometricError.Invalidated()
    } catch (error: JSONException) {
        throw BiometricError.Invalidated()
    } catch (error: IllegalArgumentException) {
        throw BiometricError.Invalidated()
    }

    private fun write(json: JSONObject) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            temp.writeText(json.toString(), Charsets.UTF_8)
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (error: IOException) {
            temp.delete()
            throw BiometricError.Unavailable()
        }
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String) = Base64.getDecoder().decode(text)

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV = "iv"
        const val CIPHERTEXT = "ciphertext"
    }
}
