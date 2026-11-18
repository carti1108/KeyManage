package com.keymanage.android.feature.auth.impl

import com.keymanage.android.domain.auth.AuthUseCase
import com.keymanage.android.feature.auth.AuthListener
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.BasicComposeRouter
import com.uber.rib.core.BasicInteractor
import com.uber.rib.core.Bundle
import com.uber.rib.core.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 로그인·가입 입력을 받아 [AuthUseCase]를 부르고, 성공하면 [listener]에 결과를 넘긴다(SEC-002). */
class AuthInteractor(
    presenter: AuthPresenter,
    private val store: ViewStateStore<AuthViewState>,
    private val actions: ViewActionStream<AuthViewAction>,
    private val authUseCase: AuthUseCase,
    private val listener: AuthListener,
    private val workDispatcher: CoroutineDispatcher,
) : BasicInteractor<AuthPresenter, BasicComposeRouter<AuthInteractor>>(presenter) {
    override fun didBecomeActive(savedInstanceState: Bundle?) {
        super.didBecomeActive(savedInstanceState)
        actions.actions.onEach(::handle).launchIn(coroutineScope)
    }

    override fun willResignActive() {
        store.update { it.copy(password = "", confirmation = "") }
        super.willResignActive()
    }

    /** 입력 동작은 바로 상태에 반영하고, 제출은 RIB 수명 동안 도는 코루틴으로 넘긴다. */
    fun handle(action: AuthViewAction) {
        when (action) {
            is AuthViewAction.EmailChanged -> store.update { it.copy(email = action.value, errorMessage = null) }
            is AuthViewAction.PasswordChanged -> store.update { it.copy(password = action.value, errorMessage = null) }
            is AuthViewAction.ConfirmationChanged -> store.update { it.copy(confirmation = action.value, errorMessage = null) }
            AuthViewAction.SwitchMode -> store.update {
                val next = if (it.mode == AuthMode.LOGIN) AuthMode.SIGNUP else AuthMode.LOGIN
                it.copy(mode = next, confirmation = "", errorMessage = null)
            }
            AuthViewAction.Submit -> coroutineScope.launch { submit() }
        }
    }

    /** 제출. 진행 중이거나 빈 칸이 있으면 무시한다. 테스트는 이 함수를 직접 부른다. */
    suspend fun submit() {
        val state = store.current
        if (!state.canSubmit) return
        store.update { it.copy(isSubmitting = true, errorMessage = null) }
        val result = try {
            withContext(workDispatcher) {
                when (state.mode) {
                    AuthMode.LOGIN -> authUseCase.login(state.email, state.password)
                    AuthMode.SIGNUP -> authUseCase.register(state.email, state.password, state.confirmation)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            store.update { it.copy(isSubmitting = false, errorMessage = AuthMessages.of(error)) }
            return
        }
        store.update { it.copy(isSubmitting = false, password = "", confirmation = "") }
        listener.onAuthenticated(result)
    }
}
