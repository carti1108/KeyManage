import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AUTO_LOCK_STORAGE_KEY, createLocalStorageAutoLockSetting } from '../adapters/browser/localStorageAutoLockSetting'
import { AUTO_LOCK_OPTIONS_MS, DEFAULT_AUTO_LOCK_MS } from '../domain/lock/autoLock'
import type { AutoLockSettingStore } from '../domain/lock/autoLockSetting'
import { createInMemoryTabHub, createSingleTabFallback } from '../domain/tabs/inMemoryTabs'
import type { TabMessageKind, Tabs } from '../domain/tabs/tabs'
import { ACTIVITY_BROADCAST_INTERVAL_MS, type LockableSession } from './autoLockWiring'
import { useAutoLockSetting } from './useAutoLockSetting'

const MINUTE = 60_000

function fakeSession() {
  const unlockListeners = new Set<() => void>()
  const lockListeners = new Set<() => void>()
  const session: LockableSession & { locks: number; scopes: (string | undefined)[]; status: 'unlocked' | 'signedOut'; unlockNow(): void; logout(): void } = {
    locks: 0,
    scopes: [],
    status: 'signedOut',
    state() {
      return this.status === 'unlocked' ? ({ status: 'unlocked' } as ReturnType<LockableSession['state']>) : { status: 'signedOut' }
    },
    lock(scope) {
      this.locks += 1
      this.scopes.push(scope)
      this.status = 'signedOut'
      for (const l of lockListeners) l()
    },
    onUnlock: (l) => (unlockListeners.add(l), () => unlockListeners.delete(l)),
    onLock: (l) => (lockListeners.add(l), () => lockListeners.delete(l)),
    unlockNow() {
      this.status = 'unlocked'
      unlockListeners.forEach((l) => l())
    },
    // 실제 세션처럼 로그아웃도 잠금 알림을 보낸다
    logout() {
      this.status = 'signedOut'
      lockListeners.forEach((l) => l())
    },
  }
  return session
}

function memoryStorage(initial: Record<string, string> = {}) {
  const values = new Map(Object.entries(initial))
  return { values, getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => void values.set(key, value) }
}

function setup(
  initial: Record<string, string> = {},
  { tabs = createSingleTabFallback(), wrap = (s: AutoLockSettingStore) => s }: { tabs?: Tabs; wrap?: (setting: AutoLockSettingStore) => AutoLockSettingStore } = {},
) {
  const storage = memoryStorage(initial)
  const session = fakeSession()
  const setting = wrap(createLocalStorageAutoLockSetting(() => storage))
  const hook = renderHook(() => useAutoLockSetting(session, tabs, setting))
  return { storage, session, hook }
}

