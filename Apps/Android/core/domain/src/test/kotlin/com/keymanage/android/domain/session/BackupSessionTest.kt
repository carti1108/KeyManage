package com.keymanage.android.domain.session

import com.keymanage.android.domain.backup.BackupCodec
import com.keymanage.android.domain.backup.BackupFile
import com.keymanage.android.domain.backup.BackupUseCase
import com.keymanage.android.domain.crypto.CryptoError
import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import com.keymanage.android.domain.crypto.KdfParameters
import com.keymanage.android.domain.crypto.KeySeparator
import com.keymanage.android.domain.crypto.MasterKeyDeriver
import com.keymanage.android.domain.crypto.SeparatedKeys
import com.keymanage.android.domain.crypto.VaultCipher
import com.keymanage.android.domain.sync.DeviceAccount
import com.keymanage.android.domain.sync.SaveOutcome
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultItem
import com.keymanage.android.domain.vault.VaultSerializer
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackupSessionTest {
    private val t0 = Instant.parse("2026-11-01T00:00:00Z")
    private val now = Instant.parse("2026-11-18T09:00:00Z")
    private val salt = ByteArray(16) { 5 }
    private val account = DeviceAccount("dev@example.com", 700_000, Base64.getEncoder().encodeToString(salt))
    private val key = "pw".toByteArray().copyOf(32)
    private val opened = OpenedVault(account, vault(), null, key)
    private val files = mutableMapOf<String, BackupFile>()
    private val saved = mutableListOf<VaultData>()
    private val session = BackupSession(BackupUseCase(EchoDeriver, Passthrough, KeyTagCipher, IdsSerializer, MemoryCodec(files))) { _, v ->
        saved += v
        SavedVault(v, SaveOutcome.Pending)
    }

    private fun vault(vararg ids: String) = VaultData(
        1, t0, ids.map { VaultItem(id = it, serviceName = it, username = "u", password = "p", createdAt = t0, updatedAt = t0) },
    )

    @Test
    fun `내보내기는 계정 KDF 값과 지금 키를 쓴다`() {
        val bytes = session.export(opened, vault("a"), now)
        val file = files.getValue(String(bytes))
        assertEquals(700_000, file.kdfIterations)
        assertEquals(account.kdfSalt, file.kdfSalt)
    }

    @Test
    fun `복원하면 되돌린 볼트를 저장하고 개수를 돌려준다`() {
        val bytes = session.export(opened, vault("a"), now)
        val result = session.restore(opened, vault("x"), bytes, "pw", now)
        assertEquals(1, result.restored)
        assertEquals(listOf("x", "a"), saved.single().items.map { it.id })
    }

    @Test
    fun `되돌릴 항목이 없으면 저장하지 않는다`() {
        val bytes = session.export(opened, vault("a"), now)
        val result = session.restore(opened, vault("a"), bytes, "pw", now)
        assertEquals(0, result.restored)
        assertNull(result.saved)
        assertEquals(emptyList(), saved)
    }

    private object EchoDeriver : MasterKeyDeriver {
        override fun deriveMasterKey(password: ByteArray, parameters: KdfParameters) = password.copyOf(32)
    }

    private object Passthrough : KeySeparator {
        override fun separate(masterKey: ByteArray) = SeparatedKeys(masterKey.copyOf(), ByteArray(32))
    }

    private object KeyTagCipher : VaultCipher {
        private fun tag(key: ByteArray) = Base64.getEncoder().encodeToString(key)
        override fun encrypt(plaintext: ByteArray, key: ByteArray) =
            EncryptedVaultPayload(1, "AES-256-GCM", "", Base64.getEncoder().encodeToString(plaintext), tag(key))

        override fun decrypt(payload: EncryptedVaultPayload, key: ByteArray): ByteArray {
            if (payload.tag != tag(key)) throw CryptoError.AuthenticationFailed()
            return Base64.getDecoder().decode(payload.ciphertext)
        }
    }

    private object IdsSerializer : VaultSerializer {
        private val t0 = Instant.parse("2026-11-01T00:00:00Z")
        override fun encode(vault: VaultData) = vault.items.joinToString(",") { it.id }.toByteArray()
        override fun decode(bytes: ByteArray) = VaultData(
            1, t0, String(bytes).split(",").filter { it.isNotEmpty() }
                .map { VaultItem(id = it, serviceName = it, username = "u", password = "p", createdAt = t0, updatedAt = t0) },
        )
    }

    private class MemoryCodec(private val files: MutableMap<String, BackupFile>) : BackupCodec {
        override fun encode(file: BackupFile): ByteArray = "f${files.size}".also { files[it] = file }.toByteArray()
        override fun decode(bytes: ByteArray) = files.getValue(String(bytes))
    }
}
