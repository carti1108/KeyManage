package com.keymanage.feature.unlock

import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.backup.VaultBackup
import com.keymanage.domain.session.LockScope
import com.keymanage.domain.session.SessionExpiredException
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.SyncChangeListener
import com.keymanage.domain.session.WrongMasterPasswordException
import com.keymanage.domain.vault.VaultData
import com.keymanage.feature.SessionPort
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

// 마스터 비밀번호 변경·계정 삭제·로그아웃이면 생체 해제용 키를 지운다 (SEC-003, SEC-004, SEC-005, ADR-0027)
class BiometricInvalidatingSessionTest {
    private val inner = InvalidationFakeSession()
    private val biometric = ForgetRecorder()
    private val session = BiometricInvalidatingSession(inner, biometric)

    @Test
    fun passwordChangeForgetsKey() = runTest {
        session.changeMasterPassword("old password!!", "new password!!")

        assertEquals(listOf("user@test.com"), biometric.forgotten)
    }

    @Test
    fun failedPasswordChangeKeepsKey() = runTest {
        inner.failure = WrongMasterPasswordException()

        assertFailsWith<WrongMasterPasswordException> { session.changeMasterPassword("wrong password", "new password!!") }

        assertTrue(biometric.forgotten.isEmpty())
    }

    @Test
    fun passwordChangeThatSignsOutStillForgetsKey() = runTest {
        inner.failure = SessionExpiredException()
        inner.signOutOnFailure = true

        assertFailsWith<SessionExpiredException> { session.changeMasterPassword("old password!!", "new password!!") }

        assertEquals(listOf("user@test.com"), biometric.forgotten)
    }

    @Test
    fun accountDeletionForgetsKey() = runTest {
        session.deleteAccount("password!!")

        assertEquals(listOf("user@test.com"), biometric.forgotten)
    }

    @Test
    fun logoutForgetsKeyEvenWhenDeviceRecordFails() = runTest {
        inner.failure = IllegalStateException("device")

        assertFailsWith<IllegalStateException> { session.logout() }

        assertEquals(listOf("user@test.com"), biometric.forgotten)
    }

    @Test
    fun signedOutSessionHasNoKeyToForget() = runTest {
        inner.current = SessionState.SignedOut

        session.logout()

        assertTrue(biometric.forgotten.isEmpty())
    }
}

private class ForgetRecorder : BiometricUnlock {
    val forgotten = mutableListOf<String>()
    override fun isAvailable() = true
    override fun isEnrolled(email: String) = true
    override fun passwordUnlockedAt(email: String): Instant? = null
    override suspend fun remember(email: String, unlockKey: ByteArray, passwordUnlockedAt: Instant) = Unit
    override suspend fun recall(email: String): BiometricRecall = BiometricRecall.Cancelled
    override fun forget(email: String) {
        forgotten += email
    }
}

/** 비밀번호 변경·계정 삭제·로그아웃만 구현한다. 실패하면 [signOutOnFailure]에 따라 로그인 전 상태가 된다 */
private class InvalidationFakeSession : SessionPort {
    var current: SessionState = SessionState.Locked("user@test.com", false)
    var failure: Exception? = null
    var signOutOnFailure = false

    override fun state(): SessionState = current

    private fun finish(signsOut: Boolean) {
        val error = failure
        if (error != null) {
            if (signOutOnFailure) current = SessionState.SignedOut
            throw error
        }
        if (signsOut) current = SessionState.SignedOut
    }

    override suspend fun changeMasterPassword(currentPassword: String, newPassword: String) = finish(signsOut = false)
    override suspend fun deleteAccount(password: String) = finish(signsOut = true)
    override suspend fun logout() {
        // 로그아웃은 실패해도 이 기기는 이미 로그아웃된 상태다
        current = SessionState.SignedOut
        failure?.let { throw it }
    }

    override suspend fun register(email: String, password: String) = unused()
    override suspend fun login(email: String, password: String, acceptRollback: Boolean) = unused()
    override fun lock(scope: LockScope) = unused()
    override suspend fun unlock(password: String, acceptRollback: Boolean): Boolean = unused()
    override suspend fun reconnect(password: String, acceptRollback: Boolean) = unused()
    override suspend fun save(data: VaultData) = unused()
    override suspend fun exportBackup(now: Instant, calendar: LocalCalendar): BackupFile = unused()
    override suspend fun restoreBackup(backup: VaultBackup, password: String, now: Instant): Int = unused()
    override suspend fun pendingOnDevice(): Boolean = unused()
    override fun onRemoteChange(listener: () -> Unit): () -> Unit = unused()
    override fun onLock(listener: () -> Unit): () -> Unit = unused()
    override fun onUnlock(listener: () -> Unit): () -> Unit = unused()
    override fun onSyncChange(listener: SyncChangeListener): () -> Unit = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("무효화 장식이 부르지 않는 세션 기능")
}
