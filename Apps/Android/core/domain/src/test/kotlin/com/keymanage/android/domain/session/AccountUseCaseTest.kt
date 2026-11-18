package com.keymanage.android.domain.session

import com.keymanage.android.domain.auth.NewPasswordProblem
import com.keymanage.android.domain.crypto.CryptoError
import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import com.keymanage.android.domain.crypto.KdfParameters
import com.keymanage.android.domain.crypto.KeySeparator
import com.keymanage.android.domain.crypto.MasterKeyDeriver
import com.keymanage.android.domain.crypto.SeparatedKeys
import com.keymanage.android.domain.crypto.VaultCipher
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
import com.keymanage.android.domain.sync.SyncError
import com.keymanage.android.domain.sync.VaultSyncCoordinator
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultItem
import com.keymanage.android.domain.vault.VaultSerializer
import java.io.IOException
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountUseCaseTest {
    private val t0 = Instant.parse("2026-11-01T00:00:00Z")
    private val salt = ByteArray(16) { 1 }
    private val newSalt = ByteArray(16) { 2 }
    private val account = DeviceAccount("dev@example.com", 700_000, Base64.getEncoder().encodeToString(salt))
    private val session = Session("tok", Instant.parse("2026-12-18T00:00:00Z"))
    private val api = FakeAuthApi()
    private val store = MemoryStore()
    private val coordinator = VaultSyncCoordinator(NoVaultApi, KeyTagCipher, IdsSerializer, store).apply {
        onAuthenticated(account)
        open(account, 4)
    }
    private val useCase = AccountUseCase(api, SaltedDeriver, Split, KeyTagCipher, IdsSerializer, coordinator) { newSalt.copyOf() }

    private fun keyOf(password: String, kdfSalt: ByteArray = salt) =
        Split.separate(SaltedDeriver.deriveMasterKey(password.toByteArray(), KdfParameters(kdfSalt, 700_000))).encryptionKey

    private fun opened(withSession: Session? = session) = OpenedVault(account, vault("a"), withSession, keyOf("current-password"))

    private fun vault(vararg ids: String, version: Int = 1) = VaultData(
        version, t0, ids.map { VaultItem(id = it, serviceName = it, username = "u", password = "p", createdAt = t0, updatedAt = t0) },
    )

    @Test
    fun `비밀번호를 바꾸면 새 salt·같은 반복 횟수로 새 키를 만들어 한 번에 교체한다`() {
        // When
        val changed = useCase.changePassword(opened(), vault("a", "b"), "current-password", "new-password!", "new-password!")

        // Then
        val request = api.changeRequests.single()
        assertEquals(700_000, request.kdfIterations)
        assertContentEquals(newSalt, request.kdfSalt)
        assertEquals(4, request.expectedRevision)
        assertContentEquals(keyOf("new-password!", newSalt), changed.encryptionKey)
        assertEquals("new-tok", changed.session?.accessToken)
        assertEquals(Base64.getEncoder().encodeToString(newSalt), store.record?.account?.kdfSalt)
        assertEquals(5, store.record?.baseRevision)
        assertFalse(store.record!!.hasPending)
        assertEquals("a,b", String(KeyTagCipher.decrypt(request.payload, keyOf("new-password!", newSalt))))
    }

    @Test
    fun `현재 비밀번호가 틀리면 서버에 보내지 않는다`() {
        assertFailsWith<AccountError.WrongPassword> { useCase.changePassword(opened(), vault("a"), "wrong", "new-password!", "new-password!") }
        assertTrue(api.changeRequests.isEmpty())
    }

    @Test
    fun `새 비밀번호 규칙과 현재와 같음을 먼저 검사한다`() {
        val short = assertFailsWith<AccountError.WeakNewPassword> { useCase.changePassword(opened(), vault("a"), "current-password", "short", "short") }
        assertEquals(NewPasswordProblem.TOO_SHORT, short.problem)
        assertFailsWith<AccountError.SamePassword> {
            useCase.changePassword(opened(), vault("a"), "current-password", "current-password", "current-password")
        }
    }

    @Test
    fun `세션이 없거나 읽기 전용 볼트면 바꾸지 않는다`() {
        assertFailsWith<AccountError.Offline> { useCase.changePassword(opened(null), vault("a"), "current-password", "new-password!", "new-password!") }
        assertFailsWith<SyncError.ReadOnlyVault> {
            useCase.changePassword(opened(), vault("a", version = 2), "current-password", "new-password!", "new-password!")
        }
    }

    @Test
    fun `서버 오류는 충돌·로그인 필요·오프라인으로 바꾸고 기기 기록은 그대로 둔다`() {
        val cases = listOf<Pair<Exception, (Exception) -> Boolean>>(
            ApiError.Server(409, ServerErrorCode.VAULT_CONFLICT, null) to { it is AccountError.Conflict },
            ApiError.Server(401, ServerErrorCode.UNAUTHORIZED, null) to { it is AccountError.LoginRequired },
            ApiError.Server(401, ServerErrorCode.AUTH_FAILED, null) to { it is AccountError.WrongPassword },
            ApiError.Network(IOException("x")) to { it is AccountError.Offline },
        )
        for ((failure, expected) in cases) {
            api.failure = failure
            val error = assertFailsWith<Exception> {
                useCase.changePassword(opened(), vault("a"), "current-password", "new-password!", "new-password!")
            }
            assertTrue(expected(error), error.toString())
        }
        assertNull(store.record)
    }

    @Test
    fun `계정을 지우면 인증 해시를 보내고 기기 기록을 지운다`() {
        coordinator.keepServerCopy(vault("a"), keyOf("current-password"))
        useCase.deleteAccount(opened(), "current-password")
        assertEquals(listOf("tok"), api.deleteTokens)
        assertNull(store.record)
    }

    @Test
    fun `계정 삭제도 틀린 비밀번호·오프라인을 막는다`() {
        assertFailsWith<AccountError.WrongPassword> { useCase.deleteAccount(opened(), "wrong") }
        assertFailsWith<AccountError.Offline> { useCase.deleteAccount(opened(null), "current-password") }
        assertTrue(api.deleteTokens.isEmpty())
    }

    /** salt 첫 바이트를 섞어 salt마다 다른 키를 만든다. */
    private object SaltedDeriver : MasterKeyDeriver {
        override fun deriveMasterKey(password: ByteArray, parameters: KdfParameters) =
            (password + parameters.salt[0]).copyOf(32)
    }

    private object Split : KeySeparator {
        override fun separate(masterKey: ByteArray) = SeparatedKeys(byteArrayOf(1) + masterKey, byteArrayOf(2) + masterKey)
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

    private object NoVaultApi : VaultApi {
        override fun fetchVault(accessToken: String): RemoteVault? = null
        override fun saveVault(accessToken: String, expectedRevision: Int, payload: EncryptedVaultPayload) = expectedRevision + 1
    }

    private class FakeAuthApi : AuthApi {
        var failure: Exception? = null
        val changeRequests = mutableListOf<ChangePasswordRequest>()
        val deleteTokens = mutableListOf<String>()

        override fun changePassword(request: ChangePasswordRequest): ChangePasswordResult {
            failure?.let { throw it }
            changeRequests += request
            return ChangePasswordResult(Session("new-tok", Instant.parse("2026-12-18T00:00:00Z")), request.expectedRevision + 1)
        }

        override fun deleteAccount(accessToken: String, authHash: ByteArray) {
            failure?.let { throw it }
            deleteTokens += accessToken
        }

        override fun prelogin(email: String): PreloginInfo = error("쓰지 않음")
        override fun register(email: String, authHash: ByteArray, kdfSalt: ByteArray, kdfIterations: Int): Session = error("쓰지 않음")
        override fun login(email: String, authHash: ByteArray): Session = error("쓰지 않음")
        override fun logout(accessToken: String) = Unit
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
