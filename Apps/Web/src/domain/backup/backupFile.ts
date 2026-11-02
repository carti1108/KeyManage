// 암호화 백업 파일 쓰기·읽기와 비밀번호로 열기 (VLT-004, vault-spec 6장, ADR-0022)
// 파일에는 암호문과 KDF 값만 담는다. 이메일·revision·세션 정보는 넣지 않는다
import type { UnlockKdf } from '../auth/authService'
import { InvalidBase64Error, fromBase64 } from '../crypto/base64'
import { InvalidKdfParamsError, assertValidKdf, deriveKeys } from '../crypto/keys'
import { UnsupportedPayloadError, VaultDecryptError, openVault, type EncryptedVaultPayload } from '../crypto/vaultCipher'
import { VAULT_SCHEMA_VERSION, formatTime, parseVaultData, type VaultData } from '../vault/vaultData'
import { MAX_PAYLOAD_BYTES, VaultTooLargeError, assertWithinLimit } from '../vault/vaultSync'

export const BACKUP_FORMAT = 'keymanage-backup'
/** 이 앱이 아는 백업 형식 버전. 더 높으면 복원하지 않는다 */
export const BACKUP_FORMAT_VERSION = 1
/** 파일의 머리 필드(format, kdf 등)와 들여쓰기 몫. 볼트 한도 직전 볼트를 내보낸 파일도 다시 읽게 한다 */
export const BACKUP_FILE_ALLOWANCE_BYTES = 65_536
/** 파일 전체를 읽는 한도. 볼트 한도(VLT-001)는 vault 필드를 압축 JSON으로 다시 쓴 크기에 따로 적용한다 */
export const MAX_BACKUP_FILE_BYTES = MAX_PAYLOAD_BYTES + BACKUP_FILE_ALLOWANCE_BYTES

const KDF_NAME = 'PBKDF2-HMAC-SHA256'
const PAYLOAD_VERSION = 1
const PAYLOAD_CIPHER = 'AES-256-GCM'

/** 형식이 맞지 않거나 5MB를 넘거나 KDF 값이 검증을 어김. 복호화를 시도하지 않는다 */
export class NotABackupFileError extends Error {
  constructor(message: string, options?: ErrorOptions) {
    super(message, options)
    this.name = 'NotABackupFileError'
  }
}

/** 이 앱보다 새 버전이 만든 백업(형식 버전, 암호화 볼트 버전, 볼트 스키마 버전) */
export class NewerBackupVersionError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'NewerBackupVersionError'
  }
}

/** 비밀번호가 틀렸거나 파일이 손상됨. AES-GCM은 둘을 구분하지 못한다 */
export class BackupDecryptError extends Error {
  constructor() {
    super('백업 비밀번호가 틀렸거나 파일이 손상됨')
    this.name = 'BackupDecryptError'
  }
}

/** 저장할 백업 파일 */
export interface BackupFile {
  readonly name: string
  readonly text: string
}

/** 사용자가 고른 파일. 브라우저 File이 그대로 맞는다. 크기를 먼저 보고 내용을 읽는다 */
export interface BackupFileSource {
  readonly size: number
  text(): Promise<string>
}

/** 백업 파일 저장·고르기 포트. 브라우저는 다운로드와 파일 선택 창을 쓴다 */
export interface BackupFiles {
  save(file: BackupFile): void
  /** @returns 고른 파일. 취소했으면 null */
  pick(): Promise<BackupFileSource | null>
}

/** 형식을 확인한 백업. 아직 암호문이다 */
export interface VaultBackup {
  readonly formatVersion: number
  readonly createdAt: string
  readonly kdf: UnlockKdf
  readonly vault: EncryptedVaultPayload
}

/** keymanage-backup-YYYYMMDD.json. 날짜는 이 기기의 현지 날짜다 */
export function backupFileName(now: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `keymanage-backup-${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}.json`
}

/** @param vault 지금 키로 새 IV를 써서 암호화한 볼트 */
export function createBackupFile(vault: EncryptedVaultPayload, kdf: UnlockKdf, now: Date): BackupFile {
  const body = {
    format: BACKUP_FORMAT,
    formatVersion: BACKUP_FORMAT_VERSION,
    createdAt: formatTime(now),
    kdf: { name: KDF_NAME, iterations: kdf.iterations, salt: kdf.salt },
    vault,
  }
  return { name: backupFileName(now), text: JSON.stringify(body, null, 2) }
}

/**
 * 크기를 먼저 확인하고, 넘으면 내용을 읽지 않는다
 * @throws NotABackupFileError, NewerBackupVersionError
 */
export async function readBackupFile(source: BackupFileSource): Promise<VaultBackup> {
  if (source.size > MAX_BACKUP_FILE_BYTES) throw new NotABackupFileError(`파일이 ${MAX_BACKUP_FILE_BYTES}바이트를 넘음`)
  let text: string
  try {
    text = await source.text()
  } catch (error) {
    throw new NotABackupFileError('파일을 읽지 못함', { cause: error })
  }
  return parseBackup(text)
}

