package com.keymanage.android.feature.settings.impl

import com.keymanage.android.domain.auth.NewPasswordProblem
import com.keymanage.android.domain.backup.BackupError
import com.keymanage.android.domain.generator.PasswordStrength
import com.keymanage.android.domain.lock.BiometricError
import com.keymanage.android.domain.session.AccountError
import com.keymanage.android.domain.backup.BackupUseCase
import com.keymanage.android.domain.lock.AutoLockOption
import com.keymanage.android.domain.lock.AutoLockSettingStore
import com.keymanage.android.domain.sync.SaveVaultUseCase
import com.keymanage.android.domain.sync.SyncError
import com.keymanage.android.domain.vault.VaultFormatError
import com.keymanage.android.feature.settings.AccountPort
import com.keymanage.android.feature.settings.BackupPort
import com.keymanage.android.feature.settings.BiometricPort
import com.keymanage.android.feature.settings.DocumentIo
import com.keymanage.android.feature.settings.PendingChanges
import com.keymanage.android.feature.settings.SettingsListener
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.BasicComposeRouter
import com.uber.rib.core.BasicInteractor
import com.uber.rib.core.Bundle
import com.uber.rib.core.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

enum class SettingsScreen { MAIN, BACKUP, PASSWORD, DELETE }

/** 비밀번호 변경 화면 상태. 입력값은 화면을 떠나거나 끝나면 지운다. */
data class PasswordChangeState(
    val current: String = "",
    val new: String = "",
    val confirmation: String = "",
    val isWorking: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
) {
    val canSubmit: Boolean get() = !isWorking && current.isNotEmpty() && new.isNotEmpty() && confirmation.isNotEmpty()
    val newScore: Int? get() = if (new.isEmpty()) null else PasswordStrength.score(new)

    override fun toString() = "PasswordChangeState(isWorking=$isWorking, message=$message)"
}

/** 계정 삭제 화면 상태. 확인 문구가 정확히 같아야 삭제할 수 있다(SEC-005 규칙 2). */
data class DeleteAccountState(
    val password: String = "",
    val phrase: String = "",
    val isWorking: Boolean = false,
    val message: String? = null,
) {
    val canDelete: Boolean get() = !isWorking && password.isNotEmpty() && phrase == SettingsMessages.DELETE_PHRASE

    override fun toString() = "DeleteAccountState(isWorking=$isWorking, message=$message)"
}

/** 백업 화면 상태. 복원 비밀번호는 복원이 끝나거나 화면을 떠나면 지운다. */
data class BackupState(
    val fileName: String = "",
    val restoreUri: String? = null,
    val restorePassword: String = "",
    val isWorking: Boolean = false,
    val isConfirmingRestore: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
) {
    val canRestore: Boolean get() = !isWorking && restoreUri != null && restorePassword.isNotEmpty()

    override fun toString() = "BackupState(isWorking=$isWorking, message=$message)"
}

/** 생체 인증 스위치 상태. 기기에서 쓸 수 없으면 보이지 않는다. */
data class BiometricState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val isWorking: Boolean = false,
    val message: String? = null,
)

data class SettingsViewState(
    val email: String,
    val autoLock: AutoLockOption = AutoLockOption.DEFAULT,
    val isChoosingAutoLock: Boolean = false,
    val isConfirmingLogout: Boolean = false,
    val screen: SettingsScreen = SettingsScreen.MAIN,
    val backup: BackupState = BackupState(),
    val password: PasswordChangeState = PasswordChangeState(),
    val delete: DeleteAccountState = DeleteAccountState(),
    val biometric: BiometricState = BiometricState(),
)

sealed interface SettingsViewAction {
    data object OpenAutoLock : SettingsViewAction
    data class ToggleBiometric(val enabled: Boolean) : SettingsViewAction
    data class ChooseAutoLock(val option: AutoLockOption) : SettingsViewAction
    data object DismissDialog : SettingsViewAction
    data object Logout : SettingsViewAction
    data object ConfirmLogout : SettingsViewAction
    data object OpenBackup : SettingsViewAction
    data object Back : SettingsViewAction

    /** 사용자가 내보낼 파일 위치를 골랐다. */
    data class ExportTo(val uri: String) : SettingsViewAction
    data class RestoreFilePicked(val uri: String) : SettingsViewAction
    data class RestorePasswordChanged(val value: String) : SettingsViewAction
    data object RequestRestore : SettingsViewAction
    data object ConfirmRestore : SettingsViewAction
    data object OpenPasswordChange : SettingsViewAction
    data class PasswordFieldsChanged(val current: String, val new: String, val confirmation: String) : SettingsViewAction
    data object SubmitPasswordChange : SettingsViewAction
    data object OpenDeleteAccount : SettingsViewAction
    data class DeleteFieldsChanged(val password: String, val phrase: String) : SettingsViewAction
    data object SubmitDeleteAccount : SettingsViewAction
}

