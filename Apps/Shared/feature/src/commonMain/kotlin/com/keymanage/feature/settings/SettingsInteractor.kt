package com.keymanage.feature.settings

import com.carti.ribs.PresentableInteractor
import com.carti.ribs.checkMainThread
import com.keymanage.domain.auth.checkNewMasterPassword
import com.keymanage.domain.backup.VaultBackup
import com.keymanage.domain.backup.readBackupFile
import com.keymanage.domain.lock.isAutoLockOption
import com.keymanage.domain.session.AccountDeletedDeviceNotClearedException
import com.keymanage.domain.session.SessionState
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.messages.ACCOUNT_DELETED
import com.keymanage.feature.messages.ACCOUNT_DELETION_OFFLINE
import com.keymanage.feature.messages.PASSWORD_CHANGE_DONE
import com.keymanage.feature.messages.PASSWORD_CHANGE_OFFLINE
import com.keymanage.feature.messages.SAME_MASTER_PASSWORD
import com.keymanage.feature.messages.describeError
import com.keymanage.feature.messages.newPasswordProblemMessage
import com.keymanage.feature.messages.restoredMessage
import com.keymanage.feature.messages.sendsToSignIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

// 설정 RIB. 계정 정보, 자동 잠금 시간, 백업 내보내기·복원, 마스터 비밀번호 변경, 계정 삭제 (SEC-003, SEC-004, SEC-005, VLT-004)
// 원본: Apps/Web/src/ui/screens/SettingsView.tsx, vault/AutoLockSelect.tsx, backup/BackupActions.tsx, account/ChangePasswordAction.tsx, account/DeleteAccountAction.tsx
// 모달·입력·진행·오류는 모두 State에 둔다. 한 번에 모달 하나만 열린다

