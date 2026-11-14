package com.keymanage.feature.unlock

import com.carti.ribs.RibCoroutinesConfig
import com.carti.ribs.RibMount
import com.carti.ribs.mount
import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.backup.VaultBackup
import com.keymanage.domain.session.LockScope
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.SyncChangeListener
import com.keymanage.domain.session.StaleUnlockKeyException
import com.keymanage.domain.session.WrongMasterPasswordException
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.domain.vault.VaultData
import com.keymanage.feature.SessionPort
import com.keymanage.feature.testing.TestDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// 생체 해제 (SEC-003 4·8번, 14일마다 마스터 비밀번호 재요구). 원본: iOS VaultLockManager.unlockWithBiometrics, Features/Unlock
@OptIn(ExperimentalCoroutinesApi::class)
class UnlockBiometricInteractorTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-11-14T00:00:00Z")
    private lateinit var view: BiometricFakeUnlockView
    private lateinit var session: BiometricFakeSession
    private lateinit var listener: BiometricFakeListener
    private lateinit var biometric: FakeBiometricUnlock

    @BeforeTest
    fun setUp() {
        RibCoroutinesConfig.dispatchers = TestDispatchers(dispatcher)
        view = BiometricFakeUnlockView()
        session = BiometricFakeSession()
        listener = BiometricFakeListener()
        biometric = FakeBiometricUnlock()
    }

    @AfterTest
    fun tearDown() {
        RibCoroutinesConfig.reset()
    }

    private fun biometricTest(port: BiometricUnlock? = biometric, keySession: UnlockKeySession? = session, block: suspend TestScope.(RibMount) -> Unit) =
        runTest(dispatcher) {
            val router = UnlockBuilder(
                object : UnlockDependency {
                    override val unlockViewFactory = UnlockViewFactory { view }
                    override val session: SessionPort = this@UnlockBiometricInteractorTest.session
                    override val biometricUnlock: BiometricUnlock? = port
                    override val unlockKeySession: UnlockKeySession? = keySession
                    override val clock: Clock = object : Clock {
                        override fun now(): Instant = this@UnlockBiometricInteractorTest.now
                    }
                },
            ).build(listener)
            val mount = router.mount()
            try {
                block(mount)
            } finally {
                if (mount.isMounted) mount.unmount()
            }
        }

    private fun TestScope.send(action: UnlockViewAction) {
        view.listener?.handle(action)
        runCurrent()
    }

    private fun TestScope.typeAndUnlock(password: String) {
        send(UnlockViewAction.UpdatePassword(password))
        send(UnlockViewAction.Unlock)
    }

    @Test
    fun withoutPortBiometricsAreHiddenAndIgnored() = biometricTest(port = null) {
        assertFalse(view.state.isBiometricsAvailable)
        assertFalse(view.state.offersBiometricEnrollment)

        send(UnlockViewAction.UnlockWithBiometrics)

        assertTrue(session.unlocks.isEmpty())
    }

    @Test
    fun capableDeviceWithoutEnrollmentOffersEnrollment() = biometricTest {
        assertFalse(view.state.isBiometricsAvailable)
        assertTrue(view.state.offersBiometricEnrollment)
    }

    @Test
    fun deviceWithoutBiometricsOffersNothing() {
        biometric.available = false
        biometricTest {
            assertFalse(view.state.isBiometricsAvailable)
            assertFalse(view.state.offersBiometricEnrollment)
        }
    }

    @Test
    fun enrolledAccountShowsButtonInsteadOfOffer() {
        biometric.enroll("user@test.com", STORED_KEY, now)
        biometricTest {
            assertTrue(view.state.isBiometricsAvailable)
            assertFalse(view.state.offersBiometricEnrollment)
        }
    }

    @Test
    fun choosingEnrollmentRemembersExportedKeyNotPasswordAfterPasswordUnlock() = biometricTest {
        send(UnlockViewAction.SetBiometricEnrollment(true))
        assertTrue(view.state.enrollsBiometrics)

        typeAndUnlock("correct horse battery")

        assertEquals(listOf("correct horse battery"), session.keyExports)
        assertTrue(session.unlocks.isEmpty())
        assertEquals(listOf(Remembered("user@test.com", EXPORTED_KEY.toList(), now)), biometric.remembered)
        // 저장소에 넘긴 뒤 메모리의 키 사본은 지운다
        assertTrue(session.lastExported!!.all { it == 0.toByte() })
        assertEquals(listOf(false), listener.unlocked)
    }

    @Test
    fun withoutChoosingEnrollmentKeyIsNotExported() = biometricTest {
        typeAndUnlock("correct horse battery")

        assertTrue(biometric.remembered.isEmpty())
        assertTrue(session.keyExports.isEmpty())
        assertEquals(listOf("correct horse battery"), session.unlocks)
        assertEquals(listOf(false), listener.unlocked)
    }

    @Test
    fun failedPasswordUnlockDoesNotEnroll() = biometricTest {
        session.exportHandler = { _ -> throw WrongMasterPasswordException() }
        send(UnlockViewAction.SetBiometricEnrollment(true))

        typeAndUnlock("wrong password!!")

        assertTrue(biometric.remembered.isEmpty())
    }

    @Test
    fun passwordUnlockOfEnrolledAccountRefreshesReprompt() {
        biometric.enroll("user@test.com", OLD_KEY, now - 13.days)
        biometricTest {
            typeAndUnlock("correct horse battery")

            assertEquals(listOf(Remembered("user@test.com", EXPORTED_KEY.toList(), now)), biometric.remembered)
        }
    }

    @Test
    fun rememberFailureDoesNotBlockUnlock() = biometricTest {
        biometric.rememberFailure = IllegalStateException("keychain")
        send(UnlockViewAction.SetBiometricEnrollment(true))

        typeAndUnlock("correct horse battery")

        assertEquals(listOf(false), listener.unlocked)
    }

    @Test
    fun biometricUnlockUsesRecalledKeyWithoutRefreshingReprompt() {
        biometric.enroll("user@test.com", STORED_KEY, now - 1.days)
        biometricTest {
            session.unlockResult = true

            send(UnlockViewAction.UnlockWithBiometrics)

            assertEquals(listOf(STORED_KEY.toList()), session.keyUnlocks)
            assertTrue(session.unlocks.isEmpty())
            assertEquals(listOf(true), listener.unlocked)
            assertTrue(biometric.remembered.isEmpty())
            // 꺼낸 키는 쓴 뒤 지운다
            assertTrue(biometric.lastRecalled!!.all { it == 0.toByte() })
        }
    }

    @Test
    fun biometricUnlockAfterFourteenDaysAsksForPassword() {
        biometric.enroll("user@test.com", STORED_KEY, now - 14.days - 1.hours)
        biometricTest {
            send(UnlockViewAction.UnlockWithBiometrics)

            assertTrue(session.keyUnlocks.isEmpty())
            assertEquals(0, biometric.recalls)
            assertEquals(UnlockMessages.BIOMETRIC_REPROMPT, view.state.error?.body)
        }
    }

    @Test
    fun biometricUnlockExactlyAtFourteenDaysIsAllowed() {
        biometric.enroll("user@test.com", STORED_KEY, now - 14.days)
        biometricTest {
            send(UnlockViewAction.UnlockWithBiometrics)

            assertEquals(listOf(STORED_KEY.toList()), session.keyUnlocks)
        }
    }

    @Test
    fun cancelledBiometricPromptChangesNothing() {
        biometric.enroll("user@test.com", STORED_KEY, now)
        biometric.recall = BiometricRecall.Cancelled
        biometricTest {
            send(UnlockViewAction.UnlockWithBiometrics)

            assertTrue(session.keyUnlocks.isEmpty())
            assertNull(view.state.error)
            assertFalse(view.state.isLoading)
            assertTrue(view.state.isBiometricsAvailable)
        }
    }

    @Test
    fun changedBiometryInvalidatesEnrollment() {
        biometric.enroll("user@test.com", STORED_KEY, now)
        biometric.recall = BiometricRecall.Invalidated
        biometricTest {
            send(UnlockViewAction.UnlockWithBiometrics)

            assertEquals(listOf("user@test.com"), biometric.forgotten)
            assertFalse(view.state.isBiometricsAvailable)
            assertEquals(UnlockMessages.BIOMETRIC_INVALIDATED, view.state.error?.body)
            assertTrue(session.keyUnlocks.isEmpty())
        }
    }

    @Test
    fun failedBiometricPromptShowsMessage() {
        biometric.enroll("user@test.com", STORED_KEY, now)
        biometric.recall = BiometricRecall.Failed
        biometricTest {
            send(UnlockViewAction.UnlockWithBiometrics)

            assertEquals(UnlockMessages.BIOMETRIC_FAILED, view.state.error?.body)
            assertTrue(view.state.isBiometricsAvailable)
        }
    }

    @Test
    fun staleStoredKeyForgetsEnrollmentAndAsksForPassword() {
        biometric.enroll("user@test.com", OLD_KEY, now)
        biometricTest {
            session.keyHandler = { _ -> throw StaleUnlockKeyException() }

            send(UnlockViewAction.UnlockWithBiometrics)

            assertEquals(listOf("user@test.com"), biometric.forgotten)
            assertFalse(view.state.isBiometricsAvailable)
            assertEquals(UnlockMessages.BIOMETRIC_REPROMPT, view.state.error?.body)
        }
    }

    @Test
    fun biometricRollbackConfirmationReusesKeyThenWipesIt() {
        biometric.enroll("user@test.com", STORED_KEY, now)
        biometricTest {
            session.keyHandler = { _ -> throw RevisionRollbackException(5, 3) }
            send(UnlockViewAction.UnlockWithBiometrics)
            assertTrue(view.state.isConfirmingRollback)
            assertFalse(biometric.lastRecalled!!.all { it == 0.toByte() })

            session.keyHandler = { _ -> false }
            send(UnlockViewAction.AcceptRollback)

            assertEquals(listOf(STORED_KEY.toList(), STORED_KEY.toList()), session.keyUnlocks)
            assertEquals(listOf(false), listener.unlocked)
            assertTrue(biometric.lastRecalled!!.all { it == 0.toByte() })
        }
    }

    @Test
    fun cancellingBiometricRollbackWipesKey() {
        biometric.enroll("user@test.com", STORED_KEY, now)
        biometricTest {
            session.keyHandler = { _ -> throw RevisionRollbackException(5, 3) }
            send(UnlockViewAction.UnlockWithBiometrics)

            send(UnlockViewAction.CancelRollback)

            assertTrue(biometric.lastRecalled!!.all { it == 0.toByte() })
        }
    }

    @Test
    fun wrongPasswordMessageStaysForPasswordUnlock() = biometricTest {
        session.unlockHandler = { _ -> throw WrongMasterPasswordException() }

        typeAndUnlock("wrong password!!")

        assertEquals("마스터 비밀번호가 올바르지 않습니다.", view.state.error?.body)
        assertTrue(biometric.forgotten.isEmpty())
    }

    @Test
    fun withoutKeySessionBiometricsAreHidden() = biometricTest(keySession = null) {
        assertFalse(view.state.isBiometricsAvailable)
        assertFalse(view.state.offersBiometricEnrollment)
    }

    @Test
    fun logoutForgetsEnrollment() {
        biometric.enroll("user@test.com", STORED_KEY, now)
        biometricTest {
            send(UnlockViewAction.RequestLogout)

            assertEquals(1, session.logoutCalls)
            assertEquals(listOf("user@test.com"), biometric.forgotten)
        }
    }
}

