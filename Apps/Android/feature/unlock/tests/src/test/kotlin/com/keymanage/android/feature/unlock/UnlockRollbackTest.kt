package com.keymanage.android.feature.unlock

import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.OpenedVault
import com.keymanage.android.domain.sync.DeviceAccount
import com.keymanage.android.domain.sync.SyncError
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.unlock.impl.UnlockInteractor
import com.keymanage.android.feature.unlock.impl.UnlockMessages
import com.keymanage.android.feature.unlock.impl.UnlockPresenter
import com.keymanage.android.feature.unlock.impl.UnlockViewAction
import com.keymanage.android.feature.unlock.impl.UnlockViewState
import com.keymanage.android.feature.unlock.testing.RecordingUnlockListener
import com.keymanage.android.feature.unlock.testing.StubBiometricUnlocker
import com.keymanage.android.feature.unlock.testing.StubUnlocker
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** VLT-001 규칙 9: 잠금 해제 때 서버 볼트가 되돌아가 있으면 사용자가 확인한 경우에만 받아들인다. */
class UnlockRollbackTest {
    private val session = Session("tok", Instant.parse("2026-12-18T00:00:00Z"))
    private val opened = OpenedVault(
        DeviceAccount("dev@example.com", 600_000, "AAAAAAAAAAAAAAAAAAAAAA=="),
        VaultData(1, Instant.EPOCH, emptyList()),
        session,
        ByteArray(32),
    )
    private val rollback = SyncError.RevisionRollback(serverRevision = 2, lastSeenRevision = 5)
    private val unlocker = StubUnlocker { opened }
    private val biometric = StubBiometricUnlocker { opened }
    private val listener = RecordingUnlockListener()
    private val store = ViewStateStore(UnlockViewState(email = "dev@example.com"))
    private val interactor = UnlockInteractor(
        UnlockPresenter(store, ViewActionStream()), store, ViewActionStream(), session, unlocker, listener,
        UnconfinedTestDispatcher(), biometric,
    )

    @Test
    fun `되돌림이면 확인을 묻고 받아들이면 그 revision으로 다시 잠금을 푼다`() = runTest {
        // Given
        unlocker.failure = rollback
        interactor.handle(UnlockViewAction.PasswordChanged("password"))
        interactor.submit()
        assertTrue(store.current.isConfirmingRollback)
        assertTrue(listener.unlocked.isEmpty())
        unlocker.failure = null

        // When
        interactor.acceptRollback()

        // Then
        assertEquals(listOf(2), unlocker.acceptedRollbacks)
        assertEquals(2, unlocker.calls.size)
        assertEquals(listOf(opened), listener.unlocked)
        assertFalse(store.current.isConfirmingRollback)
    }

    @Test
    fun `취소하면 받아들이지 않고 잠금 화면에 남는다`() = runTest {
        // Given
        unlocker.failure = rollback
        interactor.handle(UnlockViewAction.PasswordChanged("password"))
        interactor.submit()

        // When
        interactor.handle(UnlockViewAction.CancelRollback)
        interactor.acceptRollback()

        // Then
        assertFalse(store.current.isConfirmingRollback)
        assertTrue(unlocker.acceptedRollbacks.isEmpty())
        assertEquals(1, unlocker.calls.size)
        assertTrue(listener.unlocked.isEmpty())
    }

    @Test
    fun `생체 해제에서 되돌림을 받아들이면 생체 해제를 다시 한다`() = runTest {
        // Given
        biometric.failure = rollback
        interactor.unlockWithBiometrics()
        assertTrue(store.current.isConfirmingRollback)
        biometric.failure = null

        // When
        interactor.acceptRollback()

        // Then
        assertEquals(listOf(2), unlocker.acceptedRollbacks)
        assertEquals(2, biometric.sessions.size)
        assertTrue(unlocker.calls.isEmpty())
        assertEquals(listOf(opened), listener.unlocked)
    }

    @Test
    fun `되돌림 문구는 정책 원문과 같다`() {
        assertEquals("서버 볼트가 이전 상태로 돌아감", UnlockMessages.ROLLBACK_TITLE)
        assertEquals(
            "서버의 볼트가 이 기기에서 마지막으로 본 상태보다 이전으로 돌아갔습니다. 서버를 복구한 경우가 아니라면 받아들이지 마세요.",
            UnlockMessages.ROLLBACK_BODY,
        )
        assertEquals("서버 볼트 받아들이기", UnlockMessages.ROLLBACK_ACCEPT)
    }
}
