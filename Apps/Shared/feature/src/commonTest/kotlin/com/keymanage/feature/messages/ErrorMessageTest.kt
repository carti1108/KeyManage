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
import com.keymanage.domain.vault.RevisionMismatchException
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.domain.vault.VaultSyncConflictException
import com.keymanage.domain.vault.VaultTooLargeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 원본: Apps/Web/src/ui/messages/errorMessage.test.ts
class ErrorMessageTest {
    private val sessionExpired = "로그인이 만료되었습니다. 다시 로그인해 주세요."
    private val unknown = "알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."

    @Test
    fun errorsBecomePolicyMessages() {
        val cases: List<Pair<Throwable, String>> = listOf(
            ServerError(401, ServerErrorCode.AUTH_FAILED) to "이메일 또는 마스터 비밀번호가 올바르지 않습니다.",
            ServerError(401, ServerErrorCode.UNAUTHORIZED) to sessionExpired,
            SessionExpiredException() to sessionExpired,
            ServerError(429, ServerErrorCode.TOO_MANY_ATTEMPTS) to "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.",
            ServerError(409, ServerErrorCode.EMAIL_ALREADY_REGISTERED) to "이미 가입된 이메일입니다.",
            ServerError(403, ServerErrorCode.REGISTRATION_DISABLED) to "현재 새 계정을 만들 수 없습니다.",
            InvalidEmailException() to "올바른 이메일 주소를 입력해 주세요.",
            InvalidPasswordException("깨진 문자") to "마스터 비밀번호에 쓸 수 없는 문자가 있습니다.",
            NetworkError() to "서버에 연결할 수 없습니다. 네트워크 연결을 확인해 주세요.",
            WrongMasterPasswordException() to "마스터 비밀번호가 올바르지 않습니다.",
            VaultSetupException(NetworkError()) to "계정은 만들었지만 볼트를 준비하지 못했습니다. 다시 로그인해 주세요.",
            MasterPasswordChangedException() to "마스터 비밀번호가 바뀌었습니다. 새 비밀번호로 로그인해 주세요.",
            DeviceStorageException() to "이 기기에 볼트를 저장하지 못했습니다. 새로고침하면 서버에 올리지 못한 변경 사항이 사라질 수 있습니다.",
        )

        for ((error, body) in cases) {
            assertEquals(body, describeErrorMessage(error).body, error.toString())
            assertEquals(body, describeError(error), error.toString())
        }
    }

    @Test
    fun vaultFormatAndSyncConflictUseVlt001Text() {
        assertEquals(
            ErrorMessage(title = "볼트를 열 수 없음", body = "볼트 데이터 형식이 올바르지 않습니다."),
            describeErrorMessage(InvalidVaultDataException("items 없음")),
        )
        assertEquals(
            ErrorMessage(title = "동기화 충돌", body = "다른 기기의 변경 사항과 충돌했습니다. 잠시 후 다시 시도해 주세요."),
            describeErrorMessage(VaultSyncConflictException()),
        )
    }

    @Test
    fun saveFailureUsesVlt001Text() {
        assertEquals(
            ErrorMessage(title = "저장 실패", body = "볼트 허용 용량(5MB)을 초과했거나 데이터 규격이 올바르지 않습니다."),
            describeErrorMessage(VaultTooLargeException(6_000_000)),
        )
        assertEquals(
            "새 버전 앱에서 저장한 볼트입니다. 앱을 업데이트하기 전까지는 읽기만 할 수 있습니다.",
            describeErrorMessage(ReadOnlyVaultException()).body,
        )
    }

    @Test
    fun rollbackDuringSaveShowsWarning() {
        assertEquals("서버 볼트가 이전 상태로 돌아감", describeErrorMessage(RevisionRollbackException(5, 3)).title)
    }

    @Test
    fun loginFailureHasTitle() {
        assertEquals("로그인 실패", describeErrorMessage(ServerError(401, ServerErrorCode.AUTH_FAILED)).title)
    }

    @Test
    fun errorsWithoutTitleHaveNullTitle() {
        assertNull(describeErrorMessage(NetworkError()).title)
    }

    @Test
    fun unexpectedErrorsHideInternalMessage() {
        assertEquals(unknown, describeErrorMessage(Exception("stack trace with secret")).body)
        assertEquals(unknown, describeErrorMessage(ServerError(500, ServerErrorCode.INTERNAL_ERROR)).body)
        assertEquals(unknown, describeErrorMessage(ServerError(0, ServerErrorCode.UNKNOWN)).body)
        // 웹에서 RevisionMismatchError는 UnexpectedResponseError라 공통 문구로 떨어진다
        assertEquals(unknown, describeErrorMessage(RevisionMismatchException(3, 4)).body)
    }

    @Test
    fun backupErrorsUseVlt004Text() {
        assertEquals(
            ErrorMessage(body = "백업을 열 수 없습니다. 비밀번호가 맞는지, 파일이 손상되지 않았는지 확인해 주세요."),
            describeErrorMessage(BackupDecryptException()),
        )
        assertEquals(
            ErrorMessage(body = "KeyManage 백업 파일이 아닙니다."),
            describeErrorMessage(NotABackupFileException("JSON이 아님")),
        )
        assertEquals(
            ErrorMessage(body = "더 새 버전의 앱에서 만든 백업입니다. 앱을 업데이트한 뒤 복원해 주세요."),
            describeErrorMessage(NewerBackupVersionException("formatVersion 2")),
        )
    }

    @Test
    fun passwordChangeAndAccountDeletionErrorsUsePolicyText() {
        assertEquals("마스터 비밀번호는 12자 이상이어야 합니다.", describeError(NewMasterPasswordTooShortException()))
        assertEquals("새 비밀번호가 지금 비밀번호와 같습니다.", describeError(SameMasterPasswordException()))
        assertEquals("인터넷에 연결된 상태에서만 비밀번호를 바꿀 수 있습니다.", describeError(PasswordChangeOfflineException()))
        assertEquals("인터넷에 연결된 상태에서만 계정을 삭제할 수 있습니다.", describeError(AccountDeletionOfflineException()))
        assertEquals(
            "계정은 삭제했지만 이 기기에 남은 볼트 기록을 지우지 못했습니다. 브라우저의 사이트 데이터를 직접 지워 주세요.",
            describeError(AccountDeletedDeviceNotClearedException()),
        )
    }

    @Test
    fun restoredMessageIncludesCount() {
        assertEquals("백업에서 항목 3개를 복원했습니다.", restoredMessage(3))
        assertEquals("백업에서 항목 0개를 복원했습니다.", restoredMessage(0))
    }

    @Test
    fun newPasswordProblemText() {
        assertEquals("마스터 비밀번호는 12자 이상이어야 합니다.", newPasswordProblemMessage(NewMasterPasswordProblem.TOO_SHORT))
        assertEquals("비밀번호 확인이 일치하지 않습니다.", newPasswordProblemMessage(NewMasterPasswordProblem.MISMATCH))
    }

    @Test
    fun onlySessionExpiryAndPasswordChangeSendToSignIn() {
        assertTrue(sendsToSignIn(SessionExpiredException()))
        assertTrue(sendsToSignIn(MasterPasswordChangedException()))
        assertFalse(sendsToSignIn(ServerError(401, ServerErrorCode.UNAUTHORIZED)))
        assertFalse(sendsToSignIn(WrongMasterPasswordException()))
        assertFalse(sendsToSignIn(Exception("기타")))
    }
}
