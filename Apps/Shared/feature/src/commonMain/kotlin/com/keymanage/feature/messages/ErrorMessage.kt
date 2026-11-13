package com.keymanage.feature.messages

import com.keymanage.domain.auth.InvalidEmailException
import com.keymanage.domain.auth.MasterPasswordChangedException
import com.keymanage.domain.auth.NewMasterPasswordProblem
import com.keymanage.domain.backup.BackupDecryptException
import com.keymanage.domain.backup.NewerBackupVersionException
import com.keymanage.domain.backup.NotABackupFileException
import com.keymanage.domain.crypto.InvalidPasswordException
import com.keymanage.domain.device.DeviceStorageException
import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.session.AccountDeletedDeviceNotClearedException
import com.keymanage.domain.session.AccountDeletionOfflineException
import com.keymanage.domain.session.NewMasterPasswordTooShortException
import com.keymanage.domain.session.PasswordChangeOfflineException
import com.keymanage.domain.session.SameMasterPasswordException
import com.keymanage.domain.session.SessionExpiredException
import com.keymanage.domain.session.VaultSetupException
import com.keymanage.domain.session.WrongMasterPasswordException
import com.keymanage.domain.vault.InvalidVaultDataException
import com.keymanage.domain.vault.ReadOnlyVaultException
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.domain.vault.VaultSyncConflictException
import com.keymanage.domain.vault.VaultTooLargeException

// 오류를 사용자 문구로 바꾼다. 문구는 정책이 정한 그대로 쓴다 (SEC-002, SEC-003, SEC-004, SEC-005, COM-002, VLT-004)
// 원본: Apps/Web/src/ui/messages/errorMessage.ts (오류 클래스 대응: Apps/Web/src/adapters/kotlin/kotlinErrors.ts)

/** 화면에 보일 오류. 제목이 없으면 본문만 보인다 */
data class ErrorMessage(val title: String? = null, val body: String)

private const val SESSION_EXPIRED = "로그인이 만료되었습니다. 다시 로그인해 주세요."

// 내부 메시지에는 서버 응답이나 스택이 섞일 수 있어 화면에 그대로 보이지 않는다
private const val UNKNOWN = "알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."

private fun describeServerError(error: ServerError): ErrorMessage = when (error.code) {
    ServerErrorCode.AUTH_FAILED -> ErrorMessage(title = "로그인 실패", body = "이메일 또는 마스터 비밀번호가 올바르지 않습니다.")
    ServerErrorCode.UNAUTHORIZED -> ErrorMessage(body = SESSION_EXPIRED)
    ServerErrorCode.TOO_MANY_ATTEMPTS -> ErrorMessage(body = "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.")
    ServerErrorCode.EMAIL_ALREADY_REGISTERED -> ErrorMessage(body = "이미 가입된 이메일입니다.")
    ServerErrorCode.REGISTRATION_DISABLED -> ErrorMessage(body = "현재 새 계정을 만들 수 없습니다.")
    else -> ErrorMessage(body = UNKNOWN)
}

/** 오류를 제목과 본문으로 바꾼다. 모르는 오류는 내부 메시지를 숨기고 공통 문구를 쓴다 */
fun describeErrorMessage(error: Throwable): ErrorMessage = when (error) {
    is ServerError -> describeServerError(error)
    is SessionExpiredException -> ErrorMessage(body = SESSION_EXPIRED)
    is WrongMasterPasswordException -> ErrorMessage(body = "마스터 비밀번호가 올바르지 않습니다.")
    is MasterPasswordChangedException -> ErrorMessage(body = PASSWORD_CHANGED)
    is DeviceStorageException -> ErrorMessage(body = DEVICE_STORAGE_FAILED)
    is VaultSetupException -> ErrorMessage(body = "계정은 만들었지만 볼트를 준비하지 못했습니다. 다시 로그인해 주세요.")
    is InvalidEmailException -> ErrorMessage(body = "올바른 이메일 주소를 입력해 주세요.")
    is InvalidPasswordException -> ErrorMessage(body = "마스터 비밀번호에 쓸 수 없는 문자가 있습니다.")
    // 복호화는 됐지만 내용 규격이 틀림. 덮어쓰지 않는다 (VLT-001)
    is InvalidVaultDataException -> ErrorMessage(title = "볼트를 열 수 없음", body = "볼트 데이터 형식이 올바르지 않습니다.")
    is VaultSyncConflictException ->
        ErrorMessage(title = "동기화 충돌", body = "다른 기기의 변경 사항과 충돌했습니다. 잠시 후 다시 시도해 주세요.")
    is VaultTooLargeException ->
        ErrorMessage(title = "저장 실패", body = "볼트 허용 용량(5MB)을 초과했거나 데이터 규격이 올바르지 않습니다.")
    is ReadOnlyVaultException -> ErrorMessage(body = READ_ONLY_NOTICE)
    // 받아들일지 묻는 흐름이 없는 곳(저장 등)에서는 경고만 보인다
    is RevisionRollbackException -> ErrorMessage(title = RollbackWarning.TITLE, body = RollbackWarning.BODY)
    is BackupDecryptException -> ErrorMessage(body = BACKUP_OPEN_FAILED)
    is NotABackupFileException -> ErrorMessage(body = NOT_A_BACKUP)
    is NewerBackupVersionException -> ErrorMessage(body = NEWER_BACKUP)
    is NewMasterPasswordTooShortException -> ErrorMessage(body = newPasswordProblemMessage(NewMasterPasswordProblem.TOO_SHORT))
    is SameMasterPasswordException -> ErrorMessage(body = SAME_MASTER_PASSWORD)
    is PasswordChangeOfflineException -> ErrorMessage(body = PASSWORD_CHANGE_OFFLINE)
    is AccountDeletionOfflineException -> ErrorMessage(body = ACCOUNT_DELETION_OFFLINE)
    is AccountDeletedDeviceNotClearedException -> ErrorMessage(body = ACCOUNT_DELETED_DEVICE_NOT_CLEARED)
    is NetworkError -> ErrorMessage(body = "서버에 연결할 수 없습니다. 네트워크 연결을 확인해 주세요.")
    else -> ErrorMessage(body = UNKNOWN)
}

/** 제목 없이 본문만 필요한 곳(알림 줄 등)에서 쓴다. 제목이 필요하면 [describeErrorMessage] */
fun describeError(error: Throwable): String = describeErrorMessage(error).body

/** 로그인 화면으로 돌려보내며 이유를 알려야 하는 오류 (SEC-002, SEC-003) */
fun sendsToSignIn(error: Throwable): Boolean =
    error is SessionExpiredException || error is MasterPasswordChangedException
