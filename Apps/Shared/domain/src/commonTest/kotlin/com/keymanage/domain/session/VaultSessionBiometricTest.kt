package com.keymanage.domain.session

import com.keymanage.domain.auth.AuthService
import com.keymanage.domain.auth.MasterPasswordChangedException
import com.keymanage.domain.lock.BiometricError
import com.keymanage.domain.lock.BiometricKeyStore
import com.keymanage.domain.lock.BiometricSetting
import com.keymanage.domain.lock.BiometricSettingStore
import com.keymanage.domain.lock.BiometricUnlock
import com.keymanage.domain.tabs.SingleTab
import com.keymanage.domain.vault.RemoteVault
import com.keymanage.domain.vault.RevisionMemory
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.domain.vault.addItem
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// 세션의 생체 해제 (SEC-003 규칙 4·8, ADR-AND-002). 설계: docs/research/kmp-migration/android-migration-plan.md 7장 2단계

private const val EMAIL = "user@example.com"
private const val NEW_PASSWORD = "new-master-password-1"

private class FakeBiometricKeys : BiometricKeyStore {
    var sealed: ByteArray? = null
    var failure: BiometricError? = null
    var opens = 0

    /** 설정하면 꺼내기가 이 문이 열릴 때까지 기다린다(생체 인증 창이 떠 있는 동안) */
    var openGate: Gate? = null

    override fun hasKey() = sealed != null

    /** 설정하면 감싸기가 이 문이 열릴 때까지 기다린다 */
    var sealGate: Gate? = null

    override suspend fun seal(key: ByteArray) {
        sealGate?.pass()
        failure?.let { throw it }
        sealed = key.copyOf()
    }

    override suspend fun open(): ByteArray {
        opens += 1
        openGate?.pass()
        failure?.let { throw it }
        // 실제 저장소처럼 키가 없으면 무효로 알린다
        return sealed?.copyOf() ?: throw BiometricError.Invalidated()
    }

    override fun clear() {
        sealed = null
    }
}

private class MemoryBiometricSettings : BiometricSettingStore {
    var setting = BiometricSetting()

    override fun read() = setting

    override fun write(setting: BiometricSetting) {
        this.setting = setting
    }
}

/** 한 기기. 앱을 다시 켜도 서버·기기 기록·감싼 키·생체 설정은 남는다 */
private class Device {
    val authApi = FakeAuthApi()
    val vaultApi = FakeVaultApi()
    val deviceStore = FakeDeviceStore()
    val keys = FakeBiometricKeys()
    val settings = MemoryBiometricSettings()
    var now: Instant = NOW
    val clock = object : Clock {
        override fun now(): Instant = now
    }

    init {
        authApi.vaultApi = vaultApi
    }

    fun start(scope: TestScope, withBiometrics: Boolean = true): VaultSession = VaultSession(
        VaultSessionDeps(
            auth = AuthService(authApi),
            vaultSyncFor = vaultSyncFor(vaultApi),
            revisionMemory = RevisionMemory(),
            deviceStore = deviceStore,
            tabs = SingleTab(),
            scope = scope.backgroundScope,
            biometric = if (withBiometrics) BiometricUnlock(keys, settings, clock) else null,
        ),
    )

    /** 앱을 다시 켠다. 메모리의 세션 토큰은 사라지고 기기 기록으로 잠긴 화면이 된다 */
    suspend fun restart(scope: TestScope): VaultSession = start(scope).also { it.restore() }
}

private fun unlockedData(session: VaultSession) =
    (session.state() as? SessionState.Unlocked ?: error("잠금 해제 상태가 아님")).vault.data

/** 로그인해 항목 a를 저장하고 생체 해제를 켠 뒤 잠근다 */
private suspend fun TestScope.lockedWithBiometrics(device: Device): VaultSession {
    val session = device.start(this)
    session.login(EMAIL, PASSWORD)
    session.save(addItem(unlockedData(session), fields, NOW, "a"))
    session.enableBiometricUnlock()
    session.lock()
    return session
}

class VaultSessionBiometricEnableTest {
    @Test
    fun enablingOffersBiometricUnlockOnlyWhileLocked() = sessionTest {
        val device = Device()
        val session = device.start(this)
        session.login(EMAIL, PASSWORD)
        assertFalse(session.isBiometricUnlockEnabled)

        session.enableBiometricUnlock()

        assertTrue(session.isBiometricUnlockEnabled)
        assertFalse(session.canUnlockWithBiometrics(), "열려 있는 동안에는 제안하지 않는다")
        session.lock()
        assertTrue(session.canUnlockWithBiometrics())
    }

    @Test
    fun enablingWhileLockedIsRejected() = sessionTest {
        val device = Device()
        val session = device.start(this)
        session.login(EMAIL, PASSWORD)
        session.lock()

        assertFailsWith<SessionStateException> { session.enableBiometricUnlock() }

        assertFalse(session.isBiometricUnlockEnabled)
        assertNull(device.keys.sealed)
    }

