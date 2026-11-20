package com.keymanage.feature.settings.testing

import com.keymanage.domain.auth.UnlockKdf
import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.BackupFileSource
import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.backup.LocalDay
import com.keymanage.domain.backup.VaultBackup
import com.keymanage.domain.backup.createBackupFile
import com.keymanage.domain.crypto.EncryptedVaultPayload
import com.keymanage.domain.encoding.toBase64
import com.keymanage.domain.lock.AutoLockSettingStore
import com.keymanage.domain.lock.DEFAULT_AUTO_LOCK
import com.keymanage.domain.session.LockScope
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.SyncChangeListener
import com.keymanage.domain.vault.SyncedVault
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.emptyVault
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.settings.SettingsListener
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

// Settings 피처의 테스트 더블. 테스트(features/settings/tests)와 각 플랫폼의 Example 앱이 함께 쓴다

/** 내보내기가 돌려주는 백업 파일 */
val EXPORTED_BACKUP = BackupFile("keymanage-backup-20260102.json", "{}")

/** 설정 화면이 보는 열린 세션 상태 */
fun unlockedSettingsState(
    email: String = "user@example.com",
    pendingSync: Boolean = false,
    signedIn: Boolean = true,
    readOnly: Boolean = false,
): SessionState = SessionState.Unlocked(
    email = email,
    vault = SyncedVault(revision = 1, data = emptyVault(Instant.fromEpochSeconds(0)), readOnly = readOnly, payload = null),
    pendingSync = pendingSync,
    signedIn = signedIn,
    storedOnDevice = true,
)

/** 형식이 맞는 백업 파일. 내용은 열 수 없는 값이라 복원 결과는 가짜 세션이 정한다 */
fun sampleBackupSource(): BackupFileSource {
    val kdf = UnlockKdf(toBase64(ByteArray(16)), 600_000)
    val vault = EncryptedVaultPayload(1, "AES-256-GCM", toBase64(ByteArray(12)), toBase64(ByteArray(8)), toBase64(ByteArray(16)))
    val file = createBackupFile(vault, kdf, Instant.fromEpochSeconds(0)) { LocalDay(2026, 1, 2) }
    return BackupFileSource(file.text.length) { file.text }
}

/** 내보내기·복원·비밀번호 변경·계정 삭제의 결과와 끝나는 시점을 테스트가 정하는 세션. 나머지는 이 RIB이 부르지 않으므로 호출되면 실패한다 */
class FakeSettingsSession(var current: SessionState = unlockedSettingsState()) : SessionPort {
    val exportCalls = mutableListOf<Pair<Instant, LocalCalendar>>()
    var exportError: Throwable? = null
    var exportGate: CompletableDeferred<Unit>? = null
    val restorePasswords = mutableListOf<String>()
    val restoreTimes = mutableListOf<Instant>()
    var restoreCount = 1
    var restoreError: Throwable? = null
    var restoreGate: CompletableDeferred<Unit>? = null
    val changeCalls = mutableListOf<Pair<String, String>>()
    var changeError: Throwable? = null
    var changeGate: CompletableDeferred<Unit>? = null
    val deleteCalls = mutableListOf<String>()
    var deleteError: Throwable? = null
    var deleteGate: CompletableDeferred<Unit>? = null
    val syncListeners = mutableListOf<SyncChangeListener>()
    val remoteListeners = mutableListOf<() -> Unit>()

    fun fireSyncChange() = syncListeners.toList().forEach { it(null) }
    fun fireRemoteChange() = remoteListeners.toList().forEach { it() }

    override fun state() = current

    override suspend fun exportBackup(now: Instant, calendar: LocalCalendar): BackupFile {
        exportCalls += now to calendar
        exportGate?.await()
        exportError?.let { throw it }
        return EXPORTED_BACKUP
    }

    override suspend fun restoreBackup(backup: VaultBackup, password: String, now: Instant): Int {
        restorePasswords += password
        restoreTimes += now
        restoreGate?.await()
        restoreError?.let { throw it }
        return restoreCount
    }

    override suspend fun changeMasterPassword(currentPassword: String, newPassword: String) {
        changeCalls += currentPassword to newPassword
        changeGate?.await()
        changeError?.let { throw it }
    }

    override suspend fun deleteAccount(password: String) {
        deleteCalls += password
        deleteGate?.await()
        deleteError?.let { throw it }
    }

