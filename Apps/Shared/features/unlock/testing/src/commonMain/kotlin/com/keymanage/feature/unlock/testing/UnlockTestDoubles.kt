package com.keymanage.feature.unlock.testing

import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.backup.VaultBackup
import com.keymanage.domain.session.LockScope
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.SyncChangeListener
import com.keymanage.domain.vault.VaultData
import com.keymanage.feature.SessionPort
import com.keymanage.feature.unlock.UnlockListener
import com.keymanage.feature.unlock.UnlockPresentableListener
import com.keymanage.feature.unlock.UnlockViewContract
import com.keymanage.feature.unlock.UnlockViewState
import kotlin.time.Instant

// Unlock 피처의 테스트 더블. 테스트(features/unlock/tests)와 각 플랫폼의 Example 앱이 함께 쓴다

/** 그린 상태를 모두 기록하는 가짜 뷰 */
class FakeUnlockView : UnlockViewContract {
    override var listener: UnlockPresentableListener? = null
    val states = mutableListOf<UnlockViewState>()
    val state: UnlockViewState get() = states.last()

    override fun render(state: UnlockViewState) {
        states += state
    }
}

/** 부모에 알린 내용을 기록한다. [onEvent]를 넣으면 알릴 때마다 한 줄 설명을 넘긴다(Example 앱의 결과 표시) */
class FakeUnlockListener(private val onEvent: (String) -> Unit = {}) : UnlockListener {
    val unlocked = mutableListOf<Boolean>()
    val loggedOut = mutableListOf<String?>()
    val signInRequests = mutableListOf<String>()

    override fun didUnlock(offline: Boolean) {
        unlocked += offline
        onEvent(if (offline) "didUnlock(offline = true)" else "didUnlock(offline = false)")
    }

    override fun didLogout(notice: String?) {
        loggedOut += notice
        onEvent("didLogout(notice = $notice)")
    }

    override fun didRequireSignIn(notice: String) {
        signInRequests += notice
        onEvent("didRequireSignIn(notice = $notice)")
    }
}

/** 잠금 해제·로그아웃에 쓰는 기능만 구현한다. 나머지는 이 RIB이 부르지 않으므로 호출되면 실패한다. */
class FakeUnlockSession : SessionPort {
    data class UnlockCall(val password: String, val acceptRollback: Boolean)

    var email = "user@test.com"
    var pendingSync = false
    var pendingOnDevice = false
    var unlockResult = false
    val unlocks = mutableListOf<UnlockCall>()
    var logoutCalls = 0
    var pendingOnDeviceCalls = 0

    var unlockHandler: suspend (String, Boolean) -> Boolean = { _, _ -> unlockResult }
    var logoutHandler: suspend () -> Unit = {}
    var pendingOnDeviceHandler: suspend () -> Boolean = { pendingOnDevice }

    /** 잠금 화면에 생체 해제를 보일지 */
    var biometricsUsable = false

    /** 세션 만료로 생체 해제 대신 마스터 비밀번호를 기다리는 중 */
    var biometricPasswordRequired = false

    /** 생체 해제 호출마다 받은 acceptRollback */
    val biometricUnlocks = mutableListOf<Boolean>()
    var biometricUnlockHandler: suspend (Boolean) -> Boolean = { false }

    override fun state(): SessionState = SessionState.Locked(email, pendingSync)

    override suspend fun unlock(password: String, acceptRollback: Boolean): Boolean {
        unlocks += UnlockCall(password, acceptRollback)
        return unlockHandler(password, acceptRollback)
    }

    override suspend fun logout() {
        logoutCalls += 1
        logoutHandler()
    }

    override suspend fun pendingOnDevice(): Boolean {
        pendingOnDeviceCalls += 1
        return pendingOnDeviceHandler()
    }

    override fun canUnlockWithBiometrics(): Boolean = biometricsUsable

    override val isBiometricPasswordRequired: Boolean get() = biometricPasswordRequired

    override suspend fun unlockWithBiometrics(acceptRollback: Boolean): Boolean {
        biometricUnlocks += acceptRollback
        return biometricUnlockHandler(acceptRollback)
    }

    override suspend fun register(email: String, password: String) = unused()
    override suspend fun login(email: String, password: String, acceptRollback: Boolean) = unused()
    override fun lock(scope: LockScope) = unused()
    override suspend fun reconnect(password: String, acceptRollback: Boolean) = unused()
    override suspend fun save(data: VaultData) = unused()
    override suspend fun exportBackup(now: Instant, calendar: LocalCalendar): BackupFile = unused()
    override suspend fun restoreBackup(backup: VaultBackup, password: String, now: Instant): Int = unused()
    override suspend fun changeMasterPassword(currentPassword: String, newPassword: String) = unused()
    override suspend fun deleteAccount(password: String) = unused()
    override fun onRemoteChange(listener: () -> Unit): () -> Unit = unused()
    override fun onLock(listener: () -> Unit): () -> Unit = unused()
    override fun onUnlock(listener: () -> Unit): () -> Unit = unused()
    override fun onSyncChange(listener: SyncChangeListener): () -> Unit = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("Unlock RIB이 부르지 않는 세션 기능")
}
