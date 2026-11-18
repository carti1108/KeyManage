package com.keymanage.android.data.backup

import com.keymanage.android.data.crypto.AesGcmVaultCipher
import com.keymanage.android.data.crypto.HkdfKeySeparator
import com.keymanage.android.data.crypto.Pbkdf2MasterKeyDeriver
import com.keymanage.android.data.vault.JsonVaultSerializer
import com.keymanage.android.domain.backup.BackupError
import com.keymanage.android.domain.backup.BackupFile
import com.keymanage.android.domain.backup.BackupUseCase
import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import com.keymanage.android.domain.crypto.KdfParameters
import com.keymanage.android.domain.crypto.PasswordPreprocessor
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultItem
import org.json.JSONObject
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class JsonBackupCodecTest {
    private val codec = JsonBackupCodec()
    private val file = BackupFile(
        createdAt = Instant.parse("2026-11-18T09:00:00Z"),
        kdfIterations = 600_000,
        kdfSalt = "dGhpcyBpcyBhIHNhbHQxNg==",
        vault = EncryptedVaultPayload(1, "AES-256-GCM", "aXY=", "Y3Q=", "dGFn"),
    )

    private fun json(mutate: JSONObject.() -> Unit = {}) =
        JSONObject(String(codec.encode(file))).apply(mutate).toString().toByteArray()

    @Test
    fun `vault-spec 6장 필드로 쓰고 그대로 읽는다`() {
        // When
        val text = String(codec.encode(file))

        // Then
        val root = JSONObject(text)
        assertEquals("keymanage-backup", root.getString("format"))
        assertEquals(1, root.getInt("formatVersion"))
        assertEquals("2026-11-18T09:00:00Z", root.getString("createdAt"))
        assertEquals("PBKDF2-HMAC-SHA256", root.getJSONObject("kdf").getString("name"))
        assertFalse(root.has("email"))
        assertEquals(file, codec.decode(text.toByteArray()))
    }

    @Test
    fun `format이 다르거나 JSON이 아니면 백업 파일이 아니다`() {
        assertFailsWith<BackupError.NotBackupFile> { codec.decode(json { put("format", "other") }) }
        assertFailsWith<BackupError.NotBackupFile> { codec.decode("{not json".toByteArray()) }
        assertFailsWith<BackupError.NotBackupFile> { codec.decode("[]".toByteArray()) }
    }

    @Test
    fun `KDF 이름이 다르거나 필드 타입이 틀리면 백업 파일이 아니다`() {
        assertFailsWith<BackupError.NotBackupFile> { codec.decode(json { getJSONObject("kdf").put("name", "scrypt") }) }
        assertFailsWith<BackupError.NotBackupFile> { codec.decode(json { getJSONObject("kdf").put("iterations", "600000") }) }
        assertFailsWith<BackupError.NotBackupFile> { codec.decode(json { remove("vault") }) }
        assertFailsWith<BackupError.NotBackupFile> { codec.decode(json { put("formatVersion", 0) }) }
    }

    @Test
    fun `형식 버전이 더 높으면 NewerVersion`() {
        assertFailsWith<BackupError.NewerVersion> { codec.decode(json { put("formatVersion", 2) }) }
    }

    @Test
    fun `파일이 5MB + 64KB를 넘으면 백업 파일이 아니다`() {
        val oversized = ByteArray(JsonBackupCodec.MAX_FILE_BYTES + 1) { ' '.code.toByte() }
        assertFailsWith<BackupError.NotBackupFile> { codec.decode(oversized) }
    }

    @Test
    fun `vault 필드 압축 JSON이 5MB를 넘으면 들여쓰기 여유와 별개로 거절한다`() {
        // Given: 파일 전체는 한도 안이지만 vault 필드만 5MB를 1바이트 넘는다
        val head = """{"version":1,"cipher":"AES-256-GCM","iv":"aXY=","ciphertext":"","tag":"dGFn"}""".length
        val ciphertext = "A".repeat(5_242_880 - head + 1)
        val big = codec.encode(file.copy(vault = file.vault.copy(ciphertext = ciphertext)))

        // Then
        assertFailsWith<BackupError.NotBackupFile> { codec.decode(big) }
    }

    @Test
    fun `실제 암호 구현으로 내보낸 백업을 같은 비밀번호로 복원한다`() {
        // Given
        val t0 = Instant.parse("2026-11-01T00:00:00Z")
        val vault = VaultData(1, t0, listOf(VaultItem(id = "a", serviceName = "s", username = "u", password = "secret", createdAt = t0, updatedAt = t0)))
        val kdf = KdfParameters(ByteArray(16) { 3 }, 600_000)
        val useCase = BackupUseCase(Pbkdf2MasterKeyDeriver(), HkdfKeySeparator(), AesGcmVaultCipher(), JsonVaultSerializer(), codec)
        val master = Pbkdf2MasterKeyDeriver().deriveMasterKey(PasswordPreprocessor.preprocess("pw"), kdf)
        val key = HkdfKeySeparator().separate(master).encryptionKey

        // When
        val bytes = useCase.export(vault, key, kdf, t0)

        // Then
        assertFalse(String(bytes).contains("secret"))
        assertEquals("secret", useCase.open(bytes, "pw").items.single().password)
        assertFailsWith<BackupError.CannotOpen> { useCase.open(bytes, "wrong") }
    }
}
