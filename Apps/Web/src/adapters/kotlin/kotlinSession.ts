// Kotlin 공유 세션(Apps/Shared/web-session)을 웹 SessionPort로 감싼다 (docs/research/kmp-migration/domain-migration-plan.md 10장)
// 웹은 세션 로직을 갖지 않는다. 포트·상태·오류·백업 파일만 웹 모양과 Kotlin(JS 내보내기) 모양 사이에서 바꾼다
import { BACKUP_FORMAT, backupFileName, type BackupFile, type VaultBackup } from '../../domain/backup/backupFile'
import type { DeviceStore } from '../../domain/device/deviceRecord'
import type { TabMemory } from '../../domain/device/tabMemory'
import type { LockScope, RollbackOption, SyncChangeListener } from '../../domain/session/sessionTypes'
import type { VaultSession } from '../../domain/session/vaultSession'
import type { Tabs } from '../../domain/tabs/tabs'
import type { VaultData } from '../../domain/vault/vaultData'
import { createErrorTranslator } from './kotlinErrors'
import { toJsDeviceStore, toJsTabMemory, toJsTabs } from './kotlinPorts'
import { createStateReader } from './kotlinState'
import type { KotlinModule } from './kotlinTypes'

export interface KotlinSessionDeps {
  /** 서버 주소. 없으면 Kotlin이 기본값을 쓴다. 규칙에 맞지 않으면 만들 때 InvalidBaseUrlError */
  readonly apiBaseUrl: string | null | undefined
  readonly deviceStore: DeviceStore
  readonly tabs: Tabs
  readonly tabMemory: TabMemory
}

// 백업 파일의 kdf.name. backupFile.ts의 값과 같다(파일 형식이 정한 고정값)
const BACKUP_KDF_NAME = 'PBKDF2-HMAC-SHA256'

// createBackupFile과 같은 필드 순서·들여쓰기로 쓴다. Kotlin의 parseBackup이 읽는 그 형식이다
function backupToJson(backup: VaultBackup): string {
  return JSON.stringify(
    {
      format: BACKUP_FORMAT,
      formatVersion: backup.formatVersion,
      createdAt: backup.createdAt,
      kdf: { name: BACKUP_KDF_NAME, iterations: backup.kdf.iterations, salt: backup.kdf.salt },
      vault: backup.vault,
    },
    null,
    2,
  )
}

/** 한 번만 해제하는 함수. Kotlin 쪽 해제가 두 번 불려도 한 번만 부른다 */
function once(unsubscribe: () => void): () => void {
  let done = false
  return () => {
    if (done) return
    done = true
    unsubscribe()
  }
}

/**
 * @param kotlin Kotlin 빌드 결과(@JsExport 함수 묶음)
 * @throws InvalidBaseUrlError 서버 주소가 규칙에 맞지 않음
 */
export function createKotlinSession(kotlin: KotlinModule, deps: KotlinSessionDeps): VaultSession {
  const { translate, translateSyncError } = createErrorTranslator(kotlin.errorInfo)
  // 동기 throw와 Promise reject를 같이 바꾼다
  async function call<T>(fn: () => Promise<T>): Promise<T> {
    try {
      return await fn()
    } catch (error) {
      throw translate(error)
    }
  }
  // Kotlin Promise<Unit>은 Unit 객체로 resolve되므로, 값이 없는 메서드는 undefined로 바꿔 돌려준다
  async function callVoid(fn: () => Promise<unknown>): Promise<void> {
    await call(fn)
  }
  function callSync<T>(fn: () => T): T {
    try {
      return fn()
    } catch (error) {
      throw translate(error)
    }
  }

  const session = callSync(() => kotlin.createKotlinSession(deps.apiBaseUrl ?? null, toJsDeviceStore(deps.deviceStore), toJsTabs(deps.tabs), toJsTabMemory(deps.tabMemory)))
  const readState = createStateReader(() => session.state())

  return {
    state: () => callSync(readState),
    register: (email: string, password: string) => callVoid(() => session.register(email, password)),
    login: (email: string, password: string, options: RollbackOption = {}) => callVoid(() => session.login(email, password, options.acceptRollback === true)),
    lock: (scope: LockScope = 'allTabs') => callSync(() => session.lock(scope)),
    unlock: (password: string, options: RollbackOption = {}) => call(async () => ({ offline: await session.unlock(password, options.acceptRollback === true) })),
    reconnect: (password: string, options: RollbackOption = {}) => callVoid(() => session.reconnect(password, options.acceptRollback === true)),
    save: (data: VaultData) => callVoid(() => session.save(JSON.stringify(data))),
    exportBackup: (now: Date): Promise<BackupFile> => call(async () => ({ name: backupFileName(now), text: await session.exportBackup(now.getTime()) })),
    restoreBackup: (backup: VaultBackup, password: string, now: Date) => call(() => session.restoreBackup(backupToJson(backup), password, now.getTime())),
    changeMasterPassword: (currentPassword: string, newPassword: string) => callVoid(() => session.changeMasterPassword(currentPassword, newPassword)),
    deleteAccount: (password: string) => callVoid(() => session.deleteAccount(password)),
    hasUnsavedChanges: () => callSync(() => session.hasUnsavedChanges()),
    restore: () => callVoid(() => session.restore()),
    retryPendingSync: () => callVoid(() => session.retryPendingSync()),
    logout: () => callVoid(() => session.logout()),
    pendingOnDevice: () => call(() => session.pendingOnDevice()),
    onRemoteChange: (listener: () => void) => once(callSync(() => session.onRemoteChange(listener))),
    onLock: (listener: () => void) => once(callSync(() => session.onLock(listener))),
    onUnlock: (listener: () => void) => once(callSync(() => session.onUnlock(listener))),
    onSyncChange: (listener: SyncChangeListener) => once(callSync(() => session.onSyncChange((error) => listener(translateSyncError(error))))),
  }
}
