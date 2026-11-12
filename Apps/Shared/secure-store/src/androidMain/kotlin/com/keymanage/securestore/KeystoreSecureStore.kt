package com.keymanage.securestore

import android.content.Context
import java.security.GeneralSecurityException

class KeystoreSecureStore(context: Context, service: String) : SecureStore {
    private val alias = "keymanage.securestore.$service"
    private val prefs = context.getSharedPreferences(alias, Context.MODE_PRIVATE)

    override fun save(account: String, value: ByteArray) = guard("저장") {
        val cipher = KeystoreCipher.encryptCipher(KeystoreCipher.key(alias))
        prefs.writeSealed(account, KeystoreCipher.seal(cipher, value))
    }

    override fun load(account: String): ByteArray? = guard("조회") {
        val sealed = prefs.readSealed(account) ?: return@guard null
        KeystoreCipher.open(KeystoreCipher.decryptCipher(KeystoreCipher.key(alias), sealed), sealed)
    }

    override fun delete(account: String) = prefs.writeSealed(account, null)

    private inline fun <T> guard(operation: String, block: () -> T): T = try {
        block()
    } catch (e: GeneralSecurityException) {
        throw SecureStoreException(-1, operation, e)
    }
}
