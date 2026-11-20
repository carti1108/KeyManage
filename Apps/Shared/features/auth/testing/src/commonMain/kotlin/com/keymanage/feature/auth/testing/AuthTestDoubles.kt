package com.keymanage.feature.auth.testing

import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.backup.VaultBackup
import com.keymanage.domain.session.LockScope
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.SyncChangeListener
import com.keymanage.domain.vault.VaultData
import com.keymanage.feature.SessionPort
import com.keymanage.feature.auth.AuthListener
import kotlin.time.Instant

// Auth 피처의 테스트 더블. 테스트(features/auth/tests)와 각 플랫폼의 Example 앱이 함께 쓴다

/** 부모에 알린 횟수를 센다. [onEvent]를 넣으면 알릴 때마다 한 줄 설명을 넘긴다(Example 앱의 결과 표시) */
class FakeAuthListener(private val onEvent: (String) -> Unit = {}) : AuthListener {
    var completions = 0

    override fun authDidComplete() {
        completions += 1
        onEvent("authDidComplete()")
    }
}

/** 로그인·가입만 쓴다. 나머지는 이 RIB이 부르지 않으므로 호출되면 실패한다. */
class FakeAuthSession : SessionPort {
    data class LoginCall(val email: String, val password: String, val acceptRollback: Boolean)
    data class RegisterCall(val email: String, val password: String)

    val logins = mutableListOf<LoginCall>()
    val registers = mutableListOf<RegisterCall>()
    var loginHandler: suspend (String, String, Boolean) -> Unit = { _, _, _ -> }
    var registerHandler: suspend (String, String) -> Unit = { _, _ -> }

    override suspend fun login(email: String, password: String, acceptRollback: Boolean) {
        logins += LoginCall(email, password, acceptRollback)
        loginHandler(email, password, acceptRollback)
    }

    override suspend fun register(email: String, password: String) {
        registers += RegisterCall(email, password)
        registerHandler(email, password)
    }

    override fun state(): SessionState = SessionState.SignedOut
    override fun lock(scope: LockScope) = unused()
    override suspend fun unlock(password: String, acceptRollback: Boolean): Boolean = unused()
    override suspend fun reconnect(password: String, acceptRollback: Boolean) = unused()
    override suspend fun save(data: VaultData) = unused()
    override suspend fun exportBackup(now: Instant, calendar: LocalCalendar): BackupFile = unused()
    override suspend fun restoreBackup(backup: VaultBackup, password: String, now: Instant): Int = unused()
    override suspend fun changeMasterPassword(currentPassword: String, newPassword: String) = unused()
    override suspend fun deleteAccount(password: String) = unused()
    override suspend fun logout() = unused()
    override suspend fun pendingOnDevice(): Boolean = unused()
    override fun onRemoteChange(listener: () -> Unit): () -> Unit = unused()
    override fun onLock(listener: () -> Unit): () -> Unit = unused()
    override fun onUnlock(listener: () -> Unit): () -> Unit = unused()
    override fun onSyncChange(listener: SyncChangeListener): () -> Unit = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("Auth RIB이 부르지 않는 세션 기능")
}
