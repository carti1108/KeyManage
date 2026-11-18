package com.keymanage.android.domain.backup

import com.keymanage.android.domain.crypto.CryptoError
import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import com.keymanage.android.domain.crypto.KdfParameters
import com.keymanage.android.domain.crypto.KeySeparator
import com.keymanage.android.domain.crypto.MasterKeyDeriver
import com.keymanage.android.domain.crypto.SeparatedKeys
import com.keymanage.android.domain.crypto.VaultCipher
import com.keymanage.android.domain.sync.SyncError
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultItem
import com.keymanage.android.domain.vault.VaultSerializer
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class BackupUseCaseTest {
    private val t0 = Instant.parse("2026-11-01T00:00:00Z")
    private val now = Instant.parse("2026-11-18T09:00:00Z")
    private val salt = ByteArray(16) { 7 }
    private val kdf = KdfParameters(salt, 600_000)
    private val codec = MemoryCodec()
    private val useCase = BackupUseCase(PasswordDeriver, PassThroughSeparator, XorCipher, IdsSerializer, codec)

    private fun vault(vararg ids: String, version: Int = 1) = VaultData(
        version, t0, ids.map { VaultItem(id = it, serviceName = it, username = "u", password = "p", createdAt = t0, updatedAt = t0) },
    )

    /** "pw"로 만든 암호화 키. 테스트 유도기는 비밀번호 바이트를 그대로 키로 쓴다. */
    private val key = PasswordDeriver.deriveMasterKey("pw".toByteArray(), kdf)

    @Test
    fun `내보내기는 지금 키와 계정 KDF 값으로 암호문만 담는다`() {
        // When
        val bytes = useCase.export(vault("a", "b"), key, kdf, now)

        // Then
        val file = codec.files.getValue(String(bytes))
        assertEquals(600_000, file.kdfIterations)
        assertContentEquals(salt, Base64.getDecoder().decode(file.kdfSalt))
        assertEquals(now, file.createdAt)
        assertFalse(file.vault.ciphertext.contains("a,b"))
    }

    @Test
    fun `내보낸 파일을 같은 비밀번호로 열면 볼트가 돌아온다`() {
        val bytes = useCase.export(vault("a", "b"), key, kdf, now)
        assertEquals(listOf("a", "b"), useCase.open(bytes, "pw").items.map { it.id })
    }

    @Test
    fun `틀린 비밀번호는 CannotOpen`() {
        val bytes = useCase.export(vault("a"), key, kdf, now)
        assertFailsWith<BackupError.CannotOpen> { useCase.open(bytes, "wrong") }
    }

    @Test
    fun `KDF 값이 범위 밖이면 백업 파일이 아니다`() {
        // Given
        val bytes = useCase.export(vault("a"), key, kdf, now)
        codec.files[String(bytes)] = codec.files.getValue(String(bytes)).copy(kdfIterations = 1_000)

        // Then
        assertFailsWith<BackupError.NotBackupFile> { useCase.open(bytes, "pw") }
    }

    @Test
    fun `salt가 Base64가 아니면 백업 파일이 아니다`() {
        val bytes = useCase.export(vault("a"), key, kdf, now)
        codec.files[String(bytes)] = codec.files.getValue(String(bytes)).copy(kdfSalt = "!!")
        assertFailsWith<BackupError.NotBackupFile> { useCase.open(bytes, "pw") }
    }

    @Test
    fun `볼트 스키마 버전이 더 높으면 NewerVersion`() {
        val bytes = useCase.export(vault("a", version = 2), key, kdf, now)
        assertFailsWith<BackupError.NewerVersion> { useCase.open(bytes, "pw") }
    }

    @Test
    fun `복원은 백업 항목을 되돌리고 개수를 돌려준다`() {
        // Given
        val bytes = useCase.export(vault("a"), key, kdf, now)

        // When
        val result = useCase.restore(vault("x"), bytes, "pw", now)

        // Then
        assertEquals(listOf("x", "a"), result.vault.items.map { it.id })
        assertEquals(1, result.restored)
    }

    @Test
    fun `읽기 전용 볼트에는 복원하지 않는다`() {
        val bytes = useCase.export(vault("a"), key, kdf, now)
        assertFailsWith<SyncError.ReadOnlyVault> { useCase.restore(vault("x", version = 2), bytes, "pw", now) }
    }

    @Test
    fun `파일 이름은 현지 날짜를 쓴다`() {
        assertEquals("keymanage-backup-20261118.json", BackupUseCase.fileName(LocalDate.of(2026, 11, 18)))
    }

    private object PasswordDeriver : MasterKeyDeriver {
        override fun deriveMasterKey(password: ByteArray, parameters: KdfParameters): ByteArray {
            if (parameters.iterations != 600_000) error("파일의 KDF 값을 써야 한다")
            return password.copyOf(32)
        }
    }

    private object PassThroughSeparator : KeySeparator {
        override fun separate(masterKey: ByteArray) = SeparatedKeys(masterKey.copyOf(), ByteArray(32))
    }

    /** 키의 첫 바이트로 XOR하고, 키가 다르면 인증 실패를 낸다. */
    private object XorCipher : VaultCipher {
        override fun encrypt(plaintext: ByteArray, key: ByteArray) = EncryptedVaultPayload(
            1, "AES-256-GCM", "", Base64.getEncoder().encodeToString(plaintext.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()),
            Base64.getEncoder().encodeToString(key),
        )

        override fun decrypt(payload: EncryptedVaultPayload, key: ByteArray): ByteArray {
            if (payload.tag != Base64.getEncoder().encodeToString(key)) throw CryptoError.AuthenticationFailed()
            return Base64.getDecoder().decode(payload.ciphertext).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        }
    }

    private object IdsSerializer : VaultSerializer {
        private val t0 = Instant.parse("2026-11-01T00:00:00Z")
        override fun encode(vault: VaultData) = "${vault.version}|${vault.items.joinToString(",") { it.id }}".toByteArray()
        override fun decode(bytes: ByteArray): VaultData {
            val (version, ids) = String(bytes).split("|")
            return VaultData(
                version.toInt(), t0,
                ids.split(",").filter { it.isNotEmpty() }
                    .map { VaultItem(id = it, serviceName = it, username = "u", password = "p", createdAt = t0, updatedAt = t0) },
            )
        }
    }

    /** 파일 바이트를 열쇠로 백업 내용을 메모리에 둔다. */
    private class MemoryCodec : BackupCodec {
        val files = mutableMapOf<String, BackupFile>()
        override fun encode(file: BackupFile): ByteArray {
            val id = "file-${files.size}"
            files[id] = file
            return id.toByteArray()
        }

        override fun decode(bytes: ByteArray) = files.getValue(String(bytes))
    }
}