/** @throws NotABackupFileError, NewerBackupVersionError */
export function parseBackup(text: string): VaultBackup {
  if (new TextEncoder().encode(text).length > MAX_BACKUP_FILE_BYTES) throw new NotABackupFileError(`파일이 ${MAX_BACKUP_FILE_BYTES}바이트를 넘음`)
  let raw: unknown
  try {
    raw = JSON.parse(text)
  } catch {
    throw new NotABackupFileError('JSON이 아님')
  }
  if (!isRecord(raw) || raw.format !== BACKUP_FORMAT) throw new NotABackupFileError('format이 keymanage-backup이 아님')
  const { formatVersion, createdAt } = raw
  if (!isPositiveInteger(formatVersion)) throw new NotABackupFileError('formatVersion이 1 이상의 정수가 아님')
  if (formatVersion > BACKUP_FORMAT_VERSION) throw new NewerBackupVersionError(`모르는 백업 형식 버전: ${formatVersion}`)
  if (typeof createdAt !== 'string') throw new NotABackupFileError('createdAt이 문자열이 아님')
  return { formatVersion, createdAt, kdf: parseKdf(raw.kdf), vault: parsePayload(raw.vault) }
}

/**
 * 파일의 KDF 값과 비밀번호로 키를 만들어(SEC-001과 같은 전처리·검증) 볼트를 연다
 * @throws BackupDecryptError 비밀번호가 틀렸거나 파일이 손상됨
 * @throws NewerBackupVersionError 볼트 스키마 버전이 앱이 아는 것보다 높음
 * @throws InvalidPasswordError, InvalidVaultDataError(복호화는 됐지만 내용이 규격과 다름)
 */
export async function openBackup(backup: VaultBackup, password: string): Promise<VaultData> {
  const keys = await deriveKeys(password, fromBase64(backup.kdf.salt), backup.kdf.iterations)
  // 인증 해시는 서버 로그인에만 쓴다. 복원에는 필요 없으니 바로 지운다
  keys.authHash.fill(0)
  let json: string
  try {
    json = await openVault(keys.encryptionKey, backup.vault)
  } catch (error) {
    if (error instanceof VaultDecryptError) throw new BackupDecryptError()
    if (error instanceof UnsupportedPayloadError) throw new NotABackupFileError(error.message, { cause: error })
    throw error
  }
  // 새 버전 볼트는 지금 규칙으로 검사하면 형식 오류로 보일 수 있으니 버전부터 본다
  const version = schemaVersionOf(json)
  if (version !== null && version > VAULT_SCHEMA_VERSION) throw new NewerBackupVersionError(`모르는 볼트 스키마 버전: ${version}`)
  return parseVaultData(json)
}

function parseKdf(value: unknown): UnlockKdf {
  if (!isRecord(value) || value.name !== KDF_NAME) throw new NotABackupFileError(`kdf.name이 ${KDF_NAME}이 아님`)
  const { iterations, salt } = value
  if (typeof iterations !== 'number' || typeof salt !== 'string') throw new NotABackupFileError('kdf 필드 타입이 틀림')
  try {
    assertValidKdf(decode(salt, 'kdf.salt'), iterations)
  } catch (error) {
    if (error instanceof InvalidKdfParamsError) throw new NotABackupFileError(error.message, { cause: error })
    throw error
  }
  return { iterations, salt }
}

function parsePayload(value: unknown): EncryptedVaultPayload {
  if (!isRecord(value)) throw new NotABackupFileError('vault가 객체가 아님')
  const { version, cipher, iv, ciphertext, tag } = value
  if (!isPositiveInteger(version)) throw new NotABackupFileError('vault.version이 1 이상의 정수가 아님')
  if (version > PAYLOAD_VERSION) throw new NewerBackupVersionError(`모르는 볼트 암호화 버전: ${version}`)
  if (cipher !== PAYLOAD_CIPHER) throw new NotABackupFileError(`vault.cipher가 ${PAYLOAD_CIPHER}이 아님`)
  for (const [name, field] of [['iv', iv], ['ciphertext', ciphertext], ['tag', tag]] as const) {
    if (typeof field !== 'string') throw new NotABackupFileError(`vault.${name}이 문자열이 아님`)
    decode(field, `vault.${name}`)
  }
  const payload: EncryptedVaultPayload = { version: PAYLOAD_VERSION, cipher: PAYLOAD_CIPHER, iv: iv as string, ciphertext: ciphertext as string, tag: tag as string }
  // 볼트 한도는 저장할 때와 같은 압축 JSON 크기로 잰다
  try {
    assertWithinLimit(payload)
  } catch (error) {
    if (error instanceof VaultTooLargeError) throw new NotABackupFileError(error.message, { cause: error })
    throw error
  }
  return payload
}

function decode(text: string, name: string): Uint8Array<ArrayBuffer> {
  try {
    return fromBase64(text)
  } catch (error) {
    if (error instanceof InvalidBase64Error) throw new NotABackupFileError(`${name}이 표준 Base64가 아님`)
    throw error
  }
}

function schemaVersionOf(json: string): number | null {
  try {
    const raw: unknown = JSON.parse(json)
    return isRecord(raw) && typeof raw.version === 'number' ? raw.version : null
  } catch {
    return null
  }
}

function isPositiveInteger(value: unknown): value is number {
  return Number.isInteger(value) && (value as number) >= 1
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
