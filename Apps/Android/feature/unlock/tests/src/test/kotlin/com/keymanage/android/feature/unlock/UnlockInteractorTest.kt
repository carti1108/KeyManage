package com.keymanage.android.feature.unlock

import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.OpenedVault
import com.keymanage.android.domain.session.UnlockError
import com.keymanage.android.domain.sync.DeviceAccount
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.unlock.impl.UnlockInteractor
import com.keymanage.android.feature.unlock.impl.UnlockMessages
import com.keymanage.android.feature.unlock.impl.UnlockPresenter
import com.keymanage.android.feature.unlock.impl.UnlockViewAction
import com.keymanage.android.feature.unlock.impl.UnlockViewState
import com.keymanage.android.feature.unlock.testing.RecordingUnlockListener
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

class UnlockInteractorTest {
    private val session = Session("tok", Instant.parse("2026-12-18T00:00:00Z"))
    private val opened = OpenedVault(
        DeviceAccount("dev@example.com", 600_000, "AAAAAAAAAAAAAAAAAAAAAA=="),
        VaultData(1, Instant.EPOCH, emptyList()),
        session,
        ByteArray(32),
    )
    private val unlocker = StubUnlocker { opened }
    private val listener = RecordingUnlockListener()
    private val store = ViewStateStore(UnlockViewState(email = "dev@example.com"))
    private val interactor = UnlockInteractor(
        UnlockPresenter(store, ViewActionStream()), store, ViewActionStream(), session, unlocker, listener, UnconfinedTestDispatcher(),
    )

    @Test
    fun `비밀번호가 비었으면 제출하지 않는다`() = runTest {
        interactor.submit()
        assertTrue(unlocker.calls.isEmpty())
    }

    @Test
    fun `잠금이 풀리면 결과를 부모에 넘기고 비밀번호를 지운다`() = runTest {
        // Given
        interactor.handle(UnlockViewAction.PasswordChanged("password"))

        // When
        interactor.submit()

        // Then
        assertEquals<List<Pair<String, Session?>>>(listOf("password" to session), unlocker.calls)
        assertEquals(listOf(opened), listener.unlocked)
        assertEquals("", store.current.password)
        assertFalse(store.current.isSubmitting)
    }

    @Test
    fun `틀린 비밀번호는 SEC-003 문구로 알린다`() = runTest {
        unlocker.failure = UnlockError.WrongPassword()
        interactor.handle(UnlockViewAction.PasswordChanged("wrong"))
        interactor.submit()
        assertEquals(UnlockMessages.WRONG_PASSWORD, store.current.errorMessage)
        assertTrue(listener.unlocked.isEmpty())
    }

    @Test
    fun `비밀번호가 바뀌었으면 로그인 화면으로 보낸다`() = runTest {
        unlocker.failure = UnlockError.PasswordChanged()
        interactor.handle(UnlockViewAction.PasswordChanged("password"))
        interactor.submit()
        assertEquals(listOf<String?>(UnlockMessages.PASSWORD_CHANGED), listener.loginRequired)
    }

    @Test
    fun `기기 기록이 없으면 문구 없이 로그인 화면으로 보낸다`() = runTest {
        unlocker.failure = UnlockError.NoDeviceRecord()
        interactor.handle(UnlockViewAction.PasswordChanged("password"))
        interactor.submit()
        assertEquals(listOf<String?>(null), listener.loginRequired)
    }

    @Test
    fun `예상하지 못한 오류는 COM-002 문구`() = runTest {
        unlocker.failure = IllegalStateException("x")
        interactor.handle(UnlockViewAction.PasswordChanged("password"))
        interactor.submit()
        assertEquals(UnlockMessages.UNKNOWN, store.current.errorMessage)
    }

    @Test
    fun `로그아웃 동작은 부모에 요청한다`() {
        interactor.handle(UnlockViewAction.Logout)
        assertEquals(1, listener.logoutRequests)
    }
}
