// 화면이 쓰는 세션 기능만 추린 포트 (ADR-0004). app이 실제 세션을 넘긴다
import type { VaultSession } from '../../domain/session/vaultSession'

export type SessionPort = Pick<VaultSession, 'state' | 'login' | 'register' | 'unlock' | 'reconnect' | 'save' | 'lock' | 'logout' | 'onLock' | 'onUnlock' | 'onSyncChange' | 'pendingOnDevice' | 'onRemoteChange' | 'exportBackup' | 'restoreBackup'>

/** 서버 볼트 되돌림 경고를 사용자가 확인했을 때만 acceptRollback을 켠다 (VLT-001) */
export interface RollbackOption {
  readonly acceptRollback?: boolean
}
