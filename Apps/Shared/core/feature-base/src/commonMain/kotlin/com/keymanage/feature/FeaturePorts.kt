package com.keymanage.feature

import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.BackupFileSource
import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.backup.VaultBackup
import com.keymanage.domain.lock.AutoLockSettingStore
import com.keymanage.domain.lock.BiometricError
import com.keymanage.domain.session.LockScope
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.SyncChangeListener
import com.keymanage.domain.session.VaultSession
import com.keymanage.domain.vault.VaultData
import kotlin.time.Clock
import kotlin.time.Instant

// 공유 RIB이 쓰는 바깥 기능. 테스트는 가짜를, 앱은 실제 구현을 넣는다 (domain-migration-plan 11.2)

/** 화면이 쓰는 세션 기능. 실제 구현은 [VaultSession]을 감싼 [SessionPortAdapter] */
interface SessionPort {
    fun state(): SessionState
    suspend fun register(email: String, password: String)
    suspend fun login(email: String, password: String, acceptRollback: Boolean = false)
    fun lock(scope: LockScope = LockScope.ALL_TABS)
    /** @return 오프라인으로 열었으면 true */
    suspend fun unlock(password: String, acceptRollback: Boolean = false): Boolean
    suspend fun reconnect(password: String, acceptRollback: Boolean = false)
    suspend fun save(data: VaultData)
    suspend fun exportBackup(now: Instant, calendar: LocalCalendar): BackupFile
    suspend fun restoreBackup(backup: VaultBackup, password: String, now: Instant): Int
    suspend fun changeMasterPassword(currentPassword: String, newPassword: String)
    suspend fun deleteAccount(password: String)
    /**
     * 이 기기의 세션과 기록을 지운다. 서버 토큰 폐기는 기다리지 않으며(세션의 범위에서 끝까지 한다, 실패해도 알리지 않는다) 화면 수명과 무관하다.
     * @throws DeviceStorageException 기기 기록을 지우지 못함
     */
    suspend fun logout()
    suspend fun pendingOnDevice(): Boolean
    fun onRemoteChange(listener: () -> Unit): () -> Unit
    fun onLock(listener: () -> Unit): () -> Unit
    fun onUnlock(listener: () -> Unit): () -> Unit
    fun onSyncChange(listener: SyncChangeListener): () -> Unit

    // 생체 해제 (SEC-003 규칙 4·8). 생체 인증이 없는 플랫폼(웹)은 기본 구현 그대로 두면 화면에 나오지 않는다

    /** 이 기기에서 생체 해제를 켤 수 있는지 */
    val isBiometricUnlockAvailable: Boolean get() = false

    val isBiometricUnlockEnabled: Boolean get() = false

    /** 세션 만료로 생체 해제 대신 마스터 비밀번호를 기다리는 중인지 (SEC-003 규칙 8) */
    val isBiometricPasswordRequired: Boolean get() = false

    /** 잠금 화면에 생체 해제를 보일지: 켜져 있고, 감싼 키가 있고, 마지막 비밀번호 해제 뒤 14일 안 */
    fun canUnlockWithBiometrics(): Boolean = false

    /**
     * 생체 인증으로 볼트를 연다
     * @return 오프라인으로 열었으면 true
     * @throws BiometricError 취소, 14일 지남, 키 무효, 사용 불가
     */
    suspend fun unlockWithBiometrics(acceptRollback: Boolean = false): Boolean = throw BiometricError.Unavailable()

    /** 열린 볼트의 키를 생체 인증으로 감싸 두고 생체 해제를 켠다. @throws BiometricError 취소, 사용 불가 */
    suspend fun enableBiometricUnlock() {
        throw BiometricError.Unavailable()
    }

    /** 생체 해제를 끄고 감싼 키를 지운다 */
    fun disableBiometricUnlock() = Unit
}

class SessionPortAdapter(private val session: VaultSession) : SessionPort {
    override fun state() = session.state()
    override suspend fun register(email: String, password: String) = session.register(email, password)
    override suspend fun login(email: String, password: String, acceptRollback: Boolean) = session.login(email, password, acceptRollback)
    override fun lock(scope: LockScope) = session.lock(scope)
    override suspend fun unlock(password: String, acceptRollback: Boolean) = session.unlock(password, acceptRollback).offline
    override suspend fun reconnect(password: String, acceptRollback: Boolean) = session.reconnect(password, acceptRollback)
    override suspend fun save(data: VaultData) = session.save(data)
    override suspend fun exportBackup(now: Instant, calendar: LocalCalendar) = session.exportBackup(now, calendar)
    override suspend fun restoreBackup(backup: VaultBackup, password: String, now: Instant) = session.restoreBackup(backup, password, now)
    override suspend fun changeMasterPassword(currentPassword: String, newPassword: String) = session.changeMasterPassword(currentPassword, newPassword)
    override suspend fun deleteAccount(password: String) = session.deleteAccount(password)
    override suspend fun logout() = session.logoutAndRevokeInBackground()
    override suspend fun pendingOnDevice() = session.pendingOnDevice()
    override fun onRemoteChange(listener: () -> Unit) = session.onRemoteChange(listener)
    override fun onLock(listener: () -> Unit) = session.onLock(listener)
    override fun onUnlock(listener: () -> Unit) = session.onUnlock(listener)
    override fun onSyncChange(listener: SyncChangeListener) = session.onSyncChange(listener)
    override val isBiometricUnlockAvailable: Boolean get() = session.isBiometricUnlockAvailable
    override val isBiometricUnlockEnabled: Boolean get() = session.isBiometricUnlockEnabled
    override val isBiometricPasswordRequired: Boolean get() = session.isBiometricPasswordRequired
    override fun canUnlockWithBiometrics() = session.canUnlockWithBiometrics()
    override suspend fun unlockWithBiometrics(acceptRollback: Boolean) = session.unlockWithBiometrics(acceptRollback).offline
    override suspend fun enableBiometricUnlock() = session.enableBiometricUnlock()
    override fun disableBiometricUnlock() = session.disableBiometricUnlock()
}

/** 복사하고 정해진 시간 뒤 비운다 (COM-001). 웹은 ClipboardBurner, iOS는 ClipboardManager */
fun interface ClipboardPort {
    suspend fun copy(text: String)

    /** 복사한 값을 비울 시각. 기다리는 값이 없으면 null. 알 수 없는 플랫폼은 늘 null이다 */
    val burnAt: Instant? get() = null

    /** 비울 시각이 바뀌면 알린다(복사하면 시각, 비웠거나 다른 것을 복사했으면 null). 복사 뒤 안내와 남은 시간 표시에 쓴다 (COM-001) */
    fun subscribeBurn(listener: (Instant?) -> Unit): () -> Unit = {}
}

/** 백업 파일을 저장(다운로드)하고 고른다 (VLT-004) */
interface BackupFilesPort {
    suspend fun save(file: BackupFile)
    /** 사용자가 취소하면 null */
    suspend fun pick(): BackupFileSource?
}

/** 앱 쪽 상태(네트워크 끊김 등). 바뀌면 [subscribe]로 알린다 */
interface AppEnvironment {
    val clock: Clock
    val calendar: LocalCalendar
    val autoLockSetting: AutoLockSettingStore
    /** 서버 주소 표시용. 같은 출처면 빈 문자열 */
    val serverAddress: String
    fun isNetworkOffline(): Boolean
    fun subscribeNetwork(listener: () -> Unit): () -> Unit
}
