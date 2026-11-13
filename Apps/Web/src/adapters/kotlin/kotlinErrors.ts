// Kotlin 예외(errorInfo의 kind)를 화면이 instanceof로 가르는 웹 오류 클래스로 바꾼다
// kind는 Kotlin 예외 클래스의 simpleName이다. 모르는 kind는 message를 보존한 Error로 감싸고(cause=원본), Kotlin 예외가 아닌 값은 원래 값 그대로 둔다
import { InvalidEmailError, MasterPasswordChangedError } from '../../domain/auth/authService'
import { BackupDecryptError, NewerBackupVersionError, NotABackupFileError } from '../../domain/backup/backupFile'
import { InvalidBase64Error } from '../../domain/crypto/base64'
import { InvalidKdfParamsError, InvalidPasswordError } from '../../domain/crypto/keys'
import { UnsupportedPayloadError, VaultDecryptError } from '../../domain/crypto/vaultCipher'
import { DeviceStorageError } from '../../domain/device/deviceRecord'
import { NetworkError, SERVER_ERROR_CODES, ServerError, UnexpectedResponseError, type ServerErrorCode } from '../../domain/server/serverErrors'
import {
  AccountDeletedDeviceNotClearedError,
  AccountDeletionOfflineError,
  NewMasterPasswordTooShortError,
  PasswordChangeOfflineError,
  SameMasterPasswordError,
  SessionExpiredError,
  SessionStateError,
  VaultSetupError,
  WrongMasterPasswordError,
} from '../../domain/session/sessionTypes'
import { InvalidVaultDataError } from '../../domain/vault/vaultData'
import { ReadOnlyVaultError, RevisionRollbackError, VaultSyncConflictError, VaultTooLargeError } from '../../domain/vault/vaultSync'
import { InvalidBaseUrlError } from '../http/baseUrl'
import type { JsErrorInfo, KotlinModule } from './kotlinTypes'

type Detail = Readonly<Record<string, unknown>>

type Builder = (info: JsErrorInfo, original: unknown) => Error

// 메시지가 고정된 웹 오류는 Kotlin 쪽 메시지를 버리고 웹 클래스의 메시지를 쓴다. 메시지를 받는 오류만 옮긴다
const message = (info: JsErrorInfo, fallback: string): string => info.message ?? fallback

function detailOf(info: JsErrorInfo): Detail {
  if (info.detailJson == null) return {}
  try {
    const parsed: unknown = JSON.parse(info.detailJson)
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed) ? (parsed as Detail) : {}
  } catch {
    return {}
  }
}

// 값이 없어도 오류 종류는 지켜야 한다(되돌림 확인 흐름이 instanceof로 갈린다). 화면은 숫자를 쓰지 않으므로 없으면 NaN으로 둔다
function numberOf(detail: Detail, name: string): number {
  const value = detail[name]
  return typeof value === 'number' ? value : Number.NaN
}

function serverCodeOf(code: string | null): ServerErrorCode {
  return (SERVER_ERROR_CODES as readonly string[]).includes(code ?? '') ? (code as ServerErrorCode) : 'UNKNOWN'
}

