// 세션이 밖으로 내보내는 오류·상태 타입과 화면이 쓰는 세션 포트(VaultSession)의 모양 (SEC-002, SEC-003)
// 세션 로직은 Kotlin 공유 세션(Apps/Shared/web-session)에 있고, 웹은 어댑터(adapters/kotlin)로 이 포트에 맞춘다 (domain-migration-plan 10장)
import type { BackupFile, VaultBackup } from '../backup/backupFile'
import type { VaultData } from '../vault/vaultData'
import type { SyncedVault } from '../vault/vaultSync'

/** 마스터 비밀번호가 틀려 저장해 둔 볼트를 열 수 없음 */
export class WrongMasterPasswordError extends Error {
  constructor() {
    super('마스터 비밀번호가 올바르지 않음')
    this.name = 'WrongMasterPasswordError'
  }
}

/** 지금 상태에서는 할 수 없는 동작이거나, 기다리는 사이 잠금·로그아웃으로 취소된 작업 */
export class SessionStateError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'SessionStateError'
  }
}

/** 세션 토큰이 만료·폐기됨. 로그아웃 상태로 바뀌었으니 다시 로그인해야 한다 (SEC-002) */
export class SessionExpiredError extends Error {
  constructor() {
    super('세션이 만료됨')
    this.name = 'SessionExpiredError'
  }
}

/** 가입은 됐지만 빈 볼트를 저장하지 못함. 다시 가입하지 말고 로그인하면 그때 볼트를 만든다 (SEC-003) */
export class VaultSetupError extends Error {
  constructor(cause: unknown) {
    super('가입 뒤 볼트 준비 실패', { cause })
    this.name = 'VaultSetupError'
  }
}

/** 새 마스터 비밀번호가 SEC-002 길이(12자)보다 짧음 */
export class NewMasterPasswordTooShortError extends Error {
  constructor() {
    super('새 마스터 비밀번호가 너무 짧음')
    this.name = 'NewMasterPasswordTooShortError'
  }
}

/** 새 마스터 비밀번호가 지금 비밀번호와 같음 (SEC-004 규칙 2) */
export class SameMasterPasswordError extends Error {
  constructor() {
    super('새 마스터 비밀번호가 지금과 같음')
    this.name = 'SameMasterPasswordError'
  }
}

/** 세션 없이 연 볼트라 서버에 연결되지 않음. 비밀번호는 서버에 연결된 상태에서만 바꾼다 (SEC-004 규칙 1) */
export class PasswordChangeOfflineError extends Error {
  constructor() {
    super('서버에 연결되지 않아 비밀번호를 바꿀 수 없음')
    this.name = 'PasswordChangeOfflineError'
  }
}

/** 세션 없이 연 볼트라 서버에 연결되지 않음. 계정은 서버에 연결된 상태에서만 지운다 (SEC-005 규칙 1) */
export class AccountDeletionOfflineError extends Error {
  constructor() {
    super('서버에 연결되지 않아 계정을 지울 수 없음')
    this.name = 'AccountDeletionOfflineError'
  }
}

/** 계정은 서버에서 지웠지만 이 기기의 기록을 지우지 못함. 이 탭은 이미 로그아웃됐다 (SEC-005 사용자 문구) */
export class AccountDeletedDeviceNotClearedError extends Error {
  constructor() {
    super('계정은 지웠지만 기기 기록을 지우지 못함')
    this.name = 'AccountDeletedDeviceNotClearedError'
  }
}

export type SessionState =
  | { readonly status: 'signedOut' }
  | {
      readonly status: 'locked'
      readonly email: string
      /** 서버에 올리지 못한 변경 사항이 암호화된 채 남아 있음 (VLT-002) */
      readonly pendingSync: boolean
    }
  | {
      readonly status: 'unlocked'
      readonly email: string
      readonly vault: SyncedVault
      /** 서버에 올리지 못한 변경 사항이 있음 (VLT-002) */
      readonly pendingSync: boolean
      /** 세션 토큰이 있음. 없으면(오프라인으로 다시 열었을 때) 비밀번호를 다시 입력받아야 동기화한다 (SEC-003) */
      readonly signedIn: boolean
      /** 마지막 기기 저장이 성공함. 실패하면 새로고침 때 올리지 못한 변경 사항이 사라질 수 있다 (VLT-003) */
      readonly storedOnDevice: boolean
    }

