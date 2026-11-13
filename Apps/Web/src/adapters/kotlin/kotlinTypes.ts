// Kotlin/JS(Apps/Shared/web-session)가 내보내는 것과 웹이 넘기는 포트의 모양 (docs/research/kmp-migration/domain-migration-plan.md 10장)
// Kotlin 빌드 결과(.d.mts)가 생기면 이 파일의 KotlinModule을 빌드 결과의 타입으로 바꿀 수 있다

/** 웹이 Kotlin에 넘기는 기기 저장소. 기록은 JSON 문자열이다. 실패는 reject */
export interface JsDeviceStore {
  load(): Promise<string | null>
  save(recordJson: string): Promise<void>
  clear(): Promise<void>
}

export interface JsTabs {
  /** ifAvailable인데 잠금을 못 얻으면 fn 없이 null */
  lockRun(name: 'device' | 'sync', ifAvailable: boolean, fn: () => Promise<unknown>): Promise<unknown>
  post(kind: string): void
  subscribe(listener: (kind: string) => void): () => void
}

/** {"email","salt","iterations"} JSON */
export interface JsTabMemory {
  get(): string | null
  set(json: string): void
  clear(): void
}

export type JsSessionStatus = 'signedOut' | 'locked' | 'unlocked'

export interface JsSessionState {
// Kotlin/JS는 nullable을 T | null | undefined로 내보낸다
  readonly status: string
  readonly email: string | null | undefined
  readonly pendingSync: boolean
  readonly signedIn: boolean
  readonly storedOnDevice: boolean
  readonly revision: number
  readonly readOnly: boolean
  /** encodeVaultData 결과 */
  readonly vaultJson: string | null | undefined
  /** SyncedVault.payload의 JSON(EncryptedVaultPayload). 없으면 null */
  readonly payloadJson: string | null | undefined
}

export interface JsErrorInfo {
  /** Kotlin 예외 클래스 simpleName */
  readonly kind: string
  readonly message: string | null | undefined
  readonly status: number | null | undefined
  readonly code: string | null | undefined
  readonly retryAfterSeconds: number | null | undefined
  /** 그 밖의 필드 JSON (예: RevisionRollbackException의 lastSeenRevision·serverRevision) */
  readonly detailJson: string | null | undefined
}

export interface KotlinVaultSession {
  state(): JsSessionState
  register(email: string, password: string): Promise<void>
  login(email: string, password: string, acceptRollback: boolean): Promise<void>
  lock(scope: 'allTabs' | 'thisTab'): void
  /** @returns 서버와 맞추지 못해 기기 저장본으로 열었는지(offline) */
  unlock(password: string, acceptRollback: boolean): Promise<boolean>
  reconnect(password: string, acceptRollback: boolean): Promise<void>
  save(vaultJson: string): Promise<void>
  exportBackup(nowEpochMs: number): Promise<string>
  restoreBackup(backupJson: string, password: string, nowEpochMs: number): Promise<number>
  changeMasterPassword(currentPassword: string, newPassword: string): Promise<void>
  deleteAccount(password: string): Promise<void>
  hasUnsavedChanges(): boolean
  restore(): Promise<void>
  retryPendingSync(): Promise<void>
  logout(): Promise<void>
  pendingOnDevice(): Promise<boolean>
  onRemoteChange(listener: () => void): () => void
  onLock(listener: () => void): () => void
  onUnlock(listener: () => void): () => void
  /** 계약상 인자는 JsErrorInfo | null. Kotlin 예외 그대로 올 때도 읽는다 */
  onSyncChange(listener: (error: unknown) => void): () => void
}

/** Kotlin 빌드 결과가 @JsExport로 내보내는 최상위 함수 */
export interface KotlinModule {
  createKotlinSession(apiBaseUrl: string | null, deviceStore: JsDeviceStore, tabs: JsTabs, tabMemory: JsTabMemory | null): KotlinVaultSession
  /** Kotlin 예외가 아니면 null */
  errorInfo(error: unknown): JsErrorInfo | null | undefined
}
