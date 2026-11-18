package com.keymanage.android.feature.settings

import com.keymanage.android.domain.auth.NewPasswordProblem
import com.keymanage.android.domain.session.AccountError
import com.keymanage.android.feature.settings.impl.SettingsInteractor
import com.keymanage.android.feature.settings.impl.SettingsMessages
import com.keymanage.android.feature.settings.impl.SettingsPresenter
import com.keymanage.android.feature.settings.impl.SettingsScreen
import com.keymanage.android.feature.settings.impl.SettingsViewAction
import com.keymanage.android.feature.settings.impl.SettingsViewState
import com.keymanage.android.feature.settings.testing.MemoryAutoLockStore
import com.keymanage.android.feature.settings.testing.RecordingSettingsListener
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsAccountTest {
    private var failure: Exception? = null
    private val changes = mutableListOf<Triple<String, String, String>>()
    private val deletes = mutableListOf<String>()
    private val account = object : AccountPort {
        override fun changePassword(current: String, new: String, confirmation: String) {
            failure?.let { throw it }
            changes += Triple(current, new, confirmation)
        }

        override fun deleteAccount(password: String) {
            failure?.let { throw it }
            deletes += password
        }
    }
    private val listener = RecordingSettingsListener()
    private val store = ViewStateStore(SettingsViewState(email = "dev@example.com"))
    private val interactor = SettingsInteractor(
        SettingsPresenter(store, ViewActionStream()), store, ViewActionStream(), MemoryAutoLockStore(), { false }, listener,
        workDispatcher = UnconfinedTestDispatcher(), account = account,
    )

    private fun fillPassword() {
        interactor.handle(SettingsViewAction.OpenPasswordChange)
        interactor.handle(SettingsViewAction.PasswordFieldsChanged("current-password", "new-password!", "new-password!"))
    }

    @Test
    fun `비밀번호를 바꾸면 완료 문구를 보이고 입력을 지운다`() = runTest {
        fillPassword()
        interactor.changePassword()
        assertEquals(listOf(Triple("current-password", "new-password!", "new-password!")), changes)
        assertEquals(SettingsMessages.PASSWORD_CHANGED, store.current.password.message)
        assertEquals("", store.current.password.current)
    }

    @Test
    fun `비밀번호 변경 실패는 SEC-004 문구로 알린다`() = runTest {
        val cases = mapOf<Exception, String>(
            AccountError.WrongPassword() to SettingsMessages.WRONG_PASSWORD,
            AccountError.SamePassword() to SettingsMessages.SAME_PASSWORD,
            AccountError.WeakNewPassword(NewPasswordProblem.MISMATCH) to SettingsMessages.MISMATCH,
            AccountError.Offline() to SettingsMessages.PASSWORD_OFFLINE,
            AccountError.Conflict() to SettingsMessages.CONFLICT,
        )
        for ((error, message) in cases) {
            fillPassword()
            failure = error
            interactor.changePassword()
            assertEquals(message, store.current.password.message)
            assertFalse(store.current.password.isWorking)
        }
    }

    @Test
    fun `비밀번호 변경 중 세션이 만료되면 다시 로그인하게 한다`() = runTest {
        fillPassword()
        failure = AccountError.LoginRequired()
        interactor.changePassword()
        assertEquals(1, listener.sessionExpiredCount)
    }

    @Test
    fun `확인 문구가 정확히 같아야 삭제할 수 있다`() {
        interactor.handle(SettingsViewAction.OpenDeleteAccount)
        assertEquals(SettingsScreen.DELETE, store.current.screen)
        interactor.handle(SettingsViewAction.DeleteFieldsChanged("pw", "계정삭제"))
        assertFalse(store.current.delete.canDelete)
        interactor.handle(SettingsViewAction.DeleteFieldsChanged("pw", "계정 삭제"))
        assertTrue(store.current.delete.canDelete)
    }

    @Test
    fun `계정을 지우면 부모에 알린다`() = runTest {
        interactor.handle(SettingsViewAction.OpenDeleteAccount)
        interactor.handle(SettingsViewAction.DeleteFieldsChanged("pw", "계정 삭제"))
        interactor.deleteAccount()
        assertEquals(listOf("pw"), deletes)
        assertEquals(1, listener.deletedCount)
    }

    @Test
    fun `계정 삭제 실패는 SEC-005 문구로 알린다`() = runTest {
        interactor.handle(SettingsViewAction.OpenDeleteAccount)
        interactor.handle(SettingsViewAction.DeleteFieldsChanged("pw", "계정 삭제"))
        failure = AccountError.Offline()
        interactor.deleteAccount()
        assertEquals(SettingsMessages.DELETE_OFFLINE, store.current.delete.message)
        failure = AccountError.WrongPassword()
        interactor.deleteAccount()
        assertEquals(SettingsMessages.WRONG_PASSWORD, store.current.delete.message)
        assertEquals(0, listener.deletedCount)
    }
}
