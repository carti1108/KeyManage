package com.keymanage.android.data.crypto

import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import com.keymanage.android.domain.crypto.KdfParameters
import com.keymanage.android.domain.crypto.PasswordPreprocessor
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 플랫폼 공용 벡터를 그대로 재현하는지 확인한다(vault-spec 2장 "플랫폼 간 테스트 벡터"). */
class CryptoVectorTest {
    private val deriver = Pbkdf2MasterKeyDeriver()
    private val separator = HkdfKeySeparator()
    private val cipher = AesGcmVaultCipher()

    @Test
    fun `모든 키 유도 벡터의 전처리 결과와 마스터 키, 분리 키가 같다`() {
        val cases = CryptoTestVectors.keyDerivationCases()
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val name = case.getString("name")
            // Given
            val parameters = KdfParameters(CryptoTestVectors.bytes(case.getString("salt")), CryptoTestVectors.iterations())

            // When
            val normalized = PasswordPreprocessor.preprocess(case.getString("password"))
            val masterKey = deriver.deriveMasterKey(normalized, parameters)
            val keys = separator.separate(masterKey)

            // Then
            assertContentEquals(CryptoTestVectors.bytes(case.getString("normalizedPasswordUtf8")), normalized, name)
            assertContentEquals(CryptoTestVectors.bytes(case.getString("masterKey")), masterKey, name)
            assertContentEquals(CryptoTestVectors.bytes(case.getString("encryptionKey")), keys.encryptionKey, name)
            assertContentEquals(CryptoTestVectors.bytes(case.getString("authHash")), keys.authHash, name)
        }
    }

    @Test
    fun `암호화 벡터의 페이로드를 복호화하면 평문이 나온다`() {
        val keyCases = CryptoTestVectors.keyDerivationCases().associateBy { it.getString("name") }
        for (case in CryptoTestVectors.encryptionCases()) {
            // Given
            val key = CryptoTestVectors.bytes(keyCases.getValue(case.getString("keyFrom")).getString("encryptionKey"))
            val payload = payloadOf(case.getJSONObject("payload"))

            // When
            val plaintext = cipher.decrypt(payload, key)

            // Then
            assertEquals(case.getString("aad"), payload.additionalAuthenticatedData)
            assertEquals(case.getString("plaintext"), plaintext.toString(Charsets.UTF_8))
        }
    }

    @Test
    fun `고정 IV로 암호화하면 벡터와 같은 암호문과 태그가 나온다`() {
        val keyCases = CryptoTestVectors.keyDerivationCases().associateBy { it.getString("name") }
        for (case in CryptoTestVectors.encryptionCases()) {
            // Given
            val key = CryptoTestVectors.bytes(keyCases.getValue(case.getString("keyFrom")).getString("encryptionKey"))
            val expected = payloadOf(case.getJSONObject("payload"))
            val fixedIv = CryptoTestVectors.bytes(expected.iv)
            val fixedCipher = AesGcmVaultCipher(ivSource = { fixedIv.copyOf() })

            // When
            val payload = fixedCipher.encrypt(case.getString("plaintext").toByteArray(Charsets.UTF_8), key)

            // Then
            assertEquals(expected, payload)
        }
    }

    private fun payloadOf(json: org.json.JSONObject) = EncryptedVaultPayload(
        version = json.getInt("version"),
        cipher = json.getString("cipher"),
        iv = json.getString("iv"),
        ciphertext = json.getString("ciphertext"),
        tag = json.getString("tag"),
    )
}
