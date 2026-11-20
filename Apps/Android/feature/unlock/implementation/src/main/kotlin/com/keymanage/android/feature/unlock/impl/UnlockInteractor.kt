package com.keymanage.android.feature.unlock.impl

import com.keymanage.android.domain.lock.BiometricError
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.UnlockError
import com.keymanage.android.domain.sync.SyncError
import com.keymanage.android.feature.unlock.BiometricUnlocker
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
    val canUseBiometrics: Boolean = false,
    /** 서버 볼트가 되돌아가 있어 받아들일지 묻는 중(VLT-001 규칙 9). */
    val isConfirmingRollback: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && password.isNotEmpty()

    override fun toString() = "UnlockViewState(email=$email, isSubmitting=$isSubmitting, errorMessage=$errorMessage, canUseBiometrics=$canUseBiometrics)"
}

sealed interface UnlockViewAction {
    data class PasswordChanged(val value: String) : UnlockViewAction
    data object Submit : UnlockViewAction
    data object Logout : UnlockViewAction
    data object UnlockWithBiometrics : UnlockViewAction
    data object AcceptRollback : UnlockViewAction
    data object CancelRollback : UnlockViewAction
}

/** SEC-003·COM-002 사용자 문구. */
object UnlockMessages {
    const val WRONG_PASSWORD = "마스터 비밀번호가 올바르지 않습니다."
    const val PASSWORD_CHANGED = "마스터 비밀번호가 바뀌었습니다. 새 비밀번호로 로그인해 주세요."
    const val BIOMETRIC_BUTTON = "생체 인증으로 잠금 해제"
    const val BIOMETRIC_EXPIRED = "보안을 위해 14일마다 마스터 비밀번호를 입력해야 합니다."
    const val BIOMETRIC_INVALIDATED = "생체 정보가 바뀌어 생체 인증을 껐습니다. 마스터 비밀번호로 잠금을 풀어 주세요."
    const val BIOMETRIC_UNAVAILABLE = "지금은 생체 인증을 쓸 수 없습니다. 마스터 비밀번호를 입력해 주세요."
    const val ROLLBACK_TITLE = "서버 볼트가 이전 상태로 돌아감"
    const val ROLLBACK_BODY = "서버의 볼트가 이 기기에서 마지막으로 본 상태보다 이전으로 돌아갔습니다. 서버를 복구한 경우가 아니라면 받아들이지 마세요."
    const val ROLLBACK_ACCEPT = "서버 볼트 받아들이기"
    const val UNKNOWN ="알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
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
    private val biometric: BiometricUnlocker? = null,
) : BasicInteractor<UnlockPresenter, BasicComposeRouter<UnlockInteractor>>(presenter) {
    /** 되돌림으로 막힌 잠금 해제. 사용자가 받아들이면 같은 방법으로 다시 푼다(VLT-001 규칙 9). */
    private class RollbackRetry(val serverRevision: Int, val viaBiometrics: Boolean)

    private var rollbackRetry: RollbackRetry? = null

    init {
        store.update { it.copy(canUseBiometrics = biometric?.isAvailable() == true) }
    }

    override fun didBecomeActive(savedInstanceState: Bundle?) {
        super.didBecomeActive(savedInstanceState)
        actions.actions.onEach(::handle).launchIn(coroutineScope)
    }

    override fun willResignActive() {
        rollbackRetry = null
        store.update { it.copy(password = "", isConfirmingRollback = false) }
        super.willResignActive()
    }

    fun handle(action: UnlockViewAction) {
        when (action) {
            is UnlockViewAction.PasswordChanged -> store.update { it.copy(password = action.value, errorMessage = null) }
            UnlockViewAction.Submit -> coroutineScope.launch { submit() }
            UnlockViewAction.Logout -> if (!store.current.isSubmitting) listener.onLogoutRequested()
            UnlockViewAction.UnlockWithBiometrics -> coroutineScope.launch { unlockWithBiometrics() }
            UnlockViewAction.AcceptRollback -> coroutineScope.launch { acceptRollback() }
            UnlockViewAction.CancelRollback -> {
                rollbackRetry = null
                store.update { it.copy(isConfirmingRollback = false) }
            }
        }
    }

    /** 되돌림 경고에서 "서버 볼트 받아들이기"를 눌렀다. 받아들인 뒤 같은 방법으로 다시 푼다. 테스트는 직접 부른다. */
    suspend fun acceptRollback() {
        val retry = rollbackRetry ?: return
        rollbackRetry = null
        store.update { it.copy(isConfirmingRollback = false, isSubmitting = true, errorMessage = null) }
        try {
            withContext(workDispatcher) { unlocker.acceptRollback(retry.serverRevision) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            store.update { it.copy(isSubmitting = false, errorMessage = UnlockMessages.UNKNOWN) }
            return
        }
        store.update { it.copy(isSubmitting = false) }
        if (retry.viaBiometrics) unlockWithBiometrics() else submit()
    }

    private fun askRollback(error: SyncError.RevisionRollback, viaBiometrics: Boolean) {
        rollbackRetry = RollbackRetry(error.serverRevision, viaBiometrics)
        store.update { it.copy(isSubmitting = false, isConfirmingRollback = true) }
    }

    /** 생체 해제(SEC-003 규칙 4·8). 취소는 그대로 두고, 쓸 수 없게 되면 버튼을 숨기고 비밀번호를 요구한다. 테스트는 직접 부른다. */
    suspend fun unlockWithBiometrics() {
        val biometric = biometric ?: return
        if (store.current.isSubmitting || !store.current.canUseBiometrics) return
        store.update { it.copy(isSubmitting = true, errorMessage = null) }
        val opened = try {
            biometric.unlock(session)
        } catch (error: CancellationException) {
            throw error
        } catch (error: BiometricError.Cancelled) {
            store.update { it.copy(isSubmitting = false) }
            return
        } catch (error: BiometricError) {
            store.update { it.copy(isSubmitting = false, canUseBiometrics = false, errorMessage = biometricMessageOf(error)) }
            return
        } catch (error: UnlockError.NoDeviceRecord) {
            store.update { it.copy(isSubmitting = false) }
            listener.onLoginRequired(null)
            return
        } catch (error: SyncError.RevisionRollback) {
            askRollback(error, viaBiometrics = true)
            return
        } catch (error: Exception) {
            store.update { it.copy(isSubmitting = false, errorMessage = UnlockMessages.UNKNOWN) }
            return
        }
        store.update { it.copy(isSubmitting = false, password = "") }
        listener.onUnlocked(opened)
    }

    private fun biometricMessageOf(error: BiometricError): String = when (error) {
        is BiometricError.Expired -> UnlockMessages.BIOMETRIC_EXPIRED
        is BiometricError.Unavailable -> UnlockMessages.BIOMETRIC_UNAVAILABLE
        else -> UnlockMessages.BIOMETRIC_INVALIDATED
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
        } catch (error: SyncError.RevisionRollback) {
            askRollback(error, viaBiometrics = false)
            return
        } catch (error: Exception) {
            val message =if (error is UnlockError.WrongPassword) UnlockMessages.WRONG_PASSWORD else UnlockMessages.UNKNOWN
            store.update { it.copy(isSubmitting = false, errorMessage = message) }
            return
        }
        store.update { it.copy(isSubmitting = false, password = "") }
        listener.onUnlocked(opened)
    }
}
