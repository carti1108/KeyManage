import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { startAutoLock, type LockableSession } from './autoLockWiring'

const MINUTE = 60_000

function fakeSession() {
  const unlockListeners = new Set<() => void>()
  const lockListeners = new Set<() => void>()
  const session: LockableSession & { locks: number; unlocked: boolean; unlockNow(): void } = {
    locks: 0,
    unlocked: false,
    state() {
      return this.unlocked ? ({ status: 'unlocked' } as ReturnType<LockableSession['state']>) : { status: 'signedOut' }
    },
    lock() {
      this.locks += 1
      for (const l of lockListeners) l()
    },
    onUnlock: (l) => (unlockListeners.add(l), () => unlockListeners.delete(l)),
    onLock: (l) => (lockListeners.add(l), () => lockListeners.delete(l)),
    unlockNow: () => unlockListeners.forEach((l) => l()),
  }
  return session
}

describe('세션과 자동 잠금 연결 (SEC-003)', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('볼트가 열리면 타이머를 시작하고, 기본 5분 동안 조작이 없으면 잠근다', () => {
    const session = fakeSession()
    const stop = startAutoLock(session)

    session.unlockNow()
    vi.advanceTimersByTime(5 * MINUTE - 1)
    expect(session.locks).toBe(0)
    vi.advanceTimersByTime(1)
    expect(session.locks).toBe(1)
    stop()
  })

  it('조작하면 다시 재고, 잠긴 동안에는 타이머가 돌지 않는다', () => {
    const session = fakeSession()
    const stop = startAutoLock(session, MINUTE)

    session.unlockNow()
    vi.advanceTimersByTime(MINUTE - 1)
    window.dispatchEvent(new Event('keydown'))
    vi.advanceTimersByTime(MINUTE - 1)
    expect(session.locks).toBe(0)

    session.lock()
    vi.advanceTimersByTime(10 * MINUTE)
    expect(session.locks).toBe(1)
    stop()
  })

  it('연결하는 시점에 이미 열려 있으면 바로 타이머를 시작한다', () => {
    const session = fakeSession()
    session.unlocked = true
    const stop = startAutoLock(session)

    vi.advanceTimersByTime(5 * MINUTE)

    expect(session.locks).toBe(1)
    stop()
  })

  it('연결을 끊으면 잠그지 않는다', () => {
    const session = fakeSession()
    startAutoLock(session)()

    session.unlockNow()
    vi.advanceTimersByTime(60 * MINUTE)

    expect(session.locks).toBe(0)
  })
})
