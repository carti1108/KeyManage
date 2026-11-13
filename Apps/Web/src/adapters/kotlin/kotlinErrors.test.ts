import { describe, expect, it } from 'vitest'
import { InvalidEmailError, MasterPasswordChangedError } from '../../domain/auth/authService'
import { BackupDecryptError, NewerBackupVersionError, NotABackupFileError } from '../../domain/backup/backupFile'
import { InvalidBase64Error } from '../../domain/crypto/base64'
import { InvalidKdfParamsError, InvalidPasswordError } from '../../domain/crypto/keys'
import { UnsupportedPayloadError, VaultDecryptError } from '../../domain/crypto/vaultCipher'
import { DeviceStorageError } from '../../domain/device/deviceRecord'
import { NetworkError, ServerError, UnexpectedResponseError } from '../../domain/server/serverErrors'
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
} from '../../domain/session/vaultSession'
import { InvalidVaultDataError } from '../../domain/vault/vaultData'
import { ReadOnlyVaultError, RevisionRollbackError, VaultSyncConflictError, VaultTooLargeError } from '../../domain/vault/vaultSync'
import { FakeKotlinThrowable, createFakeKotlin } from '../../test/fakeKotlinModule'
import { InvalidBaseUrlError } from '../http/baseUrl'
import { createErrorTranslator } from './kotlinErrors'

const { translate, translateSyncError } = createErrorTranslator(createFakeKotlin().module.errorInfo)

type Case = readonly [kind: string, expected: new (...args: never[]) => Error]

const SIMPLE_CASES: readonly Case[] = [
  ['WrongMasterPasswordException', WrongMasterPasswordError],
  ['SessionExpiredException', SessionExpiredError],
  ['MasterPasswordChangedException', MasterPasswordChangedError],
  ['InvalidEmailException', InvalidEmailError],
  ['VaultSyncConflictException', VaultSyncConflictError],
  ['ReadOnlyVaultException', ReadOnlyVaultError],
  ['AccountDeletionOfflineException', AccountDeletionOfflineError],
  ['AccountDeletedDeviceNotClearedException', AccountDeletedDeviceNotClearedError],
  ['NewMasterPasswordTooShortException', NewMasterPasswordTooShortError],
  ['SameMasterPasswordException', SameMasterPasswordError],
  ['PasswordChangeOfflineException', PasswordChangeOfflineError],
  ['BackupDecryptException', BackupDecryptError],
  ['VaultDecryptException', VaultDecryptError],
  ['InvalidBase64Exception', InvalidBase64Error],
  ['InvalidBaseUrlException', InvalidBaseUrlError],
]

const MESSAGE_CASES: readonly Case[] = [
  ['SessionStateException', SessionStateError],
  ['InvalidPasswordException', InvalidPasswordError],
  ['InvalidKdfParamsException', InvalidKdfParamsError],
  ['InvalidVaultDataException', InvalidVaultDataError],
  ['NotABackupFileException', NotABackupFileError],
  ['NewerBackupVersionException', NewerBackupVersionError],
  ['UnsupportedPayloadException', UnsupportedPayloadError],
  ['UnexpectedResponseError', UnexpectedResponseError],
  ['RevisionMismatchException', UnexpectedResponseError],
  ['NetworkError', NetworkError],
]