/** VLT-001·VLT-002·VLT-004·COM-002 사용자 문구. */
object SettingsMessages {
    const val PENDING_LOGOUT_TITLE = "동기화되지 않은 변경 사항"
    const val PENDING_LOGOUT_BODY = "로그아웃하면 서버에 올리지 못한 변경 사항이 사라집니다."
    const val EXPORT_TITLE = "백업 내보내기"
    const val EXPORT_BODY = "이 파일은 내보낸 시점의 마스터 비밀번호로만 열 수 있습니다. 비밀번호를 바꾼 뒤에도 이 파일은 예전 비밀번호로 엽니다."
    const val RESTORE_TITLE = "백업에서 복원"
    const val RESTORE_BODY = "백업에 있던 항목은 백업 시점 내용으로 돌아갑니다. 백업 뒤에 고치거나 지운 내용은 되돌아가고, 백업 뒤에 새로 만든 항목은 그대로 남습니다."
    const val CANNOT_OPEN = "백업을 열 수 없습니다. 비밀번호가 맞는지, 파일이 손상되지 않았는지 확인해 주세요."
    const val NOT_BACKUP = "KeyManage 백업 파일이 아닙니다."
    const val NEWER_VERSION = "더 새 버전의 앱에서 만든 백업입니다. 앱을 업데이트한 뒤 복원해 주세요."
    const val VAULT_FORMAT = "볼트 데이터 형식이 올바르지 않습니다."
    const val TOO_LARGE = "볼트 허용 용량(5MB)을 초과했거나 데이터 규격이 올바르지 않습니다."
    const val READ_ONLY = "새 버전 앱에서 저장한 볼트입니다. 앱을 업데이트하기 전까지는 읽기만 할 수 있습니다."
    const val EXPORTED = "백업 파일을 저장했습니다."
    const val UNKNOWN = "알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."

    const val BIOMETRIC_UNAVAILABLE = "이 기기에서는 생체 인증을 쓸 수 없습니다. 생체 정보와 화면 잠금을 등록해 주세요."
    const val PASSWORD_TITLE = "마스터 비밀번호 변경"
    const val PASSWORD_BODY = "비밀번호를 바꾸면 다른 기기는 모두 로그아웃되고, 새 비밀번호로 다시 로그인해야 합니다. 다른 기기에서 아직 올리지 못한 변경 사항은 사라집니다. 예전에 내보낸 백업은 예전 비밀번호로 엽니다."
    const val WRONG_PASSWORD = "마스터 비밀번호가 올바르지 않습니다."
    const val SAME_PASSWORD = "새 비밀번호가 지금 비밀번호와 같습니다."
    const val PASSWORD_OFFLINE = "인터넷에 연결된 상태에서만 비밀번호를 바꿀 수 있습니다."
    const val CONFLICT = "다른 기기의 변경 사항과 충돌했습니다. 잠시 후 다시 시도해 주세요."
    const val PASSWORD_CHANGED = "마스터 비밀번호를 바꿨습니다."
    const val TOO_SHORT = "마스터 비밀번호는 12자 이상이어야 합니다."
    const val MISMATCH = "비밀번호 확인이 일치하지 않습니다."
    const val DELETE_TITLE = "계정 삭제"
    const val DELETE_BODY = "계정과 볼트가 서버에서 바로 지워지고 되돌릴 수 없습니다. 다른 기기도 모두 로그아웃되고, 아직 올리지 못한 변경 사항은 사라집니다. 필요하면 먼저 백업을 내보내 주세요."
    const val DELETE_PHRASE = "계정 삭제"
    const val DELETE_PHRASE_LABEL = "확인을 위해 '계정 삭제'를 입력해 주세요."
    const val DELETE_OFFLINE = "인터넷에 연결된 상태에서만 계정을 삭제할 수 있습니다."
    const val ACCOUNT_DELETED = "계정을 삭제했습니다."

    fun restored(count: Int) = "백업에서 항목 ${count}개를 복원했습니다."
}

