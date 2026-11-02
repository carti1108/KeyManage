// 화면 테스트용 가짜 세션. 상태를 직접 바꾸고 실제 세션처럼 잠금·잠금 해제 알림을 보낸다
import { vi } from 'vitest'
import { backupFileName, type VaultBackup } from '../domain/backup/backupFile'
import type { SessionState } from '../domain/session/vaultSession'
import { emptyVault, type VaultData } from '../domain/vault/vaultData'
import type { SyncedVault } from '../domain/vault/vaultSync'
import type { SessionPort } from '../ui/session/sessionPort'

export function fakeVault(overrides: Partial<SyncedVault> = {}): SyncedVault {
  return { revision: 1, data: emptyVault(new Date('2026-10-31T00:00:00Z')), readOnly: false, payload: null, ...overrides }
}

export function createFakeSession(initial: SessionState = { status: 'signedOut' }) {
  let current: SessionState = initial
  const lockListeners = new Set<() => void>()
  const unlockListeners = new Set<() => void>()
  const syncListeners = new Set<(error: unknown) => void>()
  const remoteListeners = new Set<() => void>()
  // 기기 기록의 대기 표시. 다른 탭이 남긴 대기 내용을 흉내 낸다. null이면 이 탭의 대기 표시를 따른다
  let devicePending: boolean | null = null

  const control = {
    /** 진행 중인 로그아웃의 서버 요청을 끝낸다 */
    finishLogout: () => {},
    openVault(email = 'me@example.com', vault: SyncedVault = fakeVault()) {
      current = { status: 'unlocked', email, vault, pendingSync: false, signedIn: true, storedOnDevice: true }
      for (const listener of unlockListeners) listener()
    },
    closeVault() {
      if (current.status !== 'unlocked') return
      current = { status: 'locked', email: current.email, pendingSync: current.pendingSync }
      for (const listener of lockListeners) listener()
    },
    /** 동기화 대기 표시를 바꾼다. 실제 세션처럼 알림은 보내지 않는다 */
    setPending(pendingSync: boolean) {
      if (current.status !== 'signedOut') current = { ...current, pendingSync }
    },
    /** 기기 저장 성공 여부를 바꾼다. 실제 세션처럼 알림은 보내지 않는다 */
    setStoredOnDevice(storedOnDevice: boolean) {
      if (current.status === 'unlocked') current = { ...current, storedOnDevice }
    },
    /** 실제 세션처럼 저장 내용을 먼저 반영하고 대기로 둔다. 그 뒤 분명한 거절을 던지는 저장을 흉내 낼 때 쓴다 */
    commitPending(data: VaultData) {
      if (current.status === 'unlocked') current = { ...current, vault: { ...current.vault, data }, pendingSync: true }
    },
    /** 대기 내용 재전송 결과를 알린다. null이면 모두 올렸다 */
    emitSync(error: unknown) {
      for (const listener of syncListeners) listener(error)
    },
    /** 다른 탭이 남긴 대기 내용처럼 기기 기록의 대기 표시만 바꾼다 */
    setDevicePending(pending: boolean) {
      devicePending = pending
    },
    /** 다른 탭 때문에 상태가 바뀌었다고 알린다 */
    emitRemote() {
      for (const listener of remoteListeners) listener()
    },
    signOut() {
      const wasUnlocked = current.status === 'unlocked'
      current = { status: 'signedOut' }
      if (wasUnlocked) for (const listener of lockListeners) listener()
    },
  }

  const session = {
    state: () => current,
    login: vi.fn(async (email: string) => control.openVault(email)),
    register: vi.fn(async (email: string) => control.openVault(email)),
    unlock: vi.fn(async () => {
      if (current.status === 'locked') control.openVault(current.email)
      return { offline: false }
    }),
    // 실제 세션처럼 저장해도 알림은 보내지 않는다. 화면이 직접 다시 읽어야 한다
    save: vi.fn(async (data: VaultData) => {
      if (current.status !== 'unlocked') throw new Error('잠금 해제 상태가 아님')
      current = { ...current, vault: { ...current.vault, revision: current.vault.revision + 1, data } }
    }),
    // 실제 세션처럼 다시 로그인하면 세션이 생기고, 저장은 알림 없이 화면이 다시 읽는다
    reconnect: vi.fn(async (_password: string, _options?: { acceptRollback?: boolean }) => {
      if (current.status === 'unlocked') current = { ...current, signedIn: true }
    }),
    lock: vi.fn(() => control.closeVault()),
    // 실제 세션처럼 이 기기의 세션을 먼저 지우고, 서버 응답은 테스트가 정할 때 끝낸다
    logout: vi.fn(() => {
      control.signOut()
      return new Promise<void>((resolve) => (control.finishLogout = resolve))
    }),
    onLock: (listener: () => void) => {
      lockListeners.add(listener)
      return () => lockListeners.delete(listener)
    },
    onUnlock: (listener: () => void) => {
      unlockListeners.add(listener)
      return () => unlockListeners.delete(listener)
    },
    exportBackup: vi.fn(async (now: Date) => ({ name: backupFileName(now), text: '{}' })),
    // 실제 세션처럼 복원 결과를 저장하지만 알림은 보내지 않는다. 테스트가 mockImplementation으로 볼트를 바꾼다
    restoreBackup: vi.fn(async (_backup: VaultBackup, _password: string, _now: Date) => 0),
    pendingOnDevice: vi.fn(async () => devicePending ?? (current.status !== 'signedOut' && current.pendingSync)),
    onRemoteChange: (listener: () => void) => {
      remoteListeners.add(listener)
      return () => remoteListeners.delete(listener)
    },
    onSyncChange: (listener: (error: unknown) => void) => {
      syncListeners.add(listener)
      return () => syncListeners.delete(listener)
    },
  } satisfies SessionPort

  return { session, control }
}