private val STORED_KEY = ByteArray(32) { 1 }
private val OLD_KEY = ByteArray(32) { 2 }
private val EXPORTED_KEY = ByteArray(32) { 3 }

private data class Remembered(val email: String, val key: List<Byte>, val at: Instant)

private class FakeBiometricUnlock : BiometricUnlock {
    var available = true
    var recall: BiometricRecall? = null
    var rememberFailure: Exception? = null
    var recalls = 0
    var lastRecalled: ByteArray? = null
    val remembered = mutableListOf<Remembered>()
    val forgotten = mutableListOf<String>()
    private val stored = mutableMapOf<String, Pair<ByteArray, Instant>>()

    fun enroll(email: String, key: ByteArray, at: Instant) {
        stored[email] = key to at
    }

    override fun isAvailable(): Boolean = available
    override fun isEnrolled(email: String): Boolean = email in stored
    override fun passwordUnlockedAt(email: String): Instant? = stored[email]?.second

    override suspend fun remember(email: String, unlockKey: ByteArray, passwordUnlockedAt: Instant) {
        rememberFailure?.let { throw it }
        remembered += Remembered(email, unlockKey.toList(), passwordUnlockedAt)
        stored[email] = unlockKey.copyOf() to passwordUnlockedAt
    }

    override suspend fun recall(email: String): BiometricRecall {
        recalls += 1
        recall?.let { return it }
        val copy = stored[email]?.first?.copyOf() ?: return BiometricRecall.Invalidated
        lastRecalled = copy
        return BiometricRecall.Key(copy)
    }

