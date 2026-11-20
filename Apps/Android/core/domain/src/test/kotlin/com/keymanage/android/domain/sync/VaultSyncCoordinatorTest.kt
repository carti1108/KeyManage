package com.keymanage.android.domain.sync

import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import com.keymanage.android.domain.crypto.VaultCipher
import com.keymanage.android.domain.remote.ApiError
import com.keymanage.android.domain.remote.RemoteVault
import com.keymanage.android.domain.remote.ServerErrorCode
import com.keymanage.android.domain.remote.VaultApi
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultItem
import com.keymanage.android.domain.vault.VaultSerializer
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultSyncCoordinatorTest {
    private val t0 = Instant.parse("2026-11-18T00:00:00Z")
    private val key = ByteArray(32)
    private val account = DeviceAccount("a@b.c", 600_000, "AAAAAAAAAAAAAAAAAAAAAA==")

    private fun vault(vararg ids: String) = VaultData(
        1, t0, ids.map { VaultItem(id = it, serviceName = it, username = "u", password = "p", createdAt = t0, updatedAt = t0) },
    )

    private val api = FakeApi()
    private val store = FakeStore()
    private val coordinator = VaultSyncCoordinator(api, PlainCipher, IdsSerializer, store)

    private fun start(revision: Int = 3) = coordinator.open(account, revision)

    @Test
    fun `저장에 성공하면 기기 기록의 기준·마지막으로 본 revision을 올리고 대기는 없다`() {
        // Given
        start()

        // When
        val outcome = coordinator.save(vault("a"), key, "t")

        // Then
        assertEquals(SaveOutcome.Synced(4), outcome)
        val record = store.record!!
        assertEquals(4, record.baseRevision)
        assertEquals(4, record.lastSeenRevision)
        assertFalse(record.hasPending)
    }

    @Test
    fun `네트워크·응답 형식·5xx·429 실패는 대기로 두고 성공 처리한다`() {
        val transient = listOf(
            ApiError.Network(IOException("x")),
            ApiError.InvalidResponse("x"),
            ApiError.Server(503, ServerErrorCode.INTERNAL_ERROR, null),
            ApiError.Server(429, ServerErrorCode.TOO_MANY_ATTEMPTS, null),
        )
        for (error in transient) {
            // Given
            start()
            api.failWith = error

            // When
            val outcome = coordinator.save(vault("a"), key, "t")

            // Then: 기준 revision은 마지막으로 서버와 맞춘 값 그대로
            assertEquals(SaveOutcome.Pending, outcome)
            assertTrue(store.record!!.hasPending)
            assertEquals(3, store.record!!.baseRevision)
            assertEquals("a", PlainCipher.decryptIds(store.record!!.payload))
        }
    }

    @Test
    fun `대기 중 다시 고치면 대기 내용 위에 쌓고 기준 revision은 유지한다`() {
        // Given
        start()
        api.failWith = ApiError.Network(IOException("x"))
        coordinator.save(vault("a"), key, "t")

        // When
        coordinator.save(vault("a", "b"), key, "t")

        // Then
        assertEquals("a,b", PlainCipher.decryptIds(store.record!!.payload))
        assertEquals(3, store.record!!.baseRevision)
    }

    @Test
    fun `재전송이 성공하면 대기를 지운다`() {
        // Given
        start()
        api.failWith = ApiError.Network(IOException("x"))
        coordinator.save(vault("a"), key, "t")
        api.failWith = null

        // When
        val outcome = coordinator.retryPending(key, "t", RetryTrigger.INTERVAL)

        // Then
        assertEquals(SaveOutcome.Synced(4), outcome)
        assertFalse(store.record!!.hasPending)
    }

    @Test
    fun `대기가 없으면 재전송은 아무것도 보내지 않는다`() {
        start()
        assertNull(coordinator.retryPending(key, "t", RetryTrigger.INTERVAL))
        assertEquals(0, api.saveCalls)
    }

    @Test
    fun `분명한 거절은 오류로 올리고 그 뒤 자동 재전송은 멈추며 사용자 저장은 다시 보낸다`() {
        // Given: 대기 중 재전송이 되돌림으로 거절된다
        start()
        api.failWith = ApiError.Network(IOException("x"))
        coordinator.save(vault("a"), key, "t")
        api.failWith = ApiError.Server(409, ServerErrorCode.VAULT_CONFLICT, null)
        api.serverRevision = 1

        // When
        assertFailsWith<SyncError.RevisionRollback> { coordinator.retryPending(key, "t", RetryTrigger.RECONNECT) }
        api.failWith = null
        api.serverRevision = 3
        val auto = coordinator.retryPending(key, "t", RetryTrigger.INTERVAL)
        val manual = coordinator.retryPending(key, "t", RetryTrigger.USER_SAVE)

        // Then
        assertNull(auto)
        assertEquals(SaveOutcome.Synced(4), manual)
    }

    @Test
    fun `자동 병합 3회 충돌은 대기 내용을 남긴 채 오류를 올린다`() {
        // Given
        start()
        api.alwaysConflict = true

        // When
        assertFailsWith<SyncError.ConflictRetriesExhausted> { coordinator.save(vault("a"), key, "t") }

        // Then
        assertTrue(store.record!!.hasPending)
    }

    @Test
    fun `401은 대기로 숨기지 않고 대기 내용은 남긴다`() {
        start()
        api.failWith = ApiError.Server(401, ServerErrorCode.UNAUTHORIZED, null)
        assertFailsWith<ApiError.Server> { coordinator.save(vault("a"), key, "t") }
        assertTrue(store.record!!.hasPending)
    }

    @Test
    fun `저장 응답 revision이 기대값+1이 아니면 서버 규약 위반으로 올린다`() {
        start()
        api.revisionSkew = 5
        assertFailsWith<SyncError.UnexpectedRevision> { coordinator.save(vault("a"), key, "t") }
    }

    @Test
    fun `기기 저장에 실패하면 다음 저장이 성공할 때까지 경고한다`() {
        // Given
        start()
        store.failWrites = true

        // When
        coordinator.save(vault("a"), key, "t")
        val warnedAfterFailure = coordinator.storageWarning
        store.failWrites = false
        coordinator.save(vault("a", "b"), key, "t")

        // Then
        assertTrue(warnedAfterFailure)
        assertFalse(coordinator.storageWarning)
    }

    @Test
    fun `다른 계정 인증이 성공하면 이전 기록을 지우고 같은 계정이면 남긴다`() {
        // Given
        start()
        api.failWith = ApiError.Network(IOException("x"))
        coordinator.save(vault("a"), key, "t")

        // When
        coordinator.onAuthenticated(account)
        val kept = store.record
        coordinator.onAuthenticated(account.copy(email = "other@b.c"))

        // Then
        assertTrue(kept!!.hasPending)
        assertNull(store.record)
    }

    @Test
    fun `같은 이메일인데 KDF 값이 바뀌면 대기만 버리고 되돌림 기준은 유지한다`() {
        // Given
        start(revision = 7)
        api.failWith = ApiError.Network(IOException("x"))
        coordinator.save(vault("a"), key, "t")

        // When
        coordinator.onAuthenticated(account.copy(kdfIterations = 700_000))

        // Then
        val record = store.record!!
        assertFalse(record.hasPending)
        assertEquals(7, record.lastSeenRevision)
    }

    @Test
    fun `재시작 뒤 기기 기록에서 대기 내용과 되돌림 기준을 이어 간다`() {
        // Given
        start(revision = 7)
        api.failWith = ApiError.Network(IOException("x"))
        coordinator.save(vault("a"), key, "t")

        // When
        val restarted = VaultSyncCoordinator(api, PlainCipher, IdsSerializer, store)
        val restored = restarted.restore()

        // Then
        assertEquals(7, restored?.lastSeenRevision)
        assertTrue(restored!!.hasPending)
    }

    @Test
    fun `로그아웃은 기기 기록을 지우고 실패하면 알린다`() {
        start()
        coordinator.logout()
        assertNull(store.record)

        start()
        store.failClear = true
        assertFailsWith<DeviceStorageError> { coordinator.logout() }
    }

    @Test
    fun `되돌림은 받아들인 revision까지만 열고 기기 기록에도 남긴다`() {
        // Given: 5까지 본 기기가 재시작한 뒤 서버가 2를 준다
        start(revision = 5)
        coordinator.keepServerCopy(vault("a"), key)
        val restarted = VaultSyncCoordinator(api, PlainCipher, IdsSerializer, store)
        restarted.onAuthenticated(account)
        assertFailsWith<SyncError.RevisionRollback> { restarted.open(account, 2) }

        // When
        restarted.acceptRollback(2)
        restarted.onAuthenticated(account)
        restarted.open(account, 2)

        // Then
        assertEquals(2, store.record!!.lastSeenRevision)
        assertFailsWith<SyncError.RevisionRollback> { restarted.open(account, 1) }
    }

    @Test
    fun `받아들이기는 마지막으로 본 revision을 올리지 않는다`() {
        start(revision = 5)
        coordinator.acceptRollback(9)
        assertFailsWith<SyncError.RevisionRollback> { coordinator.open(account, 4) }
    }

    private object PlainCipher : VaultCipher {
        override fun encrypt(plaintext: ByteArray, key: ByteArray) =
            EncryptedVaultPayload(1, "AES-256-GCM", "", String(plaintext), "")

        override fun decrypt(payload: EncryptedVaultPayload, key: ByteArray) = payload.ciphertext.toByteArray()

        fun decryptIds(payload: EncryptedVaultPayload) = payload.ciphertext
    }

    /** 볼트를 항목 id 목록(쉼표)으로만 나타내는 테스트용 직렬화기. */
    private object IdsSerializer : VaultSerializer {
        private val t0 = Instant.parse("2026-11-18T00:00:00Z")
        override fun encode(vault: VaultData) = vault.items.joinToString(",") { it.id }.toByteArray()
        override fun decode(bytes: ByteArray) = VaultData(
            1, t0, String(bytes).split(",").filter { it.isNotEmpty() }
                .map { VaultItem(id = it, serviceName = it, username = "u", password = "p", createdAt = t0, updatedAt = t0) },
        )
    }

    private class FakeApi : VaultApi {
        var serverRevision = 3
        var failWith: Exception? = null
        var alwaysConflict = false
        var revisionSkew = 0
        var saveCalls = 0

        override fun fetchVault(accessToken: String): RemoteVault? =
            RemoteVault(serverRevision, EncryptedVaultPayload(1, "AES-256-GCM", "", "", ""))

        override fun saveVault(accessToken: String, expectedRevision: Int, payload: EncryptedVaultPayload): Int {
            saveCalls++
            failWith?.let { throw it }
            if (alwaysConflict) {
                serverRevision++
                throw ApiError.Server(409, ServerErrorCode.VAULT_CONFLICT, null)
            }
            if (expectedRevision != serverRevision) throw ApiError.Server(409, ServerErrorCode.VAULT_CONFLICT, null)
            serverRevision++
            return serverRevision + revisionSkew
        }
    }

    private class FakeStore : DeviceRecordStore {
        var record: DeviceRecord? = null
        var failWrites = false
        var failClear = false

        override fun read() = record

        override fun write(record: DeviceRecord) {
            if (failWrites) throw DeviceStorageError("쓰기 실패")
            this.record = record
        }

        override fun clear() {
            if (failClear) throw DeviceStorageError("삭제 실패")
            record = null
        }
    }
}