    @Test
    fun cancelledPromptLeavesBiometricUnlockOff() = sessionTest {
        val device = Device()
        val session = device.start(this)
        session.login(EMAIL, PASSWORD)
        device.keys.failure = BiometricError.Cancelled()

        assertFailsWith<BiometricError.Cancelled> { session.enableBiometricUnlock() }

        assertFalse(session.isBiometricUnlockEnabled)
    }

    @Test
    fun platformWithoutBiometricsCannotUseIt() = sessionTest {
        val device = Device()
        val session = device.start(this, withBiometrics = false)
        session.login(EMAIL, PASSWORD)

        assertFailsWith<BiometricError.Unavailable> { session.enableBiometricUnlock() }
        session.lock()

        assertFalse(session.canUnlockWithBiometrics())
        assertFailsWith<BiometricError.Unavailable> { session.unlockWithBiometrics() }
    }

    @Test
    fun disablingWipesTheWrappedKey() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)

        session.disableBiometricUnlock()

        assertFalse(session.canUnlockWithBiometrics())
        assertNull(device.keys.sealed)
        assertFailsWith<BiometricError.Disabled> { session.unlockWithBiometrics() }
        assertIs<SessionState.Locked>(session.state())
    }
}

class VaultSessionBiometricUnlockTest {
    @Test
    fun opensTheVaultWithoutPasswordOrServerLogin() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        val loginsBefore = device.authApi.logins.size
        var unlocks = 0
        session.onUnlock { unlocks++ }

        val result = session.unlockWithBiometrics()

        assertFalse(result.offline)
        assertEquals(listOf("a"), activeIds(session.state()))
        assertEquals(loginsBefore, device.authApi.logins.size)
        assertEquals(1, unlocks)
    }

    @Test
    fun withASessionItLoadsChangesFromOtherDevices() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        saveFromOtherDevice(device.vaultApi, "b")

        session.unlockWithBiometrics()

        assertEquals(listOf("a", "b"), activeIds(session.state())?.sorted())
    }

    @Test
    fun pendingChangesAreMergedAndPushed() = sessionTest {
        val device = Device()
        val session = device.start(this)
        session.login(EMAIL, PASSWORD)
        session.enableBiometricUnlock()
        device.vaultApi.offline = true
        session.save(addItem(unlockedData(session), fields, NOW, "a"))
        session.lock()
        device.vaultApi.offline = false

        session.unlockWithBiometrics()

        assertEquals(listOf("a"), serverItemIds(device.vaultApi))
        assertEquals(false, device.deviceStore.record?.pendingSync)
    }

    @Test
    fun afterRestartItOpensWithoutASessionAndDoesNotLogIn() = sessionTest {
        val device = Device()
        lockedWithBiometrics(device)
        val restarted = device.restart(this)
        val loginsBefore = device.authApi.logins.size
        assertTrue(restarted.canUnlockWithBiometrics())

        val result = restarted.unlockWithBiometrics()

        assertTrue(result.offline)
        assertEquals(listOf("a"), activeIds(restarted.state()))
        assertEquals(loginsBefore, device.authApi.logins.size)
    }

    @Test
    fun reconnectingWithThePasswordSyncsAfterSessionlessUnlock() = sessionTest {
        val device = Device()
        lockedWithBiometrics(device)
        val restarted = device.restart(this)
        restarted.unlockWithBiometrics()
        saveFromOtherDevice(device.vaultApi, "b")

        restarted.reconnect(PASSWORD)

        assertEquals(listOf("a", "b"), activeIds(restarted.state())?.sorted())
    }

    @Test
    fun alreadyUnlockedDoesNotPrompt() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        session.unlock(PASSWORD)

        session.unlockWithBiometrics()

        assertEquals(0, device.keys.opens)
    }

    @Test
    fun cancelledPromptStaysLockedAndKeepsTheSetting() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        device.keys.failure = BiometricError.Cancelled()

        assertFailsWith<BiometricError.Cancelled> { session.unlockWithBiometrics() }

        assertIs<SessionState.Locked>(session.state())
        assertTrue(session.isBiometricUnlockEnabled)
    }

    @Test
    fun invalidatedKeyTurnsBiometricUnlockOffAndStaysLocked() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        device.keys.failure = BiometricError.Invalidated()

        assertFailsWith<BiometricError.Invalidated> { session.unlockWithBiometrics() }

        assertIs<SessionState.Locked>(session.state())
        assertFalse(session.isBiometricUnlockEnabled)
        assertNull(device.keys.sealed)
    }

    @Test
    fun keyThatCannotOpenTheVaultIsWipedAndTurnedOff() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        device.keys.sealed = ByteArray(32) { 9 }

        assertFailsWith<BiometricError.Invalidated> { session.unlockWithBiometrics() }

        assertIs<SessionState.Locked>(session.state())
        assertFalse(session.isBiometricUnlockEnabled)
        assertNull(device.keys.sealed)
    }

    @Test
    fun logoutWhileThePromptIsShowingDoesNotOpenTheVault() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        val gate = Gate()
        device.keys.openGate = gate
        val pending = begin { session.unlockWithBiometrics() }
        waitFor { gate.waiting == 1 }

        session.logout()
        gate.release()

        assertFailsWith<SessionStateException> { pending.await() }
        assertIs<SessionState.SignedOut>(session.state())
    }
}