    override fun onSyncChange(listener: SyncChangeListener): () -> Unit {
        syncListeners += listener
        return { syncListeners.remove(listener) }
    }

    override fun onRemoteChange(listener: () -> Unit): () -> Unit {
        remoteListeners += listener
        return { remoteListeners.remove(listener) }
    }

    override suspend fun register(email: String, password: String) = unused()
    override suspend fun login(email: String, password: String, acceptRollback: Boolean) = unused()
    override fun lock(scope: LockScope) = unused()
    override suspend fun unlock(password: String, acceptRollback: Boolean): Boolean = unused()
    override suspend fun reconnect(password: String, acceptRollback: Boolean) = unused()
    override suspend fun save(data: VaultData) = unused()
    override suspend fun logout() = unused()
    override suspend fun pendingOnDevice(): Boolean = unused()
    override fun onLock(listener: () -> Unit): () -> Unit = unused()
    override fun onUnlock(listener: () -> Unit): () -> Unit = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("설정 화면이 쓰지 않는 세션 기능")
}

/** 저장한 파일을 기록하고, 고를 파일과 실패를 테스트가 정하는 백업 파일 포트 */
class FakeSettingsBackupFiles : BackupFilesPort {
    val saved = mutableListOf<BackupFile>()
    var picked: BackupFileSource? = null
    var pickError: Throwable? = null
    var pickCalls = 0
    var pickGates: ArrayDeque<CompletableDeferred<BackupFileSource?>>? = null

    override suspend fun save(file: BackupFile) {
        saved += file
    }

    override suspend fun pick(): BackupFileSource? {
        pickCalls += 1
        pickError?.let { throw it }
        return pickGates?.removeFirst()?.await() ?: picked
    }
}

/** 쓴 값을 기록하고, 다른 탭이 바꾼 것처럼 [emit]으로 알릴 수 있는 자동 잠금 설정 저장소 */
class FakeSettingsAutoLockStore : AutoLockSettingStore {
    var value: Duration = DEFAULT_AUTO_LOCK
    val written = mutableListOf<Duration>()
    val listeners = mutableListOf<(Duration) -> Unit>()

    override fun read() = value

    override fun write(timeout: Duration) {
        written += timeout
        value = timeout
    }

    override fun subscribe(listener: (Duration) -> Unit): () -> Unit {
        listeners += listener
        return { listeners.remove(listener) }
    }

    fun emit(timeout: Duration) {
        value = timeout
        listeners.toList().forEach { it(timeout) }
    }
}

class FakeSettingsEnvironment : AppEnvironment {
    override val clock: Clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochSeconds(1_700_000_000)
    }
    override val calendar = LocalCalendar { LocalDay(2026, 1, 2) }
    val autoLock = FakeSettingsAutoLockStore()
    override val autoLockSetting: AutoLockSettingStore get() = autoLock
    override var serverAddress: String = ""
    var offline = false
    val networkListeners = mutableListOf<() -> Unit>()

    override fun isNetworkOffline() = offline

    override fun subscribeNetwork(listener: () -> Unit): () -> Unit {
        networkListeners += listener
        return { networkListeners.remove(listener) }
    }

    fun changeOffline(value: Boolean) {
        offline = value
        networkListeners.toList().forEach { it() }
    }
}

/** 부모에 알린 내용을 기록한다. [onEvent]를 넣으면 알릴 때마다 한 줄 설명을 넘긴다(Example 앱의 결과 표시) */
class FakeSettingsListener(private val onEvent: (String) -> Unit = {}) : SettingsListener {
    val events = mutableListOf<String>()

    private fun record(event: String, description: String) {
        events += event
        onEvent(description)
    }

    override fun settingsDidRequestLock() = record("lock", "settingsDidRequestLock()")

    override fun settingsDidRequestLogout() = record("logout", "settingsDidRequestLogout()")

    override fun settingsDidRestoreBackup() = record("restored", "settingsDidRestoreBackup()")

    override fun settingsDidChangeMasterPassword() = record("passwordChanged", "settingsDidChangeMasterPassword()")

    override fun settingsDidChangeSession() = record("changed", "settingsDidChangeSession()")

    override fun settingsDidEndSession(notice: String) = record("end:$notice", "settingsDidEndSession(notice = $notice)")
}
