// 암호화 백업 파일 쓰기·읽기와 비밀번호로 열기(VLT-004, vault-spec 6장, ADR-0022)는 공유 Kotlin(Apps/Shared/domain의 BackupFile)이 한다.
// 웹에는 파일 입출력 포트 타입, 파일 이름 규칙, 화면이 오류 종류를 구분하는 데 쓰는 오류 정의만 남긴다
import type { UnlockKdf } from '../auth/authService'
import type { EncryptedVaultPayload } from '../crypto/vaultCipher'

export const BACKUP_FORMAT = 'keymanage-backup'

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
