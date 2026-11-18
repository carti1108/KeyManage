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
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SaveVaultUseCaseTest {
    private val t0 = Instant.parse("2026-11-18T00:00:00Z")
    private val key = ByteArray(32)

    private fun item(id: String, seconds: Long) = VaultItem(
        id = id, serviceName = id, username = "u", password = "p",
        createdAt = t0, updatedAt = t0.plusSeconds(seconds),
    )

    private fun vault(vararg items: VaultItem, version: Int = 1) = VaultData(version, t0, items.toList())

    @Test
    fun `충돌이 없으면 한 번 저장하고 새 revision을 돌려준다`() {
        // Given
        val api = FakeVaultApi(serverRevision = 3)
        val useCase = useCase(api)

        // When
        val result = useCase.save(vault(item("a", 1)), baseRevision = 3, lastSeenRevision = 3, key = key, accessToken = "t")

        // Then
        assertEquals(4, result.revision)
        assertEquals(1, api.saveCalls)
    }

    @Test
    fun `409를 받으면 서버 볼트를 받아 병합한 뒤 서버 revision으로 다시 저장한다`() {
        // Given: 서버에는 다른 기기가 추가한 b가 있다
        val api = FakeVaultApi(serverRevision = 5, serverVault = vault(item("b", 2)))
        val useCase = useCase(api)

        // When
        val result = useCase.save(vault(item("a", 1)), baseRevision = 3, lastSeenRevision = 3, key = key, accessToken = "t")

        // Then
        assertEquals(setOf("a", "b"), result.vault.items.map { it.id }.toSet())
        assertEquals(6, result.revision)
        assertEquals(listOf(3, 5), api.expectedRevisions)
    }

    @Test
    fun `자동 병합 재시도는 3회까지, 요청은 최대 4번이다`() {
        // Given: 서버가 매번 먼저 바뀐다
        val api = FakeVaultApi(serverRevision = 5, serverVault = vault(item("b", 2)), alwaysConflict = true)
        val useCase = useCase(api)

        // When
        val error = assertFailsWith<SyncError.ConflictRetriesExhausted> {
            useCase.save(vault(item("a", 1)), baseRevision = 3, lastSeenRevision = 3, key = key, accessToken = "t")
        }

        // Then
        assertEquals(4, api.saveCalls)
        assertEquals(SaveVaultUseCase.MAX_MERGE_RETRIES, error.retries)
    }

    @Test
    fun `서버 revision이 마지막으로 본 값보다 낮으면 멈추고 경고한다`() {
        val api = FakeVaultApi(serverRevision = 2, serverVault = vault(item("b", 2)))
        assertFailsWith<SyncError.RevisionRollback> {
            useCase(api).save(vault(item("a", 1)), baseRevision = 3, lastSeenRevision = 3, key = key, accessToken = "t")
        }
    }

    @Test
    fun `읽기 전용 볼트는 저장하지 않는다`() {
        val api = FakeVaultApi(serverRevision = 3)
        assertFailsWith<SyncError.ReadOnlyVault> {
            useCase(api).save(vault(item("a", 1), version = 2), 3, 3, key, "t")
        }
        assertEquals(0, api.saveCalls)
    }

    @Test
    fun `서버 볼트가 더 높은 버전이면 병합하지 않고 읽기 전용으로 멈춘다`() {
        val api = FakeVaultApi(serverRevision = 5, serverVault = vault(item("b", 2), version = 2))
        assertFailsWith<SyncError.ReadOnlyVault> {
            useCase(api).save(vault(item("a", 1)), 3, 3, key, "t")
        }
    }

    @Test
    fun `페이로드 JSON이 5,242,880바이트를 넘으면 보내지 않는다`() {
        // Given: 암호문 길이를 조절해 경계 바로 위를 만든다
        val api = FakeVaultApi(serverRevision = 0)
        val overhead = EncryptedVaultPayload(1, "AES-256-GCM", "", "", "").canonicalJson().length
        val tooBig = useCase(api, ciphertextLength = SaveVaultUseCase.MAX_PAYLOAD_BYTES - overhead + 1)
        val justFits = useCase(api, ciphertextLength = SaveVaultUseCase.MAX_PAYLOAD_BYTES - overhead)

        // When / Then
        assertFailsWith<SyncError.VaultTooLarge> { tooBig.save(vault(item("a", 1)), 0, 0, key, "t") }
        assertEquals(0, api.saveCalls)
        justFits.save(vault(item("a", 1)), 0, 0, key, "t")
        assertEquals(1, api.saveCalls)
    }

    @Test
    fun `409가 아닌 서버 오류는 그대로 올린다`() {
        val api = FakeVaultApi(serverRevision = 3, failWith = ServerErrorCode.UNAUTHORIZED)
        val error = assertFailsWith<ApiError.Server> { useCase(api).save(vault(item("a", 1)), 3, 3, key, "t") }
        assertEquals(ServerErrorCode.UNAUTHORIZED, error.code)
        assertTrue(api.saveCalls == 1)
    }

    private fun useCase(api: FakeVaultApi, ciphertextLength: Int = 8) =
        SaveVaultUseCase(api, FakeCipher(api, ciphertextLength), FakeSerializer)

    /** 직렬화는 테스트 대상이 아니므로 볼트 객체를 그대로 들고 다닌다. */
    private object FakeSerializer : VaultSerializer {
        val store = HashMap<String, VaultData>()
        override fun decode(bytes: ByteArray) = store.getValue(bytes.toString(Charsets.UTF_8))
        override fun encode(vault: VaultData): ByteArray {
            val handle = "v${store.size}"
            store[handle] = vault
            return handle.toByteArray(Charsets.UTF_8)
        }
    }

    /** 암호문 필드에 직렬화 핸들을 넣고, 길이는 패딩으로 맞춘다. */
    private class FakeCipher(private val api: FakeVaultApi, private val ciphertextLength: Int) : VaultCipher {
        override fun encrypt(plaintext: ByteArray, key: ByteArray): EncryptedVaultPayload {
            val handle = plaintext.toString(Charsets.UTF_8)
            return EncryptedVaultPayload(1, "AES-256-GCM", "", handle.padEnd(ciphertextLength, '='), "")
        }

        override fun decrypt(payload: EncryptedVaultPayload, key: ByteArray): ByteArray =
            payload.ciphertext.trimEnd('=').toByteArray(Charsets.UTF_8)
    }

    private class FakeVaultApi(
        var serverRevision: Int,
        serverVault: VaultData? = null,
        private val alwaysConflict: Boolean = false,
        private val failWith: ServerErrorCode? = null,
    ) : VaultApi {
        var saveCalls = 0
        val expectedRevisions = mutableListOf<Int>()
        private val serverPayload = serverVault?.let {
            EncryptedVaultPayload(1, "AES-256-GCM", "", String(FakeSerializer.encode(it)), "")
        }

        override fun fetchVault(accessToken: String) = serverPayload?.let { RemoteVault(serverRevision, it) }

        override fun saveVault(accessToken: String, expectedRevision: Int, payload: EncryptedVaultPayload): Int {
            saveCalls++
            expectedRevisions += expectedRevision
            failWith?.let { throw ApiError.Server(401, it, null) }
            if (alwaysConflict || expectedRevision != serverRevision) {
                if (alwaysConflict) serverRevision++
                throw ApiError.Server(409, ServerErrorCode.VAULT_CONFLICT, null)
            }
            serverRevision++
            return serverRevision
        }
    }
}
