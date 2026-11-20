package com.keymanage.feature.vaultlist.testing

import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.backup.VaultBackup
import com.keymanage.domain.session.LockScope
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.SyncChangeListener
import com.keymanage.domain.vault.SyncedVault
import com.keymanage.domain.vault.VaultData
import com.keymanage.feature.SessionPort
import com.keymanage.feature.vaultlist.VaultListListener
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Instant

// VaultList 피처의 테스트 더블. 테스트(features/vaultlist/tests)와 각 플랫폼의 Example 앱이 함께 쓴다

/** 부모에 알린 횟수를 센다. [onEvent]를 넣으면 알릴 때마다 한 줄 설명을 넘긴다(Example 앱의 결과 표시) */
class RecordingVaultListListener(private val onEvent: (String) -> Unit = {}) : VaultListListener {
    var saves = 0

    override fun vaultListDidSave() {
        saves += 1
        onEvent("vaultListDidSave()")
    }
}

/** 저장이 끝나는 시점과 결과를 테스트가 정하는 세션. 쓰지 않는 기능은 부르면 실패한다 */
class FakeVaultSession(initial: VaultData, private var readOnly: Boolean = false) : SessionPort {
    var data: VaultData = initial
        private set
    var current: SessionState = unlocked()
    val saved = mutableListOf<VaultData>()
    var saveCalls = 0
        private set
    var failNext: Throwable? = null

    /** 세션이 볼트를 반영한 뒤에 거절한다. 실제 세션은 서버 충돌 같은 거절을 기기 저장 뒤에 던질 수 있다 */
    var applyThenFail: Throwable? = null

    /** 저장을 보내는 사이 세션이 다른 볼트로 바뀐다(원격 병합). 있으면 보낸 볼트 대신 이것을 반영한다 */
    var replaceOnSave: VaultData? = null
    var gate: CompletableDeferred<Unit>? = null
    val remoteListeners = mutableSetOf<() -> Unit>()
    val syncListeners = mutableSetOf<SyncChangeListener>()

    private fun unlocked() = SessionState.Unlocked(
        email = "a@b.c",
        vault = SyncedVault(revision = 1, data = data, readOnly = readOnly, payload = null),
        pendingSync = false,
        signedIn = true,
        storedOnDevice = true,
    )

    /** 다른 기기의 변경이 병합된 것처럼 세션의 볼트를 바꾼다 */
    fun replace(next: VaultData, readOnly: Boolean = this.readOnly) {
        data = next
        this.readOnly = readOnly
        current = unlocked()
    }

    fun fireRemoteChange() = remoteListeners.toList().forEach { it() }
    fun fireSyncChange(error: Throwable?) = syncListeners.toList().forEach { it(error) }

    override fun state(): SessionState = current

    override suspend fun save(data: VaultData) {
        saveCalls++
        gate?.await()
        failNext?.let {
            failNext = null
            throw it
        }
        applyThenFail?.let {
            applyThenFail = null
            replace(replaceOnSave ?: data)
            throw it
        }
        saved += data
        replace(data)
    }

    override fun onRemoteChange(listener: () -> Unit): () -> Unit {
        remoteListeners += listener
        return { remoteListeners -= listener }
    }

    override fun onSyncChange(listener: SyncChangeListener): () -> Unit {
        syncListeners += listener
        return { syncListeners -= listener }
    }

    override suspend fun register(email: String, password: String): Unit = unused()
    override suspend fun login(email: String, password: String, acceptRollback: Boolean): Unit = unused()
    override fun lock(scope: LockScope): Unit = unused()
    override suspend fun unlock(password: String, acceptRollback: Boolean): Boolean = unused()
    override suspend fun reconnect(password: String, acceptRollback: Boolean): Unit = unused()
    override suspend fun exportBackup(now: Instant, calendar: LocalCalendar): BackupFile = unused()
    override suspend fun restoreBackup(backup: VaultBackup, password: String, now: Instant): Int = unused()
    override suspend fun changeMasterPassword(currentPassword: String, newPassword: String): Unit = unused()
    override suspend fun deleteAccount(password: String): Unit = unused()
    override suspend fun logout(): Unit = unused()
    override suspend fun pendingOnDevice(): Boolean = unused()
    override fun onLock(listener: () -> Unit): () -> Unit = unused()
    override fun onUnlock(listener: () -> Unit): () -> Unit = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("VaultList RIB이 부르지 않는 세션 기능")
}
