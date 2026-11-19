package com.keymanage.android.data.crypto

import com.keymanage.android.data.backup.JsonBackupCodec
import com.keymanage.android.data.vault.JsonVaultSerializer
import com.keymanage.android.domain.backup.BackupUseCase
import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import com.keymanage.android.domain.vault.VaultData
import org.json.JSONObject
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 다른 플랫폼이 암호화한 볼트를 Android에서 끝까지 연다(VLT-001).
 * 공용 벡터의 암호문은 웹 구현으로 만들었고 iOS도 같은 벡터를 통과한다.
 */
class CrossPlatformVaultTest {
    private val keyCases = CryptoTestVectors.keyDerivationCases().associateBy { it.getString("name") }
    private val case = CryptoTestVectors.encryptionCases().first()
    private val keyCase = keyCases.getValue(case.getString("keyFrom"))

    @Test
    fun `다른 플랫폼이 암호화한 볼트를 복호화해 평문 볼트로 읽는다`() {
        // Given
        val key = CryptoTestVectors.bytes(keyCase.getString("encryptionKey"))
        val payload = payloadOf(case.getJSONObject("payload"))

        // When
        val vault = JsonVaultSerializer().decode(AesGcmVaultCipher().decrypt(payload, key))

        // Then
        assertSampleVault(vault)
    }

    @Test
    fun `다른 플랫폼 암호문을 담은 백업 파일을 마스터 비밀번호만으로 연다`() {
        // Given: vault-spec 6장 백업 파일
        val file = JSONObject()
            .put("format", "keymanage-backup")
            .put("formatVersion", 1)
            .put("createdAt", "2026-11-01T12:00:00Z")
            .put(
                "kdf",
                JSONObject()
                    .put("name", "PBKDF2-HMAC-SHA256")
                    .put("iterations", CryptoTestVectors.iterations())
                    .put("salt", keyCase.getString("salt")),
            )
            .put("vault", case.getJSONObject("payload"))
        val backup = BackupUseCase(
            Pbkdf2MasterKeyDeriver(), HkdfKeySeparator(), AesGcmVaultCipher(), JsonVaultSerializer(), JsonBackupCodec(),
        )

        // When
        val vault = backup.open(file.toString().toByteArray(Charsets.UTF_8), keyCase.getString("password"))

        // Then
        assertSampleVault(vault)
    }

    private fun assertSampleVault(vault: VaultData) {
        val expected = JSONObject(case.getString("plaintext"))
        val expectedItem = expected.getJSONArray("items").getJSONObject(0)
        assertEquals(expected.getInt("version"), vault.version)
        assertEquals(Instant.parse(expected.getString("updatedAt")), vault.updatedAt)
        assertFalse(vault.isReadOnly)
        val item = vault.items.single()
        assertEquals(expectedItem.getString("id"), item.id)
        assertEquals(expectedItem.getString("serviceName"), item.serviceName)
        assertEquals(expectedItem.getString("username"), item.username)
        assertEquals(expectedItem.getString("password"), item.password)
        assertEquals(expectedItem.getString("url"), item.url)
        assertEquals(expectedItem.getString("notes"), item.notes)
        assertEquals(Instant.parse(expectedItem.getString("createdAt")), item.createdAt)
    }

    private fun payloadOf(json: JSONObject) = EncryptedVaultPayload(
        version = json.getInt("version"),
        cipher = json.getString("cipher"),
        iv = json.getString("iv"),
        ciphertext = json.getString("ciphertext"),
        tag = json.getString("tag"),
    )
}
