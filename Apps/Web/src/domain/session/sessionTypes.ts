// 세션이 밖으로 내보내는 오류·상태 타입과 세션 안에서 나누는 값의 모양 (SEC-002, SEC-003)
import type { AuthService, UnlockKdf } from '../auth/authService'
import type { EncryptedVaultPayload } from '../crypto/vaultCipher'
import type { DeviceStore } from '../device/deviceRecord'
import type { TabMemory } from '../device/tabMemory'
import type { Tabs } from '../tabs/tabs'
import type { RevisionMemory, SyncedVault, VaultSync } from '../vault/vaultSync'

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

export interface VaultSessionDeps {
  readonly auth: AuthService
  /** revisionMemory는 세션이 넘기는 이번 로그인 전용 창이다. 로그인이 바뀐 뒤 늦게 온 응답은 기준을 바꾸지 못한다 */
  readonly vaultSyncFor: (accessToken: string, encryptionKey: CryptoKey, revisionMemory: RevisionMemory) => VaultSync
  /** 되돌림 감지 기준. 로그아웃·계정 전환 때 지운다 */
  readonly revisionMemory: RevisionMemory
  /** 한 계정의 기록을 두는 기기 저장소 (ADR-0019) */
  readonly deviceStore: DeviceStore
  /** 같은 브라우저의 다른 탭과 기기 기록 쓰기를 나누고 변경을 알린다 (ADR-WEB-001) */
  readonly tabs: Tabs
  /** 이 탭이 마지막으로 연 계정의 KDF 값. 새로 열렸을 때 다른 탭의 비밀번호 변경을 알아챈다. 없으면 기억하지 않는다 (SEC-003 규칙 11) */
  readonly tabMemory?: TabMemory
}

// 잠겨도 남는 값. 비밀이 아니거나(KDF 값) 암호화돼 있다(볼트)
export interface Account {
  readonly email: string
  /** 기기 기록으로 다시 열었고 아직 로그인하지 못했으면 null (ADR-0019) */
  readonly accessToken: string | null
  readonly kdf: UnlockKdf
  /** 마지막 볼트. 동기화 대기 중이면 대기 내용을 담은 볼트다 */
  readonly payload: EncryptedVaultPayload
  /** payload가 바탕으로 삼은 서버 revision */
  readonly revision: number
  readonly pendingSync: boolean
}

// 잠금 해제 동안만 있는 값
export interface Unlocked {
  readonly sync: VaultSync
  readonly vault: SyncedVault
  readonly pendingSync: boolean
}

// 세션 만료로 로그아웃될 때 남긴 동기화 대기 내용. 같은 계정으로 다시 로그인하면 올린다 (VLT-002)
export interface OrphanedPending {
  readonly email: string
  readonly payload: EncryptedVaultPayload
  readonly revision: number
}

export interface RollbackOption {
  /** 서버 볼트 되돌림 경고를 사용자가 확인했을 때만 true (VLT-001) */
  readonly acceptRollback?: boolean
}

/** 동기화 대기 내용을 다시 보낸 결과. null이면 모두 올렸다 */
export type SyncChangeListener = (error: unknown) => void

/** allTabs: 같은 계정의 모든 탭을 잠근다. thisTab: 이 탭만 잠근다 (SEC-003, ADR-WEB-001) */
export type LockScope = 'allTabs' | 'thisTab'
