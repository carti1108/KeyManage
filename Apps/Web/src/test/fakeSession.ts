// 화면 테스트용 가짜 세션. 상태를 직접 바꾸고 실제 세션처럼 잠금·잠금 해제 알림을 보낸다
import { vi } from 'vitest'
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

  const control = {
    /** 진행 중인 로그아웃의 서버 요청을 끝낸다 */
    finishLogout: () => {},
    openVault(email = 'me@example.com', vault: SyncedVault = fakeVault()) {
      current = { status: 'unlocked', email, vault }
      for (const listener of unlockListeners) listener()
    },
    closeVault() {
      if (current.status !== 'unlocked') return
      current = { status: 'locked', email: current.email }
      for (const listener of lockListeners) listener()
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
  } satisfies SessionPort

  return { session, control }
}