/** 자동 잠금 선택, 로그아웃, 백업 내보내기·복원. */
class SettingsInteractor(
    presenter: SettingsPresenter,
    private val store: ViewStateStore<SettingsViewState>,
    private val actions: ViewActionStream<SettingsViewAction>,
    private val autoLockStore: AutoLockSettingStore,
    private val pendingChanges: PendingChanges,
    private val listener: SettingsListener,
    private val backup: BackupPort? = null,
    private val documents: DocumentIo? = null,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Instant = Instant::now,
    private val today: () -> LocalDate = LocalDate::now,
    private val account: AccountPort? = null,
    private val biometric: BiometricPort? = null,
) : BasicInteractor<SettingsPresenter, BasicComposeRouter<SettingsInteractor>>(presenter) {
    init {
        store.update {
            it.copy(
                autoLock = autoLockStore.read(),
                biometric = BiometricState(isAvailable = biometric?.isAvailable == true, isEnabled = biometric?.isEnabled == true),
            )
        }
    }

    override fun didBecomeActive(savedInstanceState: Bundle?) {
        super.didBecomeActive(savedInstanceState)
        actions.actions.onEach(::handle).launchIn(coroutineScope)
    }

    fun handle(action: SettingsViewAction) {
        when (action) {
            SettingsViewAction.OpenAutoLock -> store.update { it.copy(isChoosingAutoLock = true) }
            is SettingsViewAction.ToggleBiometric -> coroutineScope.launch { toggleBiometric(action.enabled) }
            is SettingsViewAction.ChooseAutoLock -> {
                autoLockStore.write(action.option)
                store.update { it.copy(autoLock = action.option, isChoosingAutoLock = false) }
                listener.onAutoLockChanged(action.option)
            }
            SettingsViewAction.DismissDialog -> store.update {
                it.copy(isChoosingAutoLock = false, isConfirmingLogout = false, backup = it.backup.copy(isConfirmingRestore = false))
            }
            SettingsViewAction.Logout ->
                if (pendingChanges.exist()) store.update { it.copy(isConfirmingLogout = true) } else listener.onLogoutRequested()
            SettingsViewAction.ConfirmLogout -> {
                store.update { it.copy(isConfirmingLogout = false) }
                listener.onLogoutRequested()
            }
            SettingsViewAction.OpenBackup -> store.update {
                it.copy(screen = SettingsScreen.BACKUP, backup = BackupState(fileName = BackupUseCase.fileName(today())))
            }
            SettingsViewAction.Back -> store.update {
                it.copy(screen = SettingsScreen.MAIN, backup = BackupState(), password = PasswordChangeState(), delete = DeleteAccountState())
            }
            SettingsViewAction.OpenPasswordChange -> store.update { it.copy(screen = SettingsScreen.PASSWORD, password = PasswordChangeState()) }
            is SettingsViewAction.PasswordFieldsChanged -> store.update {
                it.copy(password = it.password.copy(current = action.current, new = action.new, confirmation = action.confirmation, message = null))
            }
            SettingsViewAction.SubmitPasswordChange -> coroutineScope.launch { changePassword() }
            SettingsViewAction.OpenDeleteAccount -> store.update { it.copy(screen = SettingsScreen.DELETE, delete = DeleteAccountState()) }
            is SettingsViewAction.DeleteFieldsChanged -> store.update {
                it.copy(delete = it.delete.copy(password = action.password, phrase = action.phrase, message = null))
            }
            SettingsViewAction.SubmitDeleteAccount -> coroutineScope.launch { deleteAccount() }
            is SettingsViewAction.ExportTo -> coroutineScope.launch { export(action.uri) }
            is SettingsViewAction.RestoreFilePicked -> updateBackup { it.copy(restoreUri = action.uri, message = null) }
            is SettingsViewAction.RestorePasswordChanged -> updateBackup { it.copy(restorePassword = action.value, message = null) }
            SettingsViewAction.RequestRestore -> if (store.current.backup.canRestore) updateBackup { it.copy(isConfirmingRestore = true) }
            SettingsViewAction.ConfirmRestore -> coroutineScope.launch { restore() }
        }
    }

    /** 고른 위치에 백업 파일을 쓴다. 테스트는 직접 부른다. */
    suspend fun export(uri: String) {
        val backup = backup ?: return
        val documents = documents ?: return
        if (store.current.backup.isWorking) return
        updateBackup { it.copy(isWorking = true, message = null) }
        val failure = runWork { documents.write(uri, backup.export(clock())) }
        updateBackup {
            it.copy(isWorking = false, message = if (failure == null) SettingsMessages.EXPORTED else SettingsMessages.UNKNOWN, isError = failure != null)
        }
    }

    /** 확인 뒤 고른 파일에서 복원한다. 복원 중에는 다시 실행하지 않는다. 테스트는 직접 부른다. */
    suspend fun restore() {
        val backup = backup ?: return
        val documents = documents ?: return
        val state = store.current.backup
        val uri = state.restoreUri ?: return
        if (state.isWorking || state.restorePassword.isEmpty()) return
        updateBackup { it.copy(isWorking = true, isConfirmingRestore = false, message = null) }
        var restored = 0
        val failure = runWork {
            val bytes = documents.read(uri, MAX_FILE_BYTES + 1)
            restored = backup.restore(bytes, state.restorePassword, clock()).restored
        }
        updateBackup {
            if (failure == null) {
                it.copy(isWorking = false, restorePassword = "", restoreUri = null, message = SettingsMessages.restored(restored), isError = false)
            } else {
                it.copy(isWorking = false, message = messageOf(failure), isError = true)
            }
        }
    }

    /** 생체 해제 켜기·끄기. 켤 때는 생체 인증 창을 거친다. 취소하면 꺼진 채로 둔다. 테스트는 직접 부른다. */
    suspend fun toggleBiometric(enabled: Boolean) {
        val biometric = biometric ?: return
        if (store.current.biometric.isWorking) return
        if (!enabled) {
            biometric.disable()
            store.update { it.copy(biometric = it.biometric.copy(isEnabled = false, message = null)) }
            return
        }
        store.update { it.copy(biometric = it.biometric.copy(isWorking = true, message = null)) }
        val message = try {
            biometric.enable()
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: BiometricError.Cancelled) {
            null
        } catch (error: BiometricError) {
            SettingsMessages.BIOMETRIC_UNAVAILABLE
        } catch (error: Exception) {
            SettingsMessages.UNKNOWN
        }
        store.update { it.copy(biometric = it.biometric.copy(isWorking = false, isEnabled = biometric.isEnabled, message = message)) }
    }

    /** 마스터 비밀번호 변경(SEC-004). 테스트는 직접 부른다. */
    suspend fun changePassword() {
        val account = account ?: return
        val state = store.current.password
        if (!state.canSubmit) return
        store.update { it.copy(password = state.copy(isWorking = true, message = null)) }
        val failure = runWork { account.changePassword(state.current, state.new, state.confirmation) }
        if (failure is AccountError.LoginRequired) {
            store.update { it.copy(password = PasswordChangeState()) }
            listener.onSessionExpired()
            return
        }
        store.update {
            it.copy(
                password = if (failure == null) {
                    PasswordChangeState(message = SettingsMessages.PASSWORD_CHANGED)
                } else {
                    it.password.copy(isWorking = false, message = accountMessageOf(failure, SettingsMessages.PASSWORD_OFFLINE), isError = true)
                },
            )
        }
    }

    /** 계정 삭제(SEC-005). 성공하면 부모가 로그인 화면으로 보낸다. 테스트는 직접 부른다. */
    suspend fun deleteAccount() {
        val account = account ?: return
        val state = store.current.delete
        if (!state.canDelete) return
        store.update { it.copy(delete = state.copy(isWorking = true, message = null)) }
        val failure = runWork { account.deleteAccount(state.password) }
        when (failure) {
            null -> {
                store.update { it.copy(delete = DeleteAccountState()) }
                listener.onAccountDeleted()
            }
            is AccountError.LoginRequired -> {
                store.update { it.copy(delete = DeleteAccountState()) }
                listener.onSessionExpired()
            }
            else -> store.update {
                it.copy(delete = it.delete.copy(isWorking = false, message = accountMessageOf(failure, SettingsMessages.DELETE_OFFLINE)))
            }
        }
    }

    private suspend fun runWork(block: () -> Unit): Exception? = try {
        withContext(workDispatcher) { block() }
        null
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        error
    }

    private fun updateBackup(transform: (BackupState) -> BackupState) = store.update { it.copy(backup = transform(it.backup)) }

    private companion object {
        /** 파일 전체 상한: 볼트 5MB + 64KB(VLT-004 값 표). 넘으면 코덱이 거절하도록 1바이트 더 읽는다. */
        const val MAX_FILE_BYTES = SaveVaultUseCase.MAX_PAYLOAD_BYTES + 65_536

        fun accountMessageOf(error: Exception, offline: String): String = when (error) {
            is AccountError.WrongPassword -> SettingsMessages.WRONG_PASSWORD
            is AccountError.SamePassword -> SettingsMessages.SAME_PASSWORD
            is AccountError.WeakNewPassword ->
                if (error.problem == NewPasswordProblem.TOO_SHORT) SettingsMessages.TOO_SHORT else SettingsMessages.MISMATCH
            is AccountError.Offline -> offline
            is AccountError.Conflict -> SettingsMessages.CONFLICT
            is SyncError.ReadOnlyVault -> SettingsMessages.READ_ONLY
            is SyncError.VaultTooLarge -> SettingsMessages.TOO_LARGE
            else -> SettingsMessages.UNKNOWN
        }

        fun messageOf(error: Exception): String = when (error) {
            is BackupError.CannotOpen -> SettingsMessages.CANNOT_OPEN
            is BackupError.NotBackupFile -> SettingsMessages.NOT_BACKUP
            is BackupError.NewerVersion -> SettingsMessages.NEWER_VERSION
            is VaultFormatError -> SettingsMessages.VAULT_FORMAT
            is SyncError.VaultTooLarge -> SettingsMessages.TOO_LARGE
            is SyncError.ReadOnlyVault -> SettingsMessages.READ_ONLY
            else -> SettingsMessages.UNKNOWN
        }
    }
}
