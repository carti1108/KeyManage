package com.keymanage.feature.unlock

import com.carti.ribs.PresentableInteractor
import com.carti.ribs.RibDispatchers
import com.carti.ribs.checkMainThread
import com.keymanage.domain.device.DeviceStorageException
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.StaleUnlockKeyException
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.feature.SessionPort
import com.keymanage.feature.messages.ErrorMessage
import com.keymanage.feature.messages.LOGOUT_CLEAR_FAILED
import com.keymanage.feature.messages.describeError
import com.keymanage.feature.messages.describeErrorMessage
import com.keymanage.feature.messages.sendsToSignIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock

// 잠금 화면 (SEC-003). 마스터 비밀번호로 열고, 동기화 대기 변경을 확인한 뒤 로그아웃한다 (VLT-002)
// 원본: Apps/Web/src/ui/screens/UnlockScreen.tsx, ui/session/SessionRoot.tsx의 unlock·logout 흐름, iOS Features/Unlock

/** 상태를 들고 입력을 해석한다. 요청은 활성 구간 스코프에서 실행하므로 detach되면 취소된다 */
class UnlockInteractor(
    presenter: UnlockPresentable,
    private val session: SessionPort,
    private val listener: UnlockListener,
    private val biometric: BiometricUnlock? = null,
    private val clock: Clock = Clock.System,
    private val keySession: UnlockKeySession? = null,
) : PresentableInteractor<UnlockPresentable>(presenter), UnlockPresentableListener {
    private var state = UnlockViewState()

    /** 되돌림 경고를 받아들이면 다시 보낼 비밀번호. 입력 칸은 비우지만 처음 요청의 값을 쓴다 */
    private var rollbackPassword: String? = null

    /** 생체 해제로 연 요청이 되돌림 경고를 받았을 때 다시 쓸 키 사본. 받아들이거나 취소하면 지운다 */
    private var rollbackKey: ByteArray? = null

    // 다른 탭의 대기 내용을 확인하는 동안 로그아웃을 다시 받지 않는다
    private var isCheckingLogout = false

    private var logoutInFlight = false

    /**
     * 로그아웃이 진행 중이다. 세션이 먼저 로그인 전 상태로 알려도 부모는 이 작업이 끝나 결과를 알릴 때까지 이 화면을 내리지 않는다.
     * 내리면 기기 기록 정리가 취소된다 (ADR-0019)
     */
    val isFinishingSession: Boolean get() = logoutInFlight

    override fun didBecomeActive() {
        presenter.listener = this
        val email = (session.state() as? SessionState.Locked)?.email.orEmpty()
        state = state.copy(email = email).withBiometrics(email)
        render()
    }

    override fun willResignActive() {
        presenter.listener = null
        clearRollback()
    }

    private fun clearRollback() {
        rollbackPassword = null
        rollbackKey?.fill(0)
        rollbackKey = null
    }

    override fun handle(action: UnlockViewAction) {
        // 내려간 뒤 늦게 들어온 입력(뷰가 쥐고 있던 listener)은 무시한다
        if (!isActive) return
        when (action) {
            is UnlockViewAction.UpdatePassword -> update { copy(password = action.password) }
            UnlockViewAction.Unlock -> unlockWithPassword(state.password, acceptRollback = false)
            UnlockViewAction.UnlockWithBiometrics -> unlockWithBiometrics()
            is UnlockViewAction.SetBiometricEnrollment ->
                if (state.offersBiometricEnrollment) update { copy(enrollsBiometrics = action.enabled) }
            UnlockViewAction.AcceptRollback -> acceptRollback()
            UnlockViewAction.CancelRollback -> {
                clearRollback()
                update { copy(isConfirmingRollback = false) }
            }
            UnlockViewAction.RequestLogout -> requestLogout()
            UnlockViewAction.ConfirmLogout -> if (state.isConfirmingLogout) logout()
            UnlockViewAction.CancelLogout -> update { copy(isConfirmingLogout = false) }
        }
    }

    private fun update(change: UnlockViewState.() -> UnlockViewState) {
        state = state.change()
        // 로그아웃은 이 RIB이 내려간 뒤에도 끝까지 하므로 그때는 그리지 않는다
        if (isActive) render()
    }

    private fun render() {
        checkMainThread("UnlockPresentable.render")
        presenter.render(state)
    }

    private fun UnlockViewState.withBiometrics(email: String): UnlockViewState {
        val port = biometric
        if (port == null || keySession == null || email.isEmpty() || !port.isAvailable()) {
            return copy(isBiometricsAvailable = false, offersBiometricEnrollment = false, enrollsBiometrics = false)
        }
        val enrolled = port.isEnrolled(email)
        return copy(
            isBiometricsAvailable = enrolled,
            offersBiometricEnrollment = !enrolled,
            enrollsBiometrics = enrollsBiometrics && !enrolled,
        )
    }

    private fun acceptRollback() {
        rollbackKey?.let { key ->
            rollbackKey = null
            unlockWithKey(key, acceptRollback = true)
            return
        }
        val password = rollbackPassword ?: return
        unlockWithPassword(password, acceptRollback = true)
    }

    /** 마지막 비밀번호 해제로부터 14일이 지났으면 비밀번호를 받는다. 생체 정보가 바뀌었으면 끈다 (SEC-003 8번) */
    private fun unlockWithBiometrics() {
        val port = biometric ?: return
        if (keySession == null) return
        if (!state.isBiometricsAvailable || state.isLoading) return
        val email = state.email
        val unlockedAt = port.passwordUnlockedAt(email)
        if (unlockedAt == null || clock.now() - unlockedAt > BIOMETRIC_PASSWORD_REPROMPT_INTERVAL) {
            update { copy(error = ErrorMessage(body = UnlockMessages.BIOMETRIC_REPROMPT)) }
            return
        }
        update { copy(isLoading = true, error = null, isConfirmingRollback = false) }
        coroutineScope.launch {
            val recall = try {
                port.recall(email)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                BiometricRecall.Failed
            }
            when (recall) {
                is BiometricRecall.Key -> {
                    // 인증 중에 이 RIB이 내려갔으면(취소) 꺼낸 키를 쓰지 않고 지운다
                    if (!this.isActive) {
                        recall.bytes.fill(0)
                        return@launch
                    }
                    update { copy(isLoading = false) }
                    unlockWithKey(recall.bytes, acceptRollback = false)
                }
                BiometricRecall.Cancelled -> update { copy(isLoading = false) }
                BiometricRecall.Invalidated -> {
                    port.forget(email)
                    update {
                        copy(isLoading = false, error = ErrorMessage(body = UnlockMessages.BIOMETRIC_INVALIDATED)).withBiometrics(email)
                    }
                }
                BiometricRecall.Failed -> update { copy(isLoading = false, error = ErrorMessage(body = UnlockMessages.BIOMETRIC_FAILED)) }
            }
        }
    }

    /** 비밀번호로 열 때 생체 해제를 켜거나 재요구 기준 시각을 새로 해야 하는지 */
    private fun refreshesBiometrics(): Boolean =
        biometric != null && keySession != null && (state.enrollsBiometrics || state.isBiometricsAvailable)

    /** 비밀번호로 연다. 생체 해제를 켰거나 켜기로 했으면 세션이 내보낸 키를 저장한다 (ADR-0027) */
    private fun unlockWithPassword(password: String, acceptRollback: Boolean) {
        val keys = keySession
        val port = biometric
        val email = state.email
        if (!refreshesBiometrics() || keys == null || port == null) {
            unlock(acceptRollback, onRollback = { rollbackPassword = password }) { session.unlock(password, acceptRollback) }
            return
        }
        unlock(acceptRollback, onRollback = { rollbackPassword = password }) {
            val opened = keys.unlockExportingKey(password, acceptRollback)
            try {
                port.remember(email, opened.unlockKey, clock.now())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 다음 잠금 화면에서 생체 해제 버튼이 보이지 않을 뿐 볼트는 열렸다. 사용자는 비밀번호로 다시 켤 수 있다
            } finally {
                opened.unlockKey.fill(0)
            }
            opened.offline
        }
    }

    /** 생체 인증으로 꺼낸 키로 연다. 14일 재요구 기준 시각은 새로 하지 않는다. 키는 쓴 뒤 지운다(되돌림 확인 중이면 그때까지 둔다) */
    private fun unlockWithKey(key: ByteArray, acceptRollback: Boolean) {
        val keys = keySession ?: return key.fill(0)
        var keepForRollback = false
        unlock(acceptRollback, onRollback = { keepForRollback = true; rollbackKey = key }, onDone = { if (!keepForRollback) key.fill(0) }) {
            keys.unlockWithKey(key, acceptRollback)
        }
    }

    private fun unlock(
        acceptRollback: Boolean,
        onRollback: () -> Unit,
        onDone: () -> Unit = {},
        request: suspend () -> Boolean,
    ) {
        if (state.isLoading) return onDone()
        clearRollback()
        update { copy(isLoading = true, error = null, isConfirmingRollback = false) }
        coroutineScope.launch {
            val result = try {
                Result.success(request())
            } catch (cancelled: CancellationException) {
                onDone()
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            // 실패한 비밀번호를 입력 칸에 남기지 않는다. 되돌림 확인용 재시도는 요청에 담긴 값을 쓴다
            val failure = result.exceptionOrNull()
            if (failure is RevisionRollbackException && !acceptRollback) onRollback()
            onDone()
            when {
                failure == null -> {
                    update { copy(isLoading = false, password = "") }
                    // 부모가 이 RIB을 내릴 수 있으므로 알림은 마지막에 한다
                    listener.didUnlock(offline = result.getOrDefault(false))
                }
                failure is RevisionRollbackException && !acceptRollback ->
                    update { copy(isLoading = false, password = "", isConfirmingRollback = true) }
                sendsToSignIn(failure) -> {
                    update { copy(isLoading = false, password = "") }
                    listener.didRequireSignIn(describeError(failure))
                }
                failure is StaleUnlockKeyException -> {
                    // 다른 곳에서 비밀번호를 바꿔 저장한 키로 열 수 없다. 새 비밀번호로 열 때 다시 켠다
                    biometric?.forget(state.email)
                    update {
                        copy(isLoading = false, password = "", error = ErrorMessage(body = UnlockMessages.BIOMETRIC_REPROMPT)).withBiometrics(state.email)
                    }
                }
                else -> update { copy(isLoading = false, password = "", error = describeErrorMessage(failure)) }
            }
        }
    }

    /**
     * 올리지 못한 변경 사항이 있으면 사라진다고 먼저 알린다 (VLT-002).
     * 이 탭에 없어도 다른 탭이 기기 기록에 남겼을 수 있으니 기록을 확인한다 (ADR-WEB-001)
     */
    private fun requestLogout() {
        if (state.isLoading || isCheckingLogout) return
        if ((session.state() as? SessionState.Locked)?.pendingSync == true) {
            update { copy(isConfirmingLogout = true) }
            return
        }
        isCheckingLogout = true
        coroutineScope.launch {
            try {
                // 읽지 못하면 다른 탭의 대기 내용을 알 수 없으니 있다고 보고 확인을 받는다
                val pending = try {
                    session.pendingOnDevice()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    true
                }
                if (pending) update { copy(isConfirmingLogout = true) } else logout()
            } finally {
                isCheckingLogout = false
            }
        }
    }

    /**
     * 기기 정리가 끝나면 부모에 알린다. 서버 토큰 폐기는 기다리지 않는다. 세션이 자기 범위에서 끝까지 하므로 오프라인·느린 서버에서도 화면이 바로 바뀐다 (SEC-002).
     * 세션은 이 기기 기록을 정리하는 도중 알림을 보내 부모가 이 화면을 내릴 수 있다. 그래도 정리는 끝까지 해야 하고
     * 결과(기록을 지우지 못했다는 안내)도 알려야 하므로 이 RIB의 스코프가 아닌 별도 스코프에서 한다
     */
    private fun logout() {
        if (state.isLoading) return
        logoutInFlight = true
        update { copy(isLoading = true, error = null, isConfirmingLogout = false) }
        CoroutineScope(SupervisorJob() + RibDispatchers.MainImmediate).launch {
            val notice = try {
                session.logout()
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: DeviceStorageException) {
                // 기기 기록을 지우지 못했으면 공용 기기에 볼트가 남으므로 알린다 (ADR-0019)
                LOGOUT_CLEAR_FAILED
            } catch (_: Exception) {
                // 세션이 서버 토큰 폐기 오류를 알리지 않지만, 그 밖의 오류가 와도 이 기기는 이미 로그아웃된 상태이므로 사용자 흐름을 막지 않는다 (SEC-002)
                null
            }
            // 이 기기에서 나가면 저장한 비밀번호도 지운다
            biometric?.forget(state.email)
            logoutInFlight = false
            update { copy(isLoading = false, password = "") }
            listener.didLogout(notice)
        }
    }
}
