package com.keymanage.android.domain.session

import com.keymanage.android.domain.auth.AuthResult
import com.keymanage.android.domain.crypto.CryptoError
import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import com.keymanage.android.domain.crypto.KdfParameters
import com.keymanage.android.domain.crypto.KeySeparator
import com.keymanage.android.domain.crypto.MasterKeyDeriver
import com.keymanage.android.domain.crypto.SeparatedKeys
import com.keymanage.android.domain.crypto.VaultCipher
import com.keymanage.android.domain.lock.BiometricError
import com.keymanage.android.domain.remote.ApiError
import com.keymanage.android.domain.remote.AuthApi
import com.keymanage.android.domain.remote.ChangePasswordRequest
import com.keymanage.android.domain.remote.ChangePasswordResult
import com.keymanage.android.domain.remote.PreloginInfo
import com.keymanage.android.domain.remote.RemoteVault
import com.keymanage.android.domain.remote.ServerErrorCode
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.remote.VaultApi
import com.keymanage.android.domain.sync.DeviceAccount
import com.keymanage.android.domain.sync.DeviceRecord
import com.keymanage.android.domain.sync.DeviceRecordStore
import com.keymanage.android.domain.sync.VaultSyncCoordinator
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultItem
import com.keymanage.android.domain.vault.VaultSerializer
import java.io.IOException
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultSessionUseCaseTest {
    private val t0 = Instant.parse("2026-11-18T00:00:00Z")
    private val salt = ByteArray(16) { 4 }
    private val account = DeviceAccount("dev@example.com", 600_000, Base64.getEncoder().encodeToString(salt))
    private val session = Session("tok", Instant.parse("2026-12-18T00:00:00Z"))
    private val authApi = FakeAuthApi()
    private val vaultApi = FakeVaultApi()
    private val store = MemoryStore()
    private val coordinator = VaultSyncCoordinator(vaultApi, KeyTagCipher, IdsSerializer, store)
    private val useCase = VaultSessionUseCase(authApi, vaultApi, EchoDeriver, SplitSeparator, KeyTagCipher, IdsSerializer, coordinator, clock = { t0 })

    private fun keyOf(password: String) = SplitSeparator.separate(EchoDeriver.deriveMasterKey(password.toByteArray(), KdfParameters(salt, 600_000))).encryptionKey
    private fun auth(password: String = "password") = AuthResult(session, keyOf(password), account)

    @Test
    fun `서버에 볼트가 없으면 빈 볼트를 저장하고 연다`() {
        // When
        val opened = useCase.openAfterAuth(auth())

        // Then
        assertTrue(opened.vault.items.isEmpty())
        assertEquals(1, vaultApi.saveCalls)
        assertEquals(1, store.record?.baseRevision)
    }

    @Test
    fun `서버 볼트를 복호화해 열고 기기 사본으로 둔다`() {
        // Given
        vaultApi.server = RemoteVault(5, KeyTagCipher.encrypt(IdsSerializer.encode(vault("a", "b")), keyOf("password")))

        // When
        val opened = useCase.openAfterAuth(auth())

        // Then
        assertEquals(listOf("a", "b"), opened.vault.items.map { it.id })
        assertEquals(5, store.record?.lastSeenRevision)
        assertFalse(store.record!!.hasPending)
        assertEquals(0, vaultApi.saveCalls)
    }

    @Test
    fun `기기에 대기 내용이 있으면 서버 볼트와 병합해 올린다`() {
        // Given: 오프라인에서 a를 저장해 대기로 남겼다
        vaultApi.server = RemoteVault(5, KeyTagCipher.encrypt(IdsSerializer.encode(vault("b")), keyOf("password")))
        useCase.openAfterAuth(auth())
        vaultApi.offline = true
        coordinator.save(vault("a", "b"), keyOf("password"), "tok")
        vaultApi.offline = false

        // When: 다시 로그인
        val opened = useCase.openAfterAuth(auth())

        // Then
        assertEquals(listOf("a", "b"), opened.vault.items.map { it.id }.sorted())
        assertFalse(store.record!!.hasPending)
    }

    @Test
    fun `재시작 뒤 잠금 화면은 기기 기록의 계정으로 연다`() {
        assertNull(useCase.lockedAccount())
        useCase.openAfterAuth(auth())
        assertEquals("dev@example.com", useCase.lockedAccount()?.email)
    }

    @Test
    fun `틀린 비밀번호로는 열리지 않는다`() {
        useCase.openAfterAuth(auth())
        assertFailsWith<UnlockError.WrongPassword> { useCase.unlock("wrong", session) }
    }

    @Test
    fun `세션이 있으면 다시 로그인하지 않고 연다`() {
        useCase.openAfterAuth(auth())
        val opened = useCase.unlock("password", session)
        assertEquals(0, authApi.loginCalls)
        assertEquals(session, opened.session)
    }

    @Test
    fun `세션이 없으면 인증 해시로 다시 로그인한다`() {
        useCase.openAfterAuth(auth())
        val opened = useCase.unlock("password", null)
        assertEquals(1, authApi.loginCalls)
        assertEquals(authApi.issued, opened.session)
    }

    @Test
    fun `서버에 닿지 못하면 세션 없이 기기 볼트를 열고 이유를 남긴다`() {
        // Given
        vaultApi.server = RemoteVault(5, KeyTagCipher.encrypt(IdsSerializer.encode(vault("a")), keyOf("password")))
        useCase.openAfterAuth(auth())
        authApi.offline = true

        // When
        val opened = useCase.unlock("password", null)

        // Then
        assertNull(opened.session)
        assertEquals(listOf("a"), opened.vault.items.map { it.id })
        assertIs<ApiError.Network>(opened.offlineReason)
    }

    @Test
    fun `서버 KDF 값이 기기 값과 다르면 비밀번호가 바뀐 것으로 본다`() {
        useCase.openAfterAuth(auth())
        authApi.iterations = 700_000
        assertFailsWith<UnlockError.PasswordChanged> { useCase.unlock("password", null) }
    }

    @Test
    fun `로그인이 인증 실패면 비밀번호가 바뀐 것으로 본다`() {
        useCase.openAfterAuth(auth())
        authApi.rejectLogin = true
        assertFailsWith<UnlockError.PasswordChanged> { useCase.unlock("password", null) }
    }

    @Test
    fun `새 비밀번호로 기기 볼트가 안 열리고 서버 KDF가 다르면 비밀번호가 바뀐 것으로 본다`() {
        useCase.openAfterAuth(auth())
        authApi.iterations = 700_000
        assertFailsWith<UnlockError.PasswordChanged> { useCase.unlock("new-password", null) }
    }

    @Test
    fun `기기 기록이 없으면 잠금을 풀 수 없다`() {
        assertFailsWith<UnlockError.NoDeviceRecord> { useCase.unlock("password", session) }
    }

    @Test
    fun `로그아웃은 서버에 닿지 못해도 기기 기록을 지운다`() {
        useCase.openAfterAuth(auth())
        authApi.offline = true
        useCase.logout(session)
        assertNull(useCase.lockedAccount())
    }

    @Test
    fun `세션이 있으면 서버에 저장하고 병합 결과를 돌려준다`() {
        // Given
        val opened = useCase.openAfterAuth(auth())

        // When
        val saved = useCase.save(opened, vault("a"))

        // Then
        assertEquals(com.keymanage.android.domain.sync.SaveOutcome.Synced(2), saved.outcome)
        assertEquals(listOf("a"), saved.vault.items.map { it.id })
    }

    @Test
    fun `세션 없이 연 볼트의 저장은 대기로 둔다`() {
        // Given
        useCase.openAfterAuth(auth())
        authApi.offline = true
        val opened = useCase.unlock("password", null)

        // When
        val saved = useCase.save(opened, vault("a"))

        // Then
        assertEquals(com.keymanage.android.domain.sync.SaveOutcome.Pending, saved.outcome)
        assertTrue(store.record!!.hasPending)
        assertEquals(1, vaultApi.saveCalls)
    }

    @Test
    fun `생체 해제는 감싼 볼트 키로 기기 볼트를 열고 다시 로그인하지 않는다`() {
        useCase.openAfterAuth(auth())
        val opened = useCase.unlockWithKey(keyOf("password"), null)
        assertEquals(0, authApi.loginCalls)
        assertNull(opened.session)
        assertNull(opened.offlineReason)
    }

    @Test
    fun `생체 해제도 메모리 세션이 있으면 서버 볼트를 받는다`() {
        vaultApi.server = RemoteVault(5, KeyTagCipher.encrypt(IdsSerializer.encode(vault("a", "b")), keyOf("password")))
        useCase.openAfterAuth(auth())
        val opened = useCase.unlockWithKey(keyOf("password"), session)
        assertEquals(session, opened.session)
        assertEquals(listOf("a", "b"), opened.vault.items.map { it.id })
    }

    @Test
    fun `감싼 키로 기기 볼트가 열리지 않으면 생체 해제가 무효다`() {
        useCase.openAfterAuth(auth())
        assertFailsWith<BiometricError.Invalidated> { useCase.unlockWithKey(keyOf("other"), null) }
    }

    private fun vault(vararg ids: String) = VaultData(
        1, t0, ids.map { VaultItem(id = it, serviceName = it, username = "u", password = "p", createdAt = t0, updatedAt = t0) },
    )

    private object EchoDeriver : MasterKeyDeriver {
        override fun deriveMasterKey(password: ByteArray, parameters: KdfParameters) = password.copyOf(32)
    }

    private object SplitSeparator : KeySeparator {
        override fun separate(masterKey: ByteArray) = SeparatedKeys(byteArrayOf(1) + masterKey, byteArrayOf(2) + masterKey)
    }

    /** 키를 태그에 담아, 다른 키로 열면 인증 실패를 낸다. */
    private object KeyTagCipher : VaultCipher {
        private fun tagOf(key: ByteArray) = Base64.getEncoder().encodeToString(key)
        override fun encrypt(plaintext: ByteArray, key: ByteArray) =
            EncryptedVaultPayload(1, "AES-256-GCM", "", Base64.getEncoder().encodeToString(plaintext), tagOf(key))

        override fun decrypt(payload: EncryptedVaultPayload, key: ByteArray): ByteArray {
            if (payload.tag != tagOf(key)) throw CryptoError.AuthenticationFailed()
            return Base64.getDecoder().decode(payload.ciphertext)
        }
    }

    private object IdsSerializer : VaultSerializer {
        private val t0 = Instant.parse("2026-11-18T00:00:00Z")
        override fun encode(vault: VaultData) = vault.items.joinToString(",") { it.id }.toByteArray()
        override fun decode(bytes: ByteArray) = VaultData(
            1, t0, String(bytes).split(",").filter { it.isNotEmpty() }
                .map { VaultItem(id = it, serviceName = it, username = "u", password = "p", createdAt = t0, updatedAt = t0) },
        )
    }

    private inner class FakeAuthApi : AuthApi {
        var offline = false
        var rejectLogin = false
        var iterations = 600_000
        var loginCalls = 0
        val issued = Session("new-tok", Instant.parse("2026-12-18T00:00:00Z"))

        override fun prelogin(email: String): PreloginInfo {
            if (offline) throw ApiError.Network(IOException("offline"))
            return PreloginInfo("PBKDF2-HMAC-SHA256", iterations, salt)
        }

        override fun login(email: String, authHash: ByteArray): Session {
            loginCalls++
            if (rejectLogin) throw ApiError.Server(401, ServerErrorCode.AUTH_FAILED, null)
            return issued
        }

        override fun register(email: String, authHash: ByteArray, kdfSalt: ByteArray, kdfIterations: Int) = error("쓰지 않음")
        override fun logout(accessToken: String) {
            if (offline) throw ApiError.Network(IOException("offline"))
        }
        override fun changePassword(request: ChangePasswordRequest): ChangePasswordResult = error("쓰지 않음")
        override fun deleteAccount(accessToken: String, authHash: ByteArray) = Unit
    }

    private class FakeVaultApi : VaultApi {
        var server: RemoteVault? = null
        var offline = false
        var saveCalls = 0

        override fun fetchVault(accessToken: String): RemoteVault? {
            if (offline) throw ApiError.Network(IOException("offline"))
            return server
        }

        override fun saveVault(accessToken: String, expectedRevision: Int, payload: EncryptedVaultPayload): Int {
            saveCalls++
            if (offline) throw ApiError.Network(IOException("offline"))
            val current = server?.revision ?: 0
            if (expectedRevision != current) throw ApiError.Server(409, ServerErrorCode.VAULT_CONFLICT, null)
            server = RemoteVault(current + 1, payload)
            return current + 1
        }
    }

    private class MemoryStore : DeviceRecordStore {
        var record: DeviceRecord? = null
        override fun read() = record
        override fun write(record: DeviceRecord) {
            this.record = record
        }

        override fun clear() {
            record = null
        }
    }
}
