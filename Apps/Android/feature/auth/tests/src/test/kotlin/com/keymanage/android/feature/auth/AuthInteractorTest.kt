package com.keymanage.android.feature.auth

import com.keymanage.android.domain.remote.ApiError
import com.keymanage.android.domain.remote.ServerErrorCode
import com.keymanage.android.feature.auth.impl.AuthInteractor
import com.keymanage.android.feature.auth.impl.AuthMessages
import com.keymanage.android.feature.auth.impl.AuthMode
import com.keymanage.android.feature.auth.impl.AuthPresenter
import com.keymanage.android.feature.auth.impl.AuthViewAction
import com.keymanage.android.feature.auth.impl.AuthViewState
import com.keymanage.android.feature.auth.testing.RecordingAuthListener
import com.keymanage.android.feature.auth.testing.StubAuthApi
import com.keymanage.android.feature.auth.testing.stubAuthUseCase
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthInteractorTest {
    private val api = StubAuthApi()
    private val listener = RecordingAuthListener()
    private val store = ViewStateStore(AuthViewState())
    private val interactor = AuthInteractor(
        AuthPresenter(store, ViewActionStream()), store, ViewActionStream(), stubAuthUseCase(api), listener, UnconfinedTestDispatcher(),
    )

    private fun type(email: String, password: String, confirmation: String? = null) {
        interactor.handle(AuthViewAction.EmailChanged(email))
        interactor.handle(AuthViewAction.PasswordChanged(password))
        confirmation?.let { interactor.handle(AuthViewAction.ConfirmationChanged(it)) }
    }

    @Test
    fun `입력 동작은 상태에 반영되고 빈 칸이 있으면 제출할 수 없다`() {
        type("dev@example.com", "")
        assertEquals("dev@example.com", store.current.email)
        assertFalse(store.current.canSubmit)

        interactor.handle(AuthViewAction.PasswordChanged("pw"))
        assertTrue(store.current.canSubmit)
    }

    @Test
    fun `모드를 바꾸면 확인 칸과 오류 문구를 지운다`() = runTest {
        // Given
        interactor.handle(AuthViewAction.SwitchMode)
        type("bad", "password", "password")
        interactor.submit()

        // When
        interactor.handle(AuthViewAction.SwitchMode)

        // Then
        assertEquals(AuthMode.LOGIN, store.current.mode)
        assertEquals("", store.current.confirmation)
        assertNull(store.current.errorMessage)
    }

    @Test
    fun `로그인에 성공하면 결과를 부모에 넘기고 비밀번호를 지운다`() = runTest {
        // Given
        type("Dev@Example.com", "password")

        // When
        interactor.submit()

        // Then
        assertEquals("dev@example.com", listener.results.single().account.email)
        assertEquals("", store.current.password)
        assertFalse(store.current.isSubmitting)
    }

    @Test
    fun `인증 실패는 SEC-002 문구로 보이고 부모에 알리지 않는다`() = runTest {
        // Given
        api.failure = ApiError.Server(401, ServerErrorCode.AUTH_FAILED, null)
        type("dev@example.com", "password")

        // When
        interactor.submit()

        // Then
        assertEquals(AuthMessages.LOGIN_FAILED, store.current.errorMessage)
        assertTrue(listener.results.isEmpty())
        assertFalse(store.current.isSubmitting)
    }

    @Test
    fun `가입 비밀번호가 짧으면 서버에 보내지 않고 길이 문구를 보인다`() = runTest {
        // Given
        interactor.handle(AuthViewAction.SwitchMode)
        type("dev@example.com", "short", "short")

        // When
        interactor.submit()

        // Then
        assertEquals(AuthMessages.TOO_SHORT, store.current.errorMessage)
        assertEquals(0, api.registerCalls)
    }

    @Test
    fun `가입에 성공하면 결과를 부모에 넘긴다`() = runTest {
        interactor.handle(AuthViewAction.SwitchMode)
        type("dev@example.com", "a".repeat(12), "a".repeat(12))
        interactor.submit()
        assertEquals(1, api.registerCalls)
        assertEquals(1, listener.results.size)
    }

    @Test
    fun `중복 가입·네트워크 오류 문구`() = runTest {
        interactor.handle(AuthViewAction.SwitchMode)
        type("dev@example.com", "a".repeat(12), "a".repeat(12))

        api.failure = ApiError.Server(409, ServerErrorCode.EMAIL_ALREADY_REGISTERED, null)
        interactor.submit()
        assertEquals(AuthMessages.EMAIL_TAKEN, store.current.errorMessage)

        api.failure = ApiError.Network(IOException("offline"))
        interactor.submit()
        assertEquals(AuthMessages.NETWORK, store.current.errorMessage)
    }

    @Test
    fun `제출할 수 없는 상태면 아무것도 보내지 않는다`() = runTest {
        type("dev@example.com", "")
        interactor.submit()
        assertEquals(0, api.loginCalls)
    }
}