class VaultSessionBiometricPasswordIntervalTest {
    @Test
    fun loginAndPasswordUnlockRecordTheLastPasswordUnlock() = sessionTest {
        val device = Device()
        val session = device.start(this)
        assertNull(device.settings.setting.lastPasswordUnlock)

        session.login(EMAIL, PASSWORD)
        assertEquals(NOW, device.settings.setting.lastPasswordUnlock)

        session.lock()
        device.now = NOW + 3.days
        session.unlock(PASSWORD)
        assertEquals(NOW + 3.days, device.settings.setting.lastPasswordUnlock)
    }

    @Test
    fun registerRecordsTheLastPasswordUnlock() = sessionTest {
        val device = Device()
        val session = device.start(this)

        session.register(EMAIL, PASSWORD)

        assertEquals(NOW, device.settings.setting.lastPasswordUnlock)
    }

    @Test
    fun wrongPasswordDoesNotRecordIt() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        device.now = NOW + 3.days

        assertFailsWith<WrongMasterPasswordException> { session.unlock("wrong-password") }

        assertEquals(NOW, device.settings.setting.lastPasswordUnlock)
    }

    @Test
    fun biometricUnlockDoesNotExtendTheInterval() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        device.now = NOW + 3.days

        session.unlockWithBiometrics()

        assertEquals(NOW, device.settings.setting.lastPasswordUnlock)
    }

    @Test
    fun afterFourteenDaysThePasswordIsRequiredAndThenBiometricsWorkAgain() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        device.now = NOW + 14.days
        assertTrue(session.canUnlockWithBiometrics())
        device.now = NOW + 14.days + 1.seconds

        assertFalse(session.canUnlockWithBiometrics())
        assertFailsWith<BiometricError.Expired> { session.unlockWithBiometrics() }
        assertIs<SessionState.Locked>(session.state())
        assertNotNull(device.keys.sealed, "키는 남긴다")

        session.unlock(PASSWORD)
        session.lock()
        assertTrue(session.canUnlockWithBiometrics())
    }

    @Test
    fun reconnectRecordsTheLastPasswordUnlock() = sessionTest {
        val device = Device()
        lockedWithBiometrics(device)
        val restarted = device.restart(this)
        restarted.unlockWithBiometrics()
        device.now = NOW + 2.days

        restarted.reconnect(PASSWORD)

        assertEquals(NOW + 2.days, device.settings.setting.lastPasswordUnlock)
    }
}

class VaultSessionBiometricKeyWipeTest {
    @Test
    fun logoutWipesTheWrappedKey() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)

        session.logout()

        assertNull(device.keys.sealed)
        assertFalse(session.isBiometricUnlockEnabled)
    }

    @Test
    fun backgroundRevokeLogoutWipesTheWrappedKey() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)

        session.logoutAndRevokeInBackground()

        assertNull(device.keys.sealed)
        assertFalse(session.isBiometricUnlockEnabled)
    }

    @Test
    fun changingTheMasterPasswordWipesTheWrappedKey() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        session.unlock(PASSWORD)

        session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

        assertNull(device.keys.sealed)
        assertFalse(session.isBiometricUnlockEnabled)
    }

    @Test
    fun failedPasswordChangeKeepsBiometricUnlock() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        session.unlock(PASSWORD)

        assertFailsWith<WrongMasterPasswordException> { session.changeMasterPassword("wrong-password", NEW_PASSWORD) }

        assertNotNull(device.keys.sealed)
        assertTrue(session.isBiometricUnlockEnabled)
    }

    @Test
    fun deletingTheAccountWipesTheWrappedKey() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        session.unlock(PASSWORD)

        session.deleteAccount(PASSWORD)

        assertNull(device.keys.sealed)
        assertFalse(session.isBiometricUnlockEnabled)
    }

    @Test
    fun passwordChangedElsewhereWipesTheWrappedKey() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        // 서버의 KDF 값이 바뀌었다(다른 기기에서 비밀번호 변경)
        device.authApi.kdf = device.authApi.kdf.copy(salt = "AAAAAAAAAAAAAAAAAAAAAA==")

        assertFailsWith<MasterPasswordChangedException> { session.unlock("the-new-password") }

        assertNull(device.keys.sealed)
        assertFalse(session.isBiometricUnlockEnabled)
    }

    @Test
    fun loggingInAsAnotherAccountWipesTheWrappedKey() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)

        session.login("other@example.com", PASSWORD)

        assertNull(device.keys.sealed)
        assertFalse(session.isBiometricUnlockEnabled)
    }

    @Test
    fun loggingInAgainAsTheSameAccountKeepsBiometricUnlock() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)

        session.login(EMAIL, PASSWORD)
        session.lock()

        assertTrue(session.canUnlockWithBiometrics())
        session.unlockWithBiometrics()
        assertEquals(listOf("a"), activeIds(session.state()))
    }
}

