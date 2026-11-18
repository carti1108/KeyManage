package com.keymanage.android.domain.sync

import com.keymanage.android.domain.crypto.VaultCipher
import com.keymanage.android.domain.remote.ApiError
import com.keymanage.android.domain.remote.VaultApi
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultSerializer

/** 저장 결과. 서버가 처리하지 못한 저장도 대기로 두고 성공으로 본다(VLT-002 규칙 2). */
sealed interface SaveOutcome {
    data class Synced(val revision: Int) : SaveOutcome
    data object Pending : SaveOutcome
}

/** 대기 내용 재전송 시점(VLT-002 규칙 6·7). 자동 시점은 분명한 거절 뒤 멈춘다. */
enum class RetryTrigger(val isAutomatic: Boolean) {
    RECONNECT(true),
    INTERVAL(true),
    UNLOCK(false),
    USER_SAVE(false),
    RELOGIN(false),
}

/**
 * 볼트 저장·동기화 대기·기기 기록을 묶는다(VLT-002, VLT-003). 시점(30초 간격·재연결·잠금 해제)은 호출자가 정한다.
 * 상태를 가지므로 한 스레드에서만 부른다.
 */
class VaultSyncCoordinator(
    api: VaultApi,
    private val cipher: VaultCipher,
    private val serializer: VaultSerializer,
    private val store: DeviceRecordStore,
) {
    private val saveVault = SaveVaultUseCase(api, cipher, serializer)
    private var account: DeviceAccount? = null
    private var baseRevision = 0
    private var lastSeenRevision = 0
    private var pendingVault: VaultData? = null
    private var autoRetryBlocked = false

    /** 마지막으로 저장(동기화 또는 대기)한 볼트. 409 병합 결과를 화면에 반영할 때 쓴다. */
    var latestVault: VaultData? = null
        private set

    /** 대기 내용이 있는지. */
    val hasPending: Boolean
        get() = pendingVault != null || (restore()?.let { it.hasPending && it.account.email == account?.email } ?: false)

    /** 기기 저장에 실패한 뒤 다음 저장이 성공할 때까지 true(VLT-003 규칙 7). */
    var storageWarning = false
        private set

    /** 앱을 열 때 기기 기록을 읽는다. 읽지 못하면 null이고, 로그인 뒤 [onAuthenticated]가 다시 읽는다(VLT-003 규칙 6). */
    fun restore(): DeviceRecord? = try {
        store.read()
    } catch (error: DeviceStorageError) {
        null
    }

    /**
     * 인증(로그인·가입)이 성공한 직후 부른다. 다른 계정이면 이전 기록을 지우고(VLT-003 규칙 3),
     * 같은 이메일인데 KDF 값이 다르면 대기 내용만 버린다(규칙 5).
     */
    fun onAuthenticated(authenticated: DeviceAccount) {
        val record = store.read()
        account = authenticated
        autoRetryBlocked = false
        if (record == null) return
        if (record.account.email != authenticated.email) {
            store.clear()
            reset()
            return
        }
        lastSeenRevision = record.lastSeenRevision
        baseRevision = record.baseRevision
        if (record.account != authenticated) {
            pendingVault = null
            if (record.hasPending) store.write(record.copy(hasPending = false))
        }
    }

    /**
     * 볼트를 연 직후 서버 revision으로 부른다. 마지막으로 본 값보다 낮으면 되돌림이다(VLT-002 규칙 8).
     * 대기 내용이 있으면 남겨 두고 [retryPending]으로 병합해 올린다(규칙 9).
     */
    fun open(opened: DeviceAccount, serverRevision: Int) {
        if (serverRevision < lastSeenRevision) throw SyncError.RevisionRollback(serverRevision, lastSeenRevision)
        account = opened
        lastSeenRevision = serverRevision
        if (pendingVault == null) baseRevision = serverRevision
    }

    /** 서버에서 받은 볼트를 대기 내용이 없을 때만 기기 사본으로 둔다(VLT-003 규칙 1·5). */
    fun keepServerCopy(vault: VaultData, key: ByteArray) {
        if (hasPending) return
        latestVault = vault
        persist(vault, key, hasPending = false)
    }

    /** 사용자 저장. 분명한 거절 뒤 멈췄던 자동 재전송도 다시 연다(VLT-002 규칙 7). */
    fun save(vault: VaultData, key: ByteArray, accessToken: String): SaveOutcome {
        autoRetryBlocked = false
        return send(vault, key, accessToken)
    }

    /** 다음 저장의 expectedRevision. 마지막으로 서버와 맞춘 revision이다. */
    val expectedRevision: Int
        get() = baseRevision

    /**
     * 마스터 비밀번호 변경이 서버에서 확정됐다(SEC-004). 새 KDF 값과 새 키로 암호화한 볼트를 기기 기록으로 두고,
     * 볼트에 담아 올린 대기 내용은 지운다.
     */
    fun adoptPasswordChange(changed: DeviceAccount, vault: VaultData, newKey: ByteArray, revision: Int) {
        account = changed
        baseRevision = revision
        lastSeenRevision = revision
        pendingVault = null
        latestVault = vault
        autoRetryBlocked = false
        persist(vault, newKey, hasPending = false)
    }

    /** 세션 없이 연 볼트의 저장. 서버에 보내지 않고 대기로 둔다(SEC-003 규칙 10, VLT-002 규칙 2). */
    fun saveLocally(vault: VaultData, key: ByteArray): SaveOutcome {
        keepPending(vault, key)
        return SaveOutcome.Pending
    }

    /** 대기 내용을 다시 보낸다. 대기가 없거나 자동 재전송이 멈춘 상태면 null. */
    fun retryPending(key: ByteArray, accessToken: String, trigger: RetryTrigger): SaveOutcome? {
        if (trigger.isAutomatic && autoRetryBlocked) return null
        val vault = pendingVault ?: pendingFromStore(key) ?: return null
        autoRetryBlocked = false
        return send(vault, key, accessToken)
    }

    /** 로그아웃. 기기 기록을 지우고, 지우지 못하면 [DeviceStorageError]를 던진다(VLT-003 규칙 4·8). */
    fun logout() {
        reset()
        account = null
        store.clear()
    }

    private fun send(vault: VaultData, key: ByteArray, accessToken: String): SaveOutcome {
        val result = try {
            saveVault.save(vault, baseRevision, lastSeenRevision, key, accessToken)
        } catch (error: Exception) {
            if (isTransient(error)) {
                keepPending(vault, key)
                return SaveOutcome.Pending
            }
            keepPending(vault, key)
            if (isClearRejection(error)) autoRetryBlocked = true
            throw error
        }
        baseRevision = result.revision
        lastSeenRevision = result.revision
        pendingVault = null
        latestVault = result.vault
        persist(result.vault, key, hasPending = false)
        return SaveOutcome.Synced(result.revision)
    }

    private fun keepPending(vault: VaultData, key: ByteArray) {
        pendingVault = vault
        latestVault = vault
        persist(vault, key, hasPending = true)
    }

    private fun persist(vault: VaultData, key: ByteArray, hasPending: Boolean) {
        val owner = account ?: return
        val plaintext = serializer.encode(vault)
        storageWarning = try {
            store.write(DeviceRecord(owner, cipher.encrypt(plaintext, key), baseRevision, lastSeenRevision, hasPending))
            false
        } catch (error: DeviceStorageError) {
            true
        } finally {
            plaintext.fill(0)
        }
    }

    private fun pendingFromStore(key: ByteArray): VaultData? {
        val record = restore()?.takeIf { it.hasPending && it.account.email == account?.email } ?: return null
        return serializer.decode(cipher.decrypt(record.payload, key))
    }

    private fun reset() {
        baseRevision = 0
        lastSeenRevision = 0
        pendingVault = null
        latestVault = null
        autoRetryBlocked = false
    }

    private companion object {
        /** 다시 보내면 성공할 수 있는 실패(VLT-002 규칙 2). */
        fun isTransient(error: Exception) = when (error) {
            is ApiError.Network, is ApiError.InvalidResponse -> true
            is ApiError.Server -> error.status >= 500 || error.status == 429
            else -> false
        }

        /** 다시 보내도 성공할 수 없는 실패(VLT-002 규칙 3·7). 충돌 재시도 소진과 401은 아니다. */
        fun isClearRejection(error: Exception) = when (error) {
            is SyncError.ConflictRetriesExhausted -> false
            is SyncError -> true
            is ApiError.Server -> error.status != 401
            else -> false
        }
    }
}