export interface RollbackOption {
  /** 서버 볼트 되돌림 경고를 사용자가 확인했을 때만 true (VLT-001) */
  readonly acceptRollback?: boolean
}

/** 동기화 대기 내용을 다시 보낸 결과. null이면 모두 올렸다 */
export type SyncChangeListener = (error: unknown) => void

/** allTabs: 같은 계정의 모든 탭을 잠근다. thisTab: 이 탭만 잠근다 (SEC-003, ADR-WEB-001) */
export type LockScope = 'allTabs' | 'thisTab'

/**
 * 화면과 앱 배선이 쓰는 세션 포트. Kotlin 공유 세션을 감싼 어댑터(adapters/kotlin/kotlinSession)가 구현한다.
 * 각 메서드가 던지는 오류는 Kotlin 예외를 웹 오류 클래스로 바꾼 것이다 (adapters/kotlin/kotlinErrors)
 */
export interface VaultSession {
  state(): SessionState
  /** @throws VaultSetupError 가입은 됐지만 빈 볼트를 저장하지 못했을 때 */
  register(email: string, password: string): Promise<void>
  /** @throws RevisionRollbackError 서버 볼트가 되돌아감. 사용자가 확인하면 acceptRollback으로 다시 부른다 */
  login(email: string, password: string, options?: RollbackOption): Promise<void>
  /** 키와 평문 볼트를 버린다. allTabs면 같은 계정의 다른 탭도 잠근다 (SEC-003) */
  lock(scope?: LockScope): void
  /**
   * @returns offline: 서버와 맞추지 못했는지
   * @throws WrongMasterPasswordError, MasterPasswordChangedError, SessionExpiredError, RevisionRollbackError, SessionStateError
   */
  unlock(password: string, options?: RollbackOption): Promise<{ offline: boolean }>
  /** 세션 없이 연 볼트에서 비밀번호를 다시 입력받아 로그인하고 서버와 맞춘다 (SEC-003, ADR-0019) */
  reconnect(password: string, options?: RollbackOption): Promise<void>
  /** 먼저 이 기기에 반영한 뒤 서버에 보낸다. 서버에 닿지 못하면 동기화 대기로 두고 성공으로 끝난다 (VLT-002) */
  save(data: VaultData): Promise<void>
  /** 화면의 볼트를 지금 키로 새로 암호화해 백업 파일로 만든다 (VLT-004) */
  exportBackup(now: Date): Promise<BackupFile>
  /** @returns 복원한 항목 수 */
  restoreBackup(backup: VaultBackup, password: string, now: Date): Promise<number>
  /** 마스터 비밀번호를 바꾼다 (SEC-004) */
  changeMasterPassword(currentPassword: string, newPassword: string): Promise<void>
  /** 계정을 지운다 (SEC-005) */
  deleteAccount(password: string): Promise<void>
  /** 서버에 올리지 못한 변경 사항이 기기에도 아직 저장되지 않았는지. 떠남 확인용 (VLT-002) */
  hasUnsavedChanges(): boolean
  /** 기기 기록이 있으면 그 계정의 잠금 화면으로 시작한다 (SEC-003, ADR-0019). 앱을 열 때 한 번 부른다 */
  restore(): Promise<void>
  /** 동기화 대기 내용을 다시 보낸다. 결과는 onSyncChange로 알린다 */
  retryPendingSync(): Promise<void>
  /** 이 기기의 세션과 기기 기록을 먼저 지우고 서버에 토큰 폐기를 요청한다 */
  logout(): Promise<void>
  /** 기기 기록에 서버에 올리지 못한 변경이 있는지. 로그아웃 확인에 쓴다 (ADR-WEB-001) */
  pendingOnDevice(): Promise<boolean>
  /** 다른 탭 때문에 상태가 바뀌었을 때 부를 함수를 등록한다. 해제 함수를 돌려준다 */
  onRemoteChange(listener: () => void): () => void
  /** 볼트가 닫힐 때 부를 함수를 등록한다. 클립보드 비우기 등 (COM-001) */
  onLock(listener: () => void): () => void
  /** 볼트가 열릴 때 부를 함수를 등록한다. 자동 잠금 타이머 재시작 등 */
  onUnlock(listener: () => void): () => void
  /** 대기 내용 재전송 결과나 세션 만료를 알릴 함수를 등록한다 */
  onSyncChange(listener: SyncChangeListener): () => void
}