    override fun forget(email: String) {
        forgotten += email
        stored.remove(email)
    }
}

private class BiometricFakeUnlockView : UnlockViewContract {
    override var listener: UnlockPresentableListener? = null
    val states = mutableListOf<UnlockViewState>()
    val state: UnlockViewState get() = states.last()

    override fun render(state: UnlockViewState) {
        states += state
    }
}

private class BiometricFakeListener : UnlockListener {
    val unlocked = mutableListOf<Boolean>()

    override fun didUnlock(offline: Boolean) {
        unlocked += offline
    }

    override fun didLogout(notice: String?) = Unit
    override fun didRequireSignIn(notice: String) = Unit
}

/** 잠금 해제·로그아웃만 구현한다 */
private class BiometricFakeSession : SessionPort, UnlockKeySession {
    var unlockResult = false
    val unlocks = mutableListOf<String>()
    val keyExports = mutableListOf<String>()
    val keyUnlocks = mutableListOf<List<Byte>>()
    var lastExported: ByteArray? = null
    var exportHandler: suspend (String) -> Boolean = { unlockResult }
    var keyHandler: suspend (ByteArray) -> Boolean = { unlockResult }
    var logoutCalls = 0
    var unlockHandler: suspend (String) -> Boolean = { unlockResult }

    override fun state(): SessionState = SessionState.Locked("user@test.com", false)

    override suspend fun unlock(password: String, acceptRollback: Boolean): Boolean {
        unlocks += password
        return unlockHandler(password)
    }

    override suspend fun unlockExportingKey(password: String, acceptRollback: Boolean): KeyedUnlock {
        keyExports += password
        val offline = exportHandler(password)
        val key = EXPORTED_KEY.copyOf()
        lastExported = key
        return KeyedUnlock(offline, key)
    }

    override suspend fun unlockWithKey(unlockKey: ByteArray, acceptRollback: Boolean): Boolean {
        keyUnlocks += unlockKey.toList()
        return keyHandler(unlockKey)
    }

    override suspend fun logout() {
        logoutCalls += 1
    }

    override suspend fun pendingOnDevice(): Boolean = false

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
