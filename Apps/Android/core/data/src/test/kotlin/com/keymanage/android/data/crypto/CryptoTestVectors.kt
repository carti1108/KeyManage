package com.keymanage.android.data.crypto

import org.json.JSONObject
import java.io.File
import java.util.Base64

/** docs/crypto-test-vectors.json 읽기 도우미. */
object CryptoTestVectors {
    val root: JSONObject by lazy {
        val path = System.getProperty("km.cryptoVectors") ?: error("km.cryptoVectors 시스템 속성이 없다")
        JSONObject(File(path).readText(Charsets.UTF_8))
    }

    fun keyDerivationCases(): List<JSONObject> = root.getJSONArray("keyDerivationCases").let { arr ->
        (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    fun encryptionCases(): List<JSONObject> = root.getJSONArray("encryptionCases").let { arr ->
        (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    fun iterations(): Int = root.getJSONObject("kdf").getInt("iterations")

    fun bytes(base64: String): ByteArray = Base64.getDecoder().decode(base64)
}
