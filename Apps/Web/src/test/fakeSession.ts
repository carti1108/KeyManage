// 화면 테스트용 가짜 세션. 상태를 직접 바꾸고 실제 세션처럼 잠금·잠금 해제 알림을 보낸다
import { vi } from 'vitest'
import type { SessionState } from '../domain/session/vaultSession'
import { emptyVault } from '../domain/vault/vaultData'
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
    lock: vi.fn(() => control.closeVault()),
    logout: vi.fn(async () => control.signOut()),
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