describe('자동 잠금 시간 설정과 타이머 연결 (SEC-003, ADR-WEB-002)', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('저장값이 없으면 기본 5분으로 잰다', () => {
    const { session, hook } = setup()
    expect(hook.result.current.timeoutMs).toBe(DEFAULT_AUTO_LOCK_MS)

    act(() => session.unlockNow())
    vi.advanceTimersByTime(5 * MINUTE - 1)
    expect(session.locks).toBe(0)
    vi.advanceTimersByTime(1)
    expect(session.locks).toBe(1)
    hook.unmount()
  })

  it('저장한 시간이 재시작 뒤에도 쓰인다', () => {
    const { session, hook } = setup({ [AUTO_LOCK_STORAGE_KEY]: String(AUTO_LOCK_OPTIONS_MS.oneMinute) })
    expect(hook.result.current.timeoutMs).toBe(AUTO_LOCK_OPTIONS_MS.oneMinute)

    act(() => session.unlockNow())
    vi.advanceTimersByTime(MINUTE)
    expect(session.locks).toBe(1)
    hook.unmount()
  })

  it('바꾸면 저장하고 그 순간부터 새 시간으로 다시 잰다', () => {
    const { storage, session, hook } = setup()
    act(() => session.unlockNow())
    vi.advanceTimersByTime(4 * MINUTE)

    act(() => hook.result.current.change(AUTO_LOCK_OPTIONS_MS.oneMinute))

    // 이미 1분이 넘게 지났어도 바꾼 순간부터 잰다
    expect(session.locks).toBe(0)
    expect(storage.values.get(AUTO_LOCK_STORAGE_KEY)).toBe('60000')
    expect(hook.result.current.timeoutMs).toBe(AUTO_LOCK_OPTIONS_MS.oneMinute)
    vi.advanceTimersByTime(MINUTE - 1)
    expect(session.locks).toBe(0)
    vi.advanceTimersByTime(1)
    expect(session.locks).toBe(1)
    hook.unmount()
  })

  it('보이는 탭에서 "즉시"로 바꿔도 그 자리에서 잠그지 않는다', () => {
    const { session, hook } = setup()
    act(() => session.unlockNow())

    act(() => hook.result.current.change(AUTO_LOCK_OPTIONS_MS.immediately))
    vi.advanceTimersByTime(60 * MINUTE)

    expect(session.locks).toBe(0)
    hook.unmount()
  })

  it('다른 탭이 바꾸면 받은 순간부터 새 시간으로 다시 잰다', () => {
    const { session, hook } = setup()
    act(() => session.unlockNow())
    vi.advanceTimersByTime(4 * MINUTE)

    act(() => {
      window.dispatchEvent(new StorageEvent('storage', { key: AUTO_LOCK_STORAGE_KEY, newValue: '900000' }))
    })

    expect(hook.result.current.timeoutMs).toBe(AUTO_LOCK_OPTIONS_MS.fifteenMinutes)
    vi.advanceTimersByTime(15 * MINUTE - 1)
    expect(session.locks).toBe(0)
    vi.advanceTimersByTime(1)
    expect(session.locks).toBe(1)
    hook.unmount()
  })

  it('로그아웃해도 설정은 남고 다음 잠금 해제에도 쓰인다', () => {
    const { storage, session, hook } = setup()
    act(() => session.unlockNow())
    act(() => hook.result.current.change(AUTO_LOCK_OPTIONS_MS.oneMinute))

    act(() => session.logout())

    expect(storage.values.get(AUTO_LOCK_STORAGE_KEY)).toBe('60000')
    expect(hook.result.current.timeoutMs).toBe(AUTO_LOCK_OPTIONS_MS.oneMinute)
    act(() => session.unlockNow())
    vi.advanceTimersByTime(MINUTE)
    expect(session.locks).toBe(1)
    hook.unmount()
  })

  it('해제하면 다른 탭의 변경을 더 받지 않는다', () => {
    const { hook } = setup()
    const before = hook.result.current.timeoutMs
    hook.unmount()

    window.dispatchEvent(new StorageEvent('storage', { key: AUTO_LOCK_STORAGE_KEY, newValue: '60000' }))

    expect(hook.result.current.timeoutMs).toBe(before)
  })

  it('숨겨진 탭이 다른 탭에서 "즉시"를 받으면 그 탭만 바로 잠근다', () => {
    const { session, hook } = setup()
    act(() => session.unlockNow())
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden')

    act(() => {
      window.dispatchEvent(new StorageEvent('storage', { key: AUTO_LOCK_STORAGE_KEY, newValue: '0' }))
    })

    expect(session.scopes).toEqual(['thisTab'])
    visibility.mockRestore()
    hook.unmount()
  })

  describe('다시 연결할 때 이전 연결을 남기지 않는다', () => {
    // 지금 붙어 있는 구독 수를 센다
    function countingTabs(tabs: Tabs) {
      let active = 0
      const counted: Tabs = {
        lock: tabs.lock,
        channel: {
          post: (kind) => tabs.channel.post(kind),
          subscribe(listener) {
            active += 1
            const off = tabs.channel.subscribe(listener)
            return () => {
              active -= 1
              off()
            }
          },
        },
      }
      return { tabs: counted, active: () => active }
    }

    function changeThreeTimes(change: (ms: number) => void) {
      act(() => change(AUTO_LOCK_OPTIONS_MS.oneMinute))
      act(() => change(AUTO_LOCK_OPTIONS_MS.oneHour))
      act(() => change(AUTO_LOCK_OPTIONS_MS.fifteenMinutes))
    }

    it('여러 번 바꾼 뒤 조작 한 번은 다른 탭에 한 번만 알린다', () => {
      const hub = createInMemoryTabHub()
      const other = hub.connect()
      const received: TabMessageKind[] = []
      other.channel.subscribe((kind) => received.push(kind))
      const { session, hook } = setup({}, { tabs: hub.connect() })
      act(() => session.unlockNow())
      changeThreeTimes(hook.result.current.change)
      vi.advanceTimersByTime(ACTIVITY_BROADCAST_INTERVAL_MS)
      received.length = 0

      window.dispatchEvent(new Event('pointerdown'))

      expect(received).toEqual(['activity'])
      hook.unmount()
    })

    it('여러 번 바꿔도 다른 탭의 조작은 마지막 연결 하나만 받는다', () => {
      const hub = createInMemoryTabHub()
      const other = hub.connect()
      const counted = countingTabs(hub.connect())
      const { session, hook } = setup({}, { tabs: counted.tabs })
      act(() => session.unlockNow())
      changeThreeTimes(hook.result.current.change)

      expect(counted.active()).toBe(1)
      // 이전 1분 연결이 남아 있으면 이 조작으로 1분 타이머가 다시 걸린다
      vi.advanceTimersByTime(10 * MINUTE)
      other.channel.post('activity')
      vi.advanceTimersByTime(15 * MINUTE - 1)
      expect(session.locks).toBe(0)
      vi.advanceTimersByTime(1)
      expect(session.locks).toBe(1)
      hook.unmount()
      expect(counted.active()).toBe(0)
    })

    it('잠긴 동안 바꾸면 타이머를 시작하지 않고, 다음 잠금 해제부터 새 시간으로 잰다', () => {
      const { session, hook } = setup()

      changeThreeTimes(hook.result.current.change)
      vi.advanceTimersByTime(2 * 60 * MINUTE)
      expect(session.locks).toBe(0)

      act(() => session.unlockNow())
      vi.advanceTimersByTime(15 * MINUTE)
      expect(session.locks).toBe(1)
      hook.unmount()
    })
  })

  it('해제하면 저장소 구독도 해제한다', () => {
    const offs: ReturnType<typeof vi.fn>[] = []
    const wrap = (setting: AutoLockSettingStore): AutoLockSettingStore => ({
      ...setting,
      subscribe(listener) {
        const off = vi.fn(setting.subscribe(listener))
        offs.push(off)
        return off
      },
    })
    const { hook } = setup({}, { wrap })
    expect(offs).not.toHaveLength(0)

    hook.unmount()

    for (const off of offs) expect(off).toHaveBeenCalledTimes(1)
  })

  it('처음 읽은 뒤 구독하기 전에 다른 탭이 바꾼 값도 반영한다', () => {
    const storage = memoryStorage()
    // 첫 렌더의 read와 구독 사이에 다른 탭이 값을 바꾼 상황을 흉내 낸다
    const wrap = (setting: AutoLockSettingStore): AutoLockSettingStore => ({
      ...setting,
      read: () => createLocalStorageAutoLockSetting(() => storage).read(),
      subscribe(listener) {
        storage.setItem(AUTO_LOCK_STORAGE_KEY, String(AUTO_LOCK_OPTIONS_MS.oneMinute))
        return setting.subscribe(listener)
      },
    })
    const { hook } = setup({}, { wrap })

    expect(hook.result.current.timeoutMs).toBe(AUTO_LOCK_OPTIONS_MS.oneMinute)
    hook.unmount()
  })
})
