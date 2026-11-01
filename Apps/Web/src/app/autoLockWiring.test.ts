import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createInMemoryTabHub } from '../domain/tabs/inMemoryTabs'
import type { TabMessageKind } from '../domain/tabs/tabs'
import { ACTIVITY_BROADCAST_INTERVAL_MS, startAutoLock, type LockableSession } from './autoLockWiring'

const MINUTE = 60_000

function fakeSession() {
  const unlockListeners = new Set<() => void>()
  const lockListeners = new Set<() => void>()
  const session: LockableSession & { locks: number; scopes: (string | undefined)[]; unlocked: boolean; unlockNow(): void } = {
    locks: 0,
    scopes: [],
    unlocked: false,
    state() {
      return this.unlocked ? ({ status: 'unlocked' } as ReturnType<LockableSession['state']>) : { status: 'signedOut' }
    },
    lock(scope) {
      this.locks += 1
      this.scopes.push(scope)
      this.unlocked = false
      for (const l of lockListeners) l()
    },
    onUnlock: (l) => (unlockListeners.add(l), () => unlockListeners.delete(l)),
    onLock: (l) => (lockListeners.add(l), () => lockListeners.delete(l)),
    unlockNow() {
      this.unlocked = true
      unlockListeners.forEach((l) => l())
    },
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

function setVisibility(state: 'hidden' | 'visible') {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state })
  document.dispatchEvent(new Event('visibilitychange'))
}

/** 자동 잠금을 연결한 탭과, 그 탭이 보낸 메시지를 지켜보는 다른 탭 */
function wiredTabs(timeoutMs = 5 * MINUTE) {
  const hub = createInMemoryTabHub()
  const session = fakeSession()
  const stop = startAutoLock(session, timeoutMs, hub.connect())
  const other = hub.connect()
  const received: TabMessageKind[] = []
  other.channel.subscribe((kind) => received.push(kind))
  return { session, stop, other, received, hub }
}

describe('여러 탭의 자동 잠금 (SEC-003, ADR-WEB-001)', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => {
    setVisibility('visible')
    vi.useRealTimers()
  })

  it('보이는 탭의 타이머가 끝나면 모든 탭을 잠근다', () => {
    const { session, stop } = wiredTabs()
    session.unlockNow()

    vi.advanceTimersByTime(5 * MINUTE)

    expect(session.scopes).toEqual(['allTabs'])
    stop()
  })

  it('"즉시" 설정에서 숨겨져 잠그면 이 탭만 잠근다', () => {
    const { session, stop } = wiredTabs(0)
    session.unlockNow()

    setVisibility('hidden')

    expect(session.scopes).toEqual(['thisTab'])
    stop()
  })

  it('동결·떠남으로 잠그면 이 탭만 잠근다', () => {
    const { session, stop } = wiredTabs()
    session.unlockNow()

    window.dispatchEvent(new Event('pagehide'))

    expect(session.scopes).toEqual(['thisTab'])
    stop()
  })

  it('조작은 1초에 한 번까지만 다른 탭에 알린다', () => {
    const { session, stop, received } = wiredTabs()
    session.unlockNow()

    window.dispatchEvent(new Event('keydown'))
    window.dispatchEvent(new Event('keydown'))
    vi.advanceTimersByTime(ACTIVITY_BROADCAST_INTERVAL_MS - 1)
    window.dispatchEvent(new Event('keydown'))
    vi.advanceTimersByTime(1)
    window.dispatchEvent(new Event('keydown'))

    expect(received).toEqual(['activity', 'activity'])
    stop()
  })

  it('다른 탭의 조작을 받으면 타이머를 다시 재고, 다시 알리지 않는다', () => {
    const { session, stop, other, hub } = wiredTabs()
    const third: TabMessageKind[] = []
    hub.connect().channel.subscribe((kind) => third.push(kind))
    session.unlockNow()

    vi.advanceTimersByTime(4 * MINUTE)
    other.channel.post('activity')
    vi.advanceTimersByTime(4 * MINUTE)

    expect(session.locks).toBe(0)
    expect(third).toEqual(['activity'])
    stop()
  })

  it('잠긴 탭은 조작을 알리지 않는다', () => {
    const { stop, received } = wiredTabs()

    window.dispatchEvent(new Event('keydown'))

    expect(received).toEqual([])
    stop()
  })
})
