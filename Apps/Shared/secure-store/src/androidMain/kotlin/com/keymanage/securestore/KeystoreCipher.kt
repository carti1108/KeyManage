package com.keymanage.securestore

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Android Keystore의 AES 키로 값을 감싸고, 감싼 값(IV + 암호문)만 앱 전용 SharedPreferences에 둔다.
// 키 자체는 Keystore(하드웨어 지원 시 TEE) 밖으로 나오지 않는다
internal object KeystoreCipher {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128

    fun key(alias: String, configure: KeyGenParameterSpec.Builder.() -> Unit = {}): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as SecretKey?)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply(configure)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    fun deleteKey(alias: String) {
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(alias)
    }

    fun encryptCipher(key: SecretKey): Cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }

    fun decryptCipher(key: SecretKey, sealed: ByteArray): Cipher = Cipher.getInstance(TRANSFORMATION).apply {
        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, IV_SIZE))
    }

    fun seal(cipher: Cipher, value: ByteArray): ByteArray = cipher.iv + cipher.doFinal(value)

    fun open(cipher: Cipher, sealed: ByteArray): ByteArray = cipher.doFinal(sealed, IV_SIZE, sealed.size - IV_SIZE)
}

internal fun SharedPreferences.readSealed(account: String): ByteArray? =
    getString(account, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

internal fun SharedPreferences.writeSealed(account: String, sealed: ByteArray?) {
    val editor = edit()
    if (sealed == null) editor.remove(account) else editor.putString(account, Base64.encodeToString(sealed, Base64.NO_WRAP))
    if (!editor.commit()) throw SecureStoreException(-1, "기록")
}