// 독립 리뷰(2026-11-20)에서 나온 경우
class VaultSessionBiometricReviewTest {
    @Test
    fun lockWhileThePromptIsShowingDoesNotOpenTheVault() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        val gate = Gate()
        device.keys.openGate = gate
        val pending = begin { session.unlockWithBiometrics() }
        waitFor { gate.waiting == 1 }

        session.lock()
        gate.release()

        assertFailsWith<SessionStateException> { pending.await() }
        assertIs<SessionState.Locked>(session.state())
        assertTrue(session.isBiometricUnlockEnabled)
    }

    @Test
    fun passwordUnlockWhileThePromptIsShowingIsNotOverwritten() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        val gate = Gate()
        device.keys.openGate = gate
        val pending = begin { session.unlockWithBiometrics() }
        waitFor { gate.waiting == 1 }
        session.unlock(PASSWORD)
        session.save(addItem(unlockedData(session), fields, NOW, "b"))

        gate.release()
        pending.await()

        assertEquals(listOf("a", "b"), activeIds(session.state())?.sorted())
    }

    @Test
    fun logoutWhileEnablingDoesNotLeaveAWrappedKey() = sessionTest {
        val device = Device()
        val session = device.start(this)
        session.login(EMAIL, PASSWORD)
        val gate = Gate()
        device.keys.sealGate = gate
        val pending = begin { session.enableBiometricUnlock() }
        waitFor { gate.waiting == 1 }

        session.logout()
        gate.release()

        assertFailsWith<SessionStateException> { pending.await() }
        assertNull(device.keys.sealed)
        assertFalse(session.isBiometricUnlockEnabled)
    }

    @Test
    fun rollbackStaysLockedAndKeepsBiometricUnlockUntilAccepted() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        // 서버를 백업(revision 1)에서 되돌린 상황
        val restored = RemoteVault(1, device.vaultApi.payload!!)
        device.vaultApi.getHook = { restored }

        assertFailsWith<RevisionRollbackException> { session.unlockWithBiometrics() }
        assertIs<SessionState.Locked>(session.state())
        assertTrue(session.canUnlockWithBiometrics())

        session.unlockWithBiometrics(acceptRollback = true)
        assertIs<SessionState.Unlocked>(session.state())
    }

    @Test
    fun switchingAccountAfterAFailedAttemptWipesTheWrappedKey() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        device.authApi.offline = true
        assertFailsWith<Exception> { session.login("other@example.com", PASSWORD) }
        assertNotNull(device.keys.sealed, "다른 계정의 인증이 성공하기 전에는 그대로 둔다")
        device.authApi.offline = false

        session.login("other@example.com", PASSWORD)

        assertNull(device.keys.sealed)
        assertFalse(session.isBiometricUnlockEnabled)
    }

    @Test
    fun loggingInWithAPasswordChangedElsewhereWipesTheWrappedKey() = sessionTest {
        val device = Device()
        lockedWithBiometrics(device)
        // 다른 기기에서 비밀번호를 바꿨다
        val other = VaultSession(
            VaultSessionDeps(
                auth = AuthService(device.authApi),
                vaultSyncFor = vaultSyncFor(device.vaultApi),
                revisionMemory = RevisionMemory(),
                deviceStore = FakeDeviceStore(),
                tabs = SingleTab(),
                scope = backgroundScope,
            ),
        )
        other.login(EMAIL, PASSWORD)
        other.changeMasterPassword(PASSWORD, NEW_PASSWORD)
        val restarted = device.restart(this)
        assertNotNull(device.keys.sealed)

        restarted.login(EMAIL, NEW_PASSWORD)

        assertNull(device.keys.sealed)
        assertFalse(restarted.isBiometricUnlockEnabled)
    }

    @Test
    fun changingTheMasterPasswordRestartsTheFourteenDays() = sessionTest {
        val device = Device()
        val session = lockedWithBiometrics(device)
        session.unlock(PASSWORD)
        device.now = NOW + 3.days

        session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

        assertEquals(NOW + 3.days, device.settings.setting.lastPasswordUnlock)
    }
}
