package com.keymanage.android.feature.unlock.impl

import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.UnlockError
import com.keymanage.android.feature.unlock.UnlockListener
import com.keymanage.android.feature.unlock.Unlocker
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

/** 잠금 화면 상태. */
data class UnlockViewState(
    val email: String,
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && password.isNotEmpty()

    override fun toString() = "UnlockViewState(email=$email, isSubmitting=$isSubmitting, errorMessage=$errorMessage)"
}

sealed interface UnlockViewAction {
    data class PasswordChanged(val value: String) : UnlockViewAction
    data object Submit : UnlockViewAction
    data object Logout : UnlockViewAction
}

/** SEC-003·COM-002 사용자 문구. */
object UnlockMessages {
    const val WRONG_PASSWORD = "마스터 비밀번호가 올바르지 않습니다."
    const val PASSWORD_CHANGED = "마스터 비밀번호가 바뀌었습니다. 새 비밀번호로 로그인해 주세요."
    const val UNKNOWN = "알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
}

/** 마스터 비밀번호로 잠금을 푼다(SEC-003 규칙 4·10~12). */
class UnlockInteractor(
    presenter: UnlockPresenter,
    private val store: ViewStateStore<UnlockViewState>,
    private val actions: ViewActionStream<UnlockViewAction>,
    private val session: Session?,
    private val unlocker: Unlocker,
    private val listener: UnlockListener,
    private val workDispatcher: CoroutineDispatcher,
) : BasicInteractor<UnlockPresenter, BasicComposeRouter<UnlockInteractor>>(presenter) {
    override fun didBecomeActive(savedInstanceState: Bundle?) {
        super.didBecomeActive(savedInstanceState)
        actions.actions.onEach(::handle).launchIn(coroutineScope)
    }

    override fun willResignActive() {
        store.update { it.copy(password = "") }
        super.willResignActive()
    }

    fun handle(action: UnlockViewAction) {
        when (action) {
            is UnlockViewAction.PasswordChanged -> store.update { it.copy(password = action.value, errorMessage = null) }
            UnlockViewAction.Submit -> coroutineScope.launch { submit() }
            UnlockViewAction.Logout -> if (!store.current.isSubmitting) listener.onLogoutRequested()
        }
    }

    /** 제출. 진행 중이거나 비밀번호가 비었으면 무시한다. 테스트는 이 함수를 직접 부른다. */
    suspend fun submit() {
        val state = store.current
        if (!state.canSubmit) return
        store.update { it.copy(isSubmitting = true, errorMessage = null) }
        val opened = try {
            withContext(workDispatcher) { unlocker.unlock(state.password, session) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: UnlockError.PasswordChanged) {
            store.update { it.copy(isSubmitting = false, password = "") }
            listener.onLoginRequired(UnlockMessages.PASSWORD_CHANGED)
            return
        } catch (error: UnlockError.NoDeviceRecord) {
            store.update { it.copy(isSubmitting = false, password = "") }
            listener.onLoginRequired(null)
            return
        } catch (error: Exception) {
            val message = if (error is UnlockError.WrongPassword) UnlockMessages.WRONG_PASSWORD else UnlockMessages.UNKNOWN
            store.update { it.copy(isSubmitting = false, errorMessage = message) }
            return
        }
        store.update { it.copy(isSubmitting = false, password = "") }
        listener.onUnlocked(opened)
    }
}
