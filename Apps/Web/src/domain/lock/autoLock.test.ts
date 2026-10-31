import { describe, expect, it } from 'vitest'
import { AUTO_LOCK_OPTIONS_MS, DEFAULT_AUTO_LOCK_MS, InvalidAutoLockTimeoutError, createAutoLock, type Scheduler } from './autoLock'

const MINUTE = 60_000

// 시간을 직접 움직이는 시계와 타이머. 브라우저처럼 백그라운드에서 타이머가 늦게 도는 상황도 흉내 낸다
function fakeTime() {
  let now = 0
  let pending: { at: number; run: () => void } | null = null
  const scheduler: Scheduler = {
    schedule(ms, run) {
      const timer = { at: now + ms, run }
      pending = timer
      return () => {
        if (pending === timer) pending = null
      }
    },
  }
  return {
    now: () => now,
    scheduler,
    /** 시간을 흘리고, 그 사이 예정된 타이머가 있으면 실행한다 */
    advance(ms: number) {
      now += ms
      if (pending && pending.at <= now) {
        const { run } = pending
        pending = null
        run()
      }
    },
    /** 타이머를 실행하지 않고 시간만 흘린다 (백그라운드 탭의 타이머 지연) */
    advanceWithoutTimers(ms: number) {
      now += ms
    },
  }
}

function setup(timeoutMs = DEFAULT_AUTO_LOCK_MS) {
  const time = fakeTime()
  let locks = 0
  const autoLock = createAutoLock({ timeoutMs, now: time.now, scheduler: time.scheduler, onTimeout: () => locks++ })
  autoLock.start()
  return { time, autoLock, locks: () => locks }
}

describe('자동 잠금 (SEC-003)', () => {
  it('선택지는 즉시·1·5·15·60분이고 기본은 5분이다', () => {
    expect(Object.values(AUTO_LOCK_OPTIONS_MS)).toEqual([0, MINUTE, 5 * MINUTE, 15 * MINUTE, 60 * MINUTE])
    expect(DEFAULT_AUTO_LOCK_MS).toBe(5 * MINUTE)
  })

  it('정해진 선택지가 아닌 시간은 받지 않는다 ("잠그지 않음" 없음)', () => {
    for (const timeoutMs of [-1, 30_000, Infinity, Number.NaN]) {
      expect(() => createAutoLock({ timeoutMs, now: () => 0, scheduler: fakeTime().scheduler, onTimeout: () => {} })).toThrow(
        InvalidAutoLockTimeoutError,
      )
    }
  })

  it('마지막 조작부터 설정 시간이 지나면 한 번 잠근다', () => {
    const { time, locks } = setup()

    time.advance(5 * MINUTE - 1)
    expect(locks()).toBe(0)
    time.advance(1)
    expect(locks()).toBe(1)
    time.advance(10 * MINUTE)
    expect(locks()).toBe(1)
  })

  it('조작하면 시간을 다시 잰다', () => {
    const { time, autoLock, locks } = setup()

    time.advance(4 * MINUTE)
    autoLock.activity()
    time.advance(4 * MINUTE)

    expect(locks()).toBe(0)
    time.advance(1 * MINUTE)
    expect(locks()).toBe(1)
  })

  it('백그라운드에서 타이머가 늦게 돌아도 다시 보이거나 조작할 때 경과 시간으로 잠근다', () => {
    const shown = setup()
    shown.autoLock.hidden()
    shown.time.advanceWithoutTimers(6 * MINUTE)
    shown.autoLock.visible()
    expect(shown.locks()).toBe(1)

    // 시간이 지난 뒤의 첫 조작은 잠금을 미루지 않고 잠근다
    const touched = setup()
    touched.time.advanceWithoutTimers(6 * MINUTE)
    touched.autoLock.activity()
    expect(touched.locks()).toBe(1)
  })

  it('"즉시"는 백그라운드로 가는 순간 잠그고, 보이는 동안에는 잠그지 않는다', () => {
    const { time, autoLock, locks } = setup(AUTO_LOCK_OPTIONS_MS.immediately)

    time.advance(60 * MINUTE)
    expect(locks()).toBe(0)
    autoLock.hidden()
    expect(locks()).toBe(1)
  })

  it('잠근 뒤 다시 시작하면(잠금 해제) 그때부터 잰다', () => {
    const { time, autoLock, locks } = setup()
    time.advance(5 * MINUTE)

    autoLock.start()
    time.advance(5 * MINUTE - 1)
    expect(locks()).toBe(1)
    time.advance(1)
    expect(locks()).toBe(2)
  })

  it('탭이 동결되거나 페이지를 떠나면 바로 잠근다 (타이머가 멈출 수 있으므로)', () => {
    const { autoLock, locks } = setup()

    autoLock.suspended()

    expect(locks()).toBe(1)
  })

  it('멈추면 잠그지 않는다', () => {
    const { time, autoLock, locks } = setup()

    autoLock.stop()
    time.advance(60 * MINUTE)
    autoLock.visible()
    autoLock.activity()

    expect(locks()).toBe(0)
  })
})