describe('Kotlin 오류를 웹 오류로 바꾸기', () => {
  it.each(SIMPLE_CASES)('%s는 웹 오류 클래스로 바뀐다', (kind, expected) => {
    const translated = translate(new FakeKotlinThrowable(kind))

    expect(translated).toBeInstanceOf(expected)
  })

  it.each(MESSAGE_CASES)('%s는 클래스와 메시지를 그대로 옮긴다', (kind, expected) => {
    const translated = translate(new FakeKotlinThrowable(kind, { message: '원래 메시지' }))

    expect(translated).toBeInstanceOf(expected)
    expect((translated as Error).message).toBe('원래 메시지')
  })

  it('메시지가 없으면 웹 오류의 기본 메시지를 쓴다', () => {
    expect((translate(new FakeKotlinThrowable('NetworkError')) as Error).message).toBe('서버에 연결할 수 없음')
  })

  it('ServerError는 status, code, retryAfterSeconds를 보존한다', () => {
    const translated = translate(new FakeKotlinThrowable('ServerError', { status: 429, code: 'TOO_MANY_ATTEMPTS', retryAfterSeconds: 30 })) as ServerError

    expect(translated).toBeInstanceOf(ServerError)
    expect(translated.status).toBe(429)
    expect(translated.code).toBe('TOO_MANY_ATTEMPTS')
    expect(translated.retryAfterSeconds).toBe(30)
  })

  it('ServerError의 retryAfterSeconds가 없으면 undefined이고, 모르는 code는 UNKNOWN이다', () => {
    const translated = translate(new FakeKotlinThrowable('ServerError', { status: 418, code: 'TEAPOT' })) as ServerError

    expect(translated.code).toBe('UNKNOWN')
    expect(translated.retryAfterSeconds).toBeUndefined()
    expect(translate(new FakeKotlinThrowable('ServerError', { status: 500 }))).toMatchObject({ code: 'UNKNOWN' })
  })

  it('RevisionRollbackException은 detailJson의 revision 값을 보존한다', () => {
    const translated = translate(new FakeKotlinThrowable('RevisionRollbackException', { detailJson: '{"lastSeenRevision":7,"serverRevision":3}' })) as RevisionRollbackError

    expect(translated).toBeInstanceOf(RevisionRollbackError)
    expect(translated.lastSeenRevision).toBe(7)
    expect(translated.serverRevision).toBe(3)
  })

  it('VaultTooLargeException은 sizeInBytes를 보존한다', () => {
    const translated = translate(new FakeKotlinThrowable('VaultTooLargeException', { detailJson: '{"sizeInBytes":6000000}' })) as VaultTooLargeError

    expect(translated).toBeInstanceOf(VaultTooLargeError)
    expect(translated.sizeInBytes).toBe(6_000_000)
  })

  it('detailJson이 없거나 깨져도 분류는 유지한다 (되돌림 확인 흐름이 끊기지 않게)', () => {
    expect(translate(new FakeKotlinThrowable('RevisionRollbackException'))).toBeInstanceOf(RevisionRollbackError)
    expect(translate(new FakeKotlinThrowable('RevisionRollbackException', { detailJson: '{깨짐' }))).toBeInstanceOf(RevisionRollbackError)
    expect(translate(new FakeKotlinThrowable('VaultTooLargeException', { detailJson: '[]' }))).toBeInstanceOf(VaultTooLargeError)
  })

  it('DeviceStorageException과 VaultSetupException은 원래 Kotlin 예외를 cause로 둔다', () => {
    const original = new FakeKotlinThrowable('DeviceStorageException')
    const setup = new FakeKotlinThrowable('VaultSetupException')

    const device = translate(original) as DeviceStorageError
    const vault = translate(setup) as VaultSetupError

    expect(device).toBeInstanceOf(DeviceStorageError)
    expect(device.cause).toBe(original)
    expect(vault).toBeInstanceOf(VaultSetupError)
    expect(vault.cause).toBe(setup)
  })

  it('모르는 kind는 message를 보존한 Error로 감싼다 (cause는 원본, message가 없으면 kind)', () => {
    const withMessage = new FakeKotlinThrowable('SomethingNewException', { message: '새 오류' })
    const withoutMessage = new FakeKotlinThrowable('SomethingNewException')

    const wrapped = translate(withMessage) as Error
    const bare = translate(withoutMessage) as Error

    expect(wrapped).toBeInstanceOf(Error)
    expect(wrapped.message).toBe('새 오류')
    expect(wrapped.cause).toBe(withMessage)
    expect(bare.message).toBe('SomethingNewException')
    expect(bare.cause).toBe(withoutMessage)
  })

  it('Kotlin 예외가 아닌 값(웹 오류, 문자열, undefined)은 그대로 돌려준다', () => {
    const own = new Error('브라우저 오류')

    expect(translate(own)).toBe(own)
    expect(translate('문자열')).toBe('문자열')
    expect(translate(undefined)).toBeUndefined()
  })
})

describe('onSyncChange 인자 바꾸기', () => {
  it('null이나 undefined는 null(모두 올림)이다', () => {
    expect(translateSyncError(null)).toBeNull()
    expect(translateSyncError(undefined)).toBeNull()
  })

  it('Kotlin 예외 그대로 와도 읽는다', () => {
    expect(translateSyncError(new FakeKotlinThrowable('NetworkError'))).toBeInstanceOf(NetworkError)
  })

  it('계약대로 JsErrorInfo가 와도 읽는다', () => {
    const info = new FakeKotlinThrowable('ServerError', { status: 401, code: 'UNAUTHORIZED' }).info

    const translated = translateSyncError(info) as ServerError

    expect(translated).toBeInstanceOf(ServerError)
    expect(translated.code).toBe('UNAUTHORIZED')
  })

  it('알 수 없는 값은 그대로 돌려준다', () => {
    const other = new Error('다른 오류')

    expect(translateSyncError(other)).toBe(other)
  })

  it('모르는 kind의 JsErrorInfo는 객체 그대로가 아니라 message를 보존한 Error로 감싼다 (cause는 원본)', () => {
    const info = new FakeKotlinThrowable('SomethingNewException', { message: '새 오류' }).info

    const translated = translateSyncError(info) as Error

    expect(translated).toBeInstanceOf(Error)
    expect(translated.message).toBe('새 오류')
    expect(translated.cause).toBe(info)
  })

  it('모르는 kind의 Kotlin 예외도 Error로 감싸고, message가 없으면 kind를 메시지로 쓴다', () => {
    const thrown = new FakeKotlinThrowable('SomethingNewException')

    const translated = translateSyncError(thrown) as Error

    expect(translated).toBeInstanceOf(Error)
    expect(translated.message).toBe('SomethingNewException')
    expect(translated.cause).toBe(thrown)
  })
})
