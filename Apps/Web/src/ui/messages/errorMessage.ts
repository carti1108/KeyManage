// 오류를 사용자 문구로 바꾼다. 문구는 정책이 정한 그대로 쓴다 (SEC-002, SEC-003, SEC-004, SEC-005, COM-002, VLT-004)
import { InvalidEmailError, MasterPasswordChangedError } from '../../domain/auth/authService'
import { BackupDecryptError, NewerBackupVersionError, NotABackupFileError } from '../../domain/backup/backupFile'
import { DeviceStorageError } from '../../domain/device/deviceRecord'
import { InvalidPasswordError } from '../../domain/crypto/keys'
import { NetworkError, ServerError } from '../../domain/server/serverErrors'
import { InvalidVaultDataError } from '../../domain/vault/vaultData'
import { ReadOnlyVaultError, RevisionRollbackError, VaultSyncConflictError, VaultTooLargeError } from '../../domain/vault/vaultSync'
import { ACCOUNT_DELETED_DEVICE_NOT_CLEARED, ACCOUNT_DELETION_OFFLINE, PASSWORD_CHANGE_OFFLINE, SAME_MASTER_PASSWORD } from './accountNotices'
import { NEW_PASSWORD_PROBLEMS } from './authNotices'
import { BACKUP_OPEN_FAILED, NEWER_BACKUP, NOT_A_BACKUP } from './backupNotices'
import { DEVICE_STORAGE_FAILED, PASSWORD_CHANGED, READ_ONLY_NOTICE, ROLLBACK_WARNING } from './vaultNotices'
import {
  AccountDeletedDeviceNotClearedError,
  AccountDeletionOfflineError,
  NewMasterPasswordTooShortError,
  PasswordChangeOfflineError,
  SameMasterPasswordError,
  SessionExpiredError,
  VaultSetupError,
  WrongMasterPasswordError,
} from '../../domain/session/vaultSession'

export interface ErrorMessage {
  readonly title?: string
  readonly body: string
}

const SESSION_EXPIRED = '로그인이 만료되었습니다. 다시 로그인해 주세요.'
// 내부 메시지에는 서버 응답이나 스택이 섞일 수 있어 화면에 그대로 보이지 않는다
const UNKNOWN = '알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'

const SERVER_MESSAGES: Partial<Record<ServerError['code'], ErrorMessage>> = {
  AUTH_FAILED: { title: '로그인 실패', body: '이메일 또는 마스터 비밀번호가 올바르지 않습니다.' },
  UNAUTHORIZED: { body: SESSION_EXPIRED },
  TOO_MANY_ATTEMPTS: { body: '로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.' },
  EMAIL_ALREADY_REGISTERED: { body: '이미 가입된 이메일입니다.' },
  REGISTRATION_DISABLED: { body: '현재 새 계정을 만들 수 없습니다.' },
}

export function describeError(error: unknown): ErrorMessage {
  if (error instanceof ServerError) return SERVER_MESSAGES[error.code] ?? { body: UNKNOWN }
  if (error instanceof SessionExpiredError) return { body: SESSION_EXPIRED }
  if (error instanceof WrongMasterPasswordError) return { body: '마스터 비밀번호가 올바르지 않습니다.' }
  if (error instanceof MasterPasswordChangedError) return { body: PASSWORD_CHANGED }
  if (error instanceof DeviceStorageError) return { body: DEVICE_STORAGE_FAILED }
  if (error instanceof VaultSetupError) return { body: '계정은 만들었지만 볼트를 준비하지 못했습니다. 다시 로그인해 주세요.' }
  if (error instanceof InvalidEmailError) return { body: '올바른 이메일 주소를 입력해 주세요.' }
  if (error instanceof InvalidPasswordError) return { body: '마스터 비밀번호에 쓸 수 없는 문자가 있습니다.' }
  // 복호화는 됐지만 내용 규격이 틀림. 덮어쓰지 않는다 (VLT-001)
  if (error instanceof InvalidVaultDataError) return { title: '볼트를 열 수 없음', body: '볼트 데이터 형식이 올바르지 않습니다.' }
  if (error instanceof VaultSyncConflictError) {
    return { title: '동기화 충돌', body: '다른 기기의 변경 사항과 충돌했습니다. 잠시 후 다시 시도해 주세요.' }
  }
  if (error instanceof VaultTooLargeError) {
    return { title: '저장 실패', body: '볼트 허용 용량(5MB)을 초과했거나 데이터 규격이 올바르지 않습니다.' }
  }
  if (error instanceof ReadOnlyVaultError) return { body: READ_ONLY_NOTICE }
  // 받아들일지 묻는 흐름이 없는 곳(저장 등)에서는 경고만 보인다
  if (error instanceof RevisionRollbackError) return { title: ROLLBACK_WARNING.title, body: ROLLBACK_WARNING.body }
  if (error instanceof BackupDecryptError) return { body: BACKUP_OPEN_FAILED }
  if (error instanceof NotABackupFileError) return { body: NOT_A_BACKUP }
  if (error instanceof NewerBackupVersionError) return { body: NEWER_BACKUP }
  if (error instanceof NewMasterPasswordTooShortError) return { body: NEW_PASSWORD_PROBLEMS.tooShort }
  if (error instanceof SameMasterPasswordError) return { body: SAME_MASTER_PASSWORD }
  if (error instanceof PasswordChangeOfflineError) return { body: PASSWORD_CHANGE_OFFLINE }
  if (error instanceof AccountDeletionOfflineError) return { body: ACCOUNT_DELETION_OFFLINE }
  if (error instanceof AccountDeletedDeviceNotClearedError) return { body: ACCOUNT_DELETED_DEVICE_NOT_CLEARED }
  if (error instanceof NetworkError) return { body: '서버에 연결할 수 없습니다. 네트워크 연결을 확인해 주세요.' }
  return { body: UNKNOWN }
}