const BUILDERS: Readonly<Record<string, Builder>> = {
  // 세션
  WrongMasterPasswordException: () => new WrongMasterPasswordError(),
  SessionStateException: (info) => new SessionStateError(message(info, '지금 상태에서는 할 수 없는 동작')),
  SessionExpiredException: () => new SessionExpiredError(),
  VaultSetupException: (_info, original) => new VaultSetupError(original),
  MasterPasswordChangedException: () => new MasterPasswordChangedError(),
  InvalidEmailException: () => new InvalidEmailError(),
  AccountDeletionOfflineException: () => new AccountDeletionOfflineError(),
  AccountDeletedDeviceNotClearedException: () => new AccountDeletedDeviceNotClearedError(),
  NewMasterPasswordTooShortException: () => new NewMasterPasswordTooShortError(),
  SameMasterPasswordException: () => new SameMasterPasswordError(),
  PasswordChangeOfflineException: () => new PasswordChangeOfflineError(),
  // 기기 저장소
  DeviceStorageException: (_info, original) => new DeviceStorageError(original),
  // 볼트·동기화
  VaultSyncConflictException: () => new VaultSyncConflictError(),
  ReadOnlyVaultException: () => new ReadOnlyVaultError(),
  VaultTooLargeException: (info) => new VaultTooLargeError(numberOf(detailOf(info), 'sizeInBytes')),
  RevisionRollbackException: (info) => {
    const detail = detailOf(info)
    return new RevisionRollbackError(numberOf(detail, 'lastSeenRevision'), numberOf(detail, 'serverRevision'))
  },
  InvalidVaultDataException: (info) => new InvalidVaultDataError(message(info, '볼트 데이터 형식이 올바르지 않음')),
  // 백업
  NotABackupFileException: (info) => new NotABackupFileError(message(info, '백업 파일이 아님')),
  NewerBackupVersionException: (info) => new NewerBackupVersionError(message(info, '모르는 백업 버전')),
  BackupDecryptException: () => new BackupDecryptError(),
  // 암호
  InvalidPasswordException: (info) => new InvalidPasswordError(message(info, '쓸 수 없는 문자가 있는 비밀번호')),
  InvalidKdfParamsException: (info) => new InvalidKdfParamsError(message(info, 'KDF 값이 규격에 맞지 않음')),
  VaultDecryptException: () => new VaultDecryptError(),
  UnsupportedPayloadException: (info) => new UnsupportedPayloadError(message(info, '지원하지 않는 볼트 형식')),
  InvalidBase64Exception: () => new InvalidBase64Error(),
  // 서버 통신
  ServerError: (info) => new ServerError(info.status ?? 0, serverCodeOf(info.code ?? null), info.retryAfterSeconds ?? undefined),
  NetworkError: (info) => (info.message == null ? new NetworkError() : new NetworkError(info.message)),
  UnexpectedResponseError: (info) => new UnexpectedResponseError(message(info, '서버 응답 형식이 올바르지 않음')),
  // 웹의 RevisionMismatchError는 기대·실제 revision을 필드로 갖지만 Kotlin 예외는 값을 내놓지 않으므로 부모 클래스로 옮긴다
  RevisionMismatchException: (info) => new UnexpectedResponseError(message(info, '저장 응답 revision이 기대와 다름')),
  // 설정
  InvalidBaseUrlException: () => new InvalidBaseUrlError(),
}

export interface ErrorTranslator {
  /** Promise reject·throw된 값을 웹 오류로 바꾼다. 모르는 kind는 Error로 감싸고, Kotlin 예외가 아니면 원래 값 */
  translate(error: unknown): unknown
  /** onSyncChange 인자. null·undefined는 null(모두 올림), JsErrorInfo와 Kotlin 예외 둘 다 읽는다 */
  translateSyncError(error: unknown): unknown
}

function looksLikeErrorInfo(value: unknown): value is JsErrorInfo {
  return typeof value === 'object' && value !== null && typeof (value as { kind?: unknown }).kind === 'string'
}

export function createErrorTranslator(errorInfo: KotlinModule['errorInfo']): ErrorTranslator {
  function fromInfo(info: JsErrorInfo, original: unknown): unknown {
    const build = Object.hasOwn(BUILDERS, info.kind) ? BUILDERS[info.kind] : undefined
    // 모르는 kind는 JsErrorInfo 객체가 그대로 새지 않게 Error로 감싼다. 원본은 cause에 남긴다
    return build === undefined ? new Error(info.message ?? info.kind, { cause: original }) : build(info, original)
  }

  function translate(error: unknown): unknown {
    const info = errorInfo(error)
    return info == null ? error : fromInfo(info, error)
  }

  function translateSyncError(error: unknown): unknown {
    if (error === null || error === undefined) return null
    const info = errorInfo(error)
    if (info != null) return fromInfo(info, error)
    return looksLikeErrorInfo(error) ? fromInfo(error, error) : error
  }

  return { translate, translateSyncError }
}