class SettingsInteractor(
    presenter: SettingsPresentable,
    private val session: SessionPort,
    private val backupFiles: BackupFilesPort,
    private val environment: AppEnvironment,
    private val listener: SettingsListener,
) : PresentableInteractor<SettingsPresentable>(presenter), SettingsPresentableListener {
    private var state = SettingsViewState()
    private var unsubscribes: List<() -> Unit> = emptyList()

    // 형식을 확인한 복원 대상. 비밀번호 모달이 열려 있는 동안만 가진다
    private var pendingBackup: VaultBackup? = null

    // 파일 선택 차례. 새 선택이나 다른 동작을 시작하면 올려, 늦게 끝난 이전 선택 결과를 버린다
    private var pickTurn = 0

    override fun didBecomeActive() {
        presenter.listener = this
        unsubscribes = listOf(
            environment.subscribeNetwork { refreshInfo() },
            session.onSyncChange { refreshInfo() },
            session.onRemoteChange { refreshInfo() },
            environment.autoLockSetting.subscribe { timeout -> update { copy(autoLockTimeout = timeout) } },
        )
        refreshInfo()
    }

    override fun willResignActive() {
        presenter.listener = null
        unsubscribes.forEach { it() }
        unsubscribes = emptyList()
        pendingBackup = null
        clearPlaintext()
    }

    /** 내려가기 전에 입력해 둔 비밀번호를 비우고 그려, 뷰가 마지막으로 받은 상태에 남지 않게 한다 */
    private fun clearPlaintext() {
        update {
            copy(
                restorePassword = "",
                currentPassword = "",
                newPassword = "",
                passwordConfirmation = "",
                deletionPassword = "",
                deletionPhrase = "",
            )
        }
    }

    override fun handle(action: SettingsViewAction) {
        // 비활성 뒤 늦게 온 입력은 무시한다(옛 Presenter가 onAction을 비워 막던 것)
        if (!isActive) return
        when (action) {
            is SettingsViewAction.SelectAutoLock -> selectAutoLock(action.timeout)
            SettingsViewAction.RequestLock -> listener.settingsDidRequestLock()
            SettingsViewAction.RequestLogout -> listener.settingsDidRequestLogout()
            SettingsViewAction.StartExport -> startExport()
            SettingsViewAction.ConfirmExport -> confirmExport()
            SettingsViewAction.StartRestore -> startRestore()
            is SettingsViewAction.EditRestorePassword -> edit { copy(restorePassword = action.value) }
            SettingsViewAction.SubmitRestore -> submitRestore()
            SettingsViewAction.StartChangePassword -> startChangePassword()
            is SettingsViewAction.EditCurrentPassword -> edit { copy(currentPassword = action.value) }
            is SettingsViewAction.EditNewPassword -> edit { copy(newPassword = action.value) }
            is SettingsViewAction.EditPasswordConfirmation -> edit { copy(passwordConfirmation = action.value) }
            SettingsViewAction.SubmitChangePassword -> submitChangePassword()
            SettingsViewAction.StartDeleteAccount -> startDeleteAccount()
            is SettingsViewAction.EditDeletionPassword -> edit { copy(deletionPassword = action.value) }
            is SettingsViewAction.EditDeletionPhrase -> edit { copy(deletionPhrase = action.value) }
            SettingsViewAction.SubmitDeleteAccount -> submitDeleteAccount()
            SettingsViewAction.CancelModal -> cancelModal()
            SettingsViewAction.DismissMessage -> update { copy(error = null, notice = null) }
        }
    }

    // --- 상태 ---

    private fun update(change: SettingsViewState.() -> SettingsViewState) {
        state = state.change()
        if (!isActive) return
        checkMainThread("SettingsPresentable.render")
        presenter.render(state)
    }

    /** 진행 중에는 입력 칸이 막혀 있다 */
    private fun edit(change: SettingsViewState.() -> SettingsViewState) {
        if (!state.inProgress) update(change)
    }

    /** 세션·네트워크·설정에서 읽는 값을 다시 읽는다. 모달·입력은 건드리지 않는다 */
    private fun refreshInfo() {
        val current = session.state()
        val unlocked = current as? SessionState.Unlocked
        val networkOffline = environment.isNetworkOffline()
        val signedIn = unlocked?.signedIn ?: false
        update {
            copy(
                email = when (current) {
                    is SessionState.Unlocked -> current.email
                    is SessionState.Locked -> current.email
                    SessionState.SignedOut -> ""
                },
                serverAddress = environment.serverAddress,
                syncOffline = networkOffline || (unlocked?.pendingSync ?: false) || !signedIn,
                readOnly = unlocked?.vault?.readOnly ?: false,
                accountOffline = networkOffline || !signedIn,
                autoLockTimeout = environment.autoLockSetting.read(),
            )
        }
    }

    /** 모달을 닫고 입력을 비운다 */
    private fun SettingsViewState.closed(): SettingsViewState = copy(
        modal = SettingsModal.NONE,
        restorePassword = "",
        currentPassword = "",
        newPassword = "",
        passwordConfirmation = "",
        deletionPassword = "",
        deletionPhrase = "",
    )

    private fun cancelModal() {
        if (state.inProgress) return
        pendingBackup = null
        update { closed().copy(error = null) }
    }

    /**
     * 한 번에 하나만 한다. 이미 진행 중이면 무시한다. 실패는 오류 문구로 바꿔 State에 둔다
     * @return 끝까지 했는지
     */
    private suspend fun runExclusive(work: suspend () -> Unit): Boolean {
        if (state.inProgress) return false
        update { copy(inProgress = true, error = null) }
        try {
            work()
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            update { copy(error = describeError(error)) }
            return false
        } finally {
            update { copy(inProgress = false) }
        }
    }

    /** 복원·비밀번호 변경·계정 삭제를 시도한 뒤 부모에게 알린다. 실패해도 세션이 바뀌었을 수 있다 */
    private fun finishAttempt(finished: Boolean, endNotice: String?) {
        if (finished && endNotice != null) {
            listener.settingsDidEndSession(endNotice)
            return
        }
        refreshInfo()
        listener.settingsDidChangeSession()
    }

    // --- 자동 잠금 (SEC-003) ---

    private fun selectAutoLock(timeout: Duration) {
        if (!isAutoLockOption(timeout)) return
        environment.autoLockSetting.write(timeout)
        update { copy(autoLockTimeout = timeout) }
    }

    // --- 백업 내보내기 (VLT-004) ---

    private fun startExport() {
        if (state.inProgress) return
        pickTurn += 1
        pendingBackup = null
        update { closed().copy(modal = SettingsModal.EXPORT_CONFIRM, error = null, notice = null) }
    }

    private fun confirmExport() {
        if (state.modal != SettingsModal.EXPORT_CONFIRM || state.inProgress) return
        coroutineScope.launch {
            runExclusive {
                backupFiles.save(session.exportBackup(environment.clock.now(), environment.calendar))
                update { closed() }
            }
        }
    }

    // --- 백업 복원 (VLT-004) ---

    /**
     * 선택 창이 열려 있는 동안에는 막지 않는다. 취소를 알리지 않는 환경에서 끝나지 않을 수 있으므로,
     * 다시 눌러 새로 고를 수 있게 하고 가장 최근 선택의 결과만 쓴다. 파일을 받은 뒤부터(형식 검사) 진행 중으로 막는다
     */
    private fun startRestore() {
        if (!state.canRestoreBackup) return
        pickTurn += 1
        val turn = pickTurn
        pendingBackup = null
        update { closed().copy(error = null, notice = null) }
        coroutineScope.launch {
            val picked = try {
                backupFiles.pick()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (turn == pickTurn) update { copy(error = describeError(error)) }
                return@launch
            }
            if (picked == null || turn != pickTurn) return@launch
            runExclusive {
                val backup = readBackupFile(picked)
                if (turn == pickTurn) {
                    pendingBackup = backup
                    update { copy(modal = SettingsModal.RESTORE_PASSWORD) }
                }
            }
        }
    }

    private fun submitRestore() {
        val backup = pendingBackup
        if (state.modal != SettingsModal.RESTORE_PASSWORD || backup == null || !state.canSubmitRestore) return
        // 입력값은 요청에만 담고 화면 상태에서는 바로 버린다
        val entered = state.restorePassword
        update { copy(restorePassword = "") }
        coroutineScope.launch {
            val finished = runExclusive {
                val count = session.restoreBackup(backup, entered, environment.clock.now())
                pendingBackup = null
                update { closed().copy(notice = restoredMessage(count), noticeKind = SettingsNoticeKind.INFO) }
            }
            if (finished) listener.settingsDidRestoreBackup()
            finishAttempt(finished, endNotice = null)
        }
    }

    // --- 마스터 비밀번호 변경 (SEC-004) ---

    private fun startChangePassword() {
        if (state.inProgress || state.readOnly) return
        update { copy(error = null) }
        if (state.accountOffline) {
            update { copy(notice = PASSWORD_CHANGE_OFFLINE, noticeKind = SettingsNoticeKind.ERROR) }
            return
        }
        pickTurn += 1
        update { closed().copy(modal = SettingsModal.CHANGE_PASSWORD, notice = null) }
    }

    private fun submitChangePassword() {
        if (state.modal != SettingsModal.CHANGE_PASSWORD || !state.canSubmitChangePassword) return
        val current = state.currentPassword
        val next = state.newPassword
        val problem = checkNewMasterPassword(next, state.passwordConfirmation)
        if (problem != null) {
            update { copy(error = newPasswordProblemMessage(problem)) }
            return
        }
        if (next == current) {
            update { copy(error = SAME_MASTER_PASSWORD) }
            return
        }
        // 현재 비밀번호는 요청에만 담고 화면 상태에서는 바로 버린다. 실패하면 다시 입력받는다
        update { copy(currentPassword = "") }
        coroutineScope.launch {
            var endNotice: String? = null
            val finished = runExclusive {
                try {
                    session.changeMasterPassword(current, next)
                } catch (error: Exception) {
                    // 세션이 만료됐으면 로그인 화면으로 가며 이유를 알린다 (SEC-004 규칙 9)
                    if (!sendsToSignIn(error)) throw error
                    endNotice = describeError(error)
                    update { closed() }
                    return@runExclusive
                }
                update { closed().copy(notice = PASSWORD_CHANGE_DONE, noticeKind = SettingsNoticeKind.INFO) }
            }
            if (finished && endNotice == null) listener.settingsDidChangeMasterPassword()
            finishAttempt(finished, endNotice)
        }
    }

    // --- 계정 삭제 (SEC-005) ---

    private fun startDeleteAccount() {
        if (state.inProgress) return
        update { copy(error = null) }
        if (state.accountOffline) {
            update { copy(notice = ACCOUNT_DELETION_OFFLINE, noticeKind = SettingsNoticeKind.ERROR) }
            return
        }
        pickTurn += 1
        update { closed().copy(modal = SettingsModal.DELETE_ACCOUNT, notice = null) }
    }

    private fun submitDeleteAccount() {
        if (state.modal != SettingsModal.DELETE_ACCOUNT || !state.canSubmitDeletion) return
        // 비밀번호는 요청에만 담고 화면 상태에서는 바로 버린다. 실패하면 다시 입력받는다
        val password = state.deletionPassword
        update { copy(deletionPassword = "") }
        coroutineScope.launch {
            var endNotice: String? = null
            val finished = runExclusive {
                endNotice = try {
                    session.deleteAccount(password)
                    ACCOUNT_DELETED
                } catch (error: Exception) {
                    // 지웠지만 기기 기록이 남았거나 세션이 만료됐으면 로그인 화면에 그 이유를 보인다. 그 밖의 실패는 모달이 보인다
                    if (error !is AccountDeletedDeviceNotClearedException && !sendsToSignIn(error)) throw error
                    describeError(error)
                }
                update { closed() }
            }
            finishAttempt(finished, endNotice)
        }
    }
}
