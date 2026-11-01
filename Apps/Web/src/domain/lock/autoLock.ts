// 자동 잠금 타이머 (SEC-003). 마지막 조작부터 설정 시간이 지나면 onTimeout을 한 번 부른다
// 브라우저는 백그라운드 탭의 타이머를 늦추므로, 타이머만 믿지 않고 다시 보이거나 조작할 때 경과 시간으로도 판정한다

const MINUTE_MS = 60_000

export const AUTO_LOCK_OPTIONS_MS = {
  immediately: 0,
  oneMinute: MINUTE_MS,
  fiveMinutes: 5 * MINUTE_MS,
  fifteenMinutes: 15 * MINUTE_MS,
  oneHour: 60 * MINUTE_MS,
} as const

export const DEFAULT_AUTO_LOCK_MS = AUTO_LOCK_OPTIONS_MS.fiveMinutes

const ALLOWED_TIMEOUTS: readonly number[] = Object.values(AUTO_LOCK_OPTIONS_MS)

/** 정해진 선택지가 아닌 자동 잠금 시간. "잠그지 않음"은 없다 (SEC-003) */
export class InvalidAutoLockTimeoutError extends Error {
  constructor(timeoutMs: number) {
    super(`자동 잠금 시간은 정해진 선택지 중 하나여야 함: ${timeoutMs}`)
    this.name = 'InvalidAutoLockTimeoutError'
  }
}

export interface Scheduler {
  /** @returns 예약 취소 함수 */
  schedule(delayMs: number, run: () => void): () => void
}

/**
 * 잠금 원인. 보이는 탭에서 타이머가 끝난 timer만 다른 탭에도 퍼뜨린다 (SEC-003, ADR-WEB-001).
 * hidden: "즉시" 설정에서 숨겨짐, suspended: 동결·떠남, expired: 다시 보이거나 조작할 때 발견한 만료(이 탭의 조작 시각이 낡았을 수 있음)
 */
export type LockCause = 'timer' | 'hidden' | 'suspended' | 'expired'

export interface AutoLockDeps {
  readonly timeoutMs: number
  readonly now: () => number
  readonly scheduler: Scheduler
  readonly onTimeout: (cause: LockCause) => void
}

export interface AutoLock {
  /** 잠금 해제 뒤 부른다. 지금부터 시간을 잰다 */
  start(): void
  stop(): void
  /** 사용자 조작 */
  activity(): void
  /** 백그라운드로 감 */
  hidden(): void
  /** 다시 보임 */
  visible(): void
  /** 탭 동결·페이지 떠남. 타이머가 멈출 수 있으므로 바로 잠근다 */
  suspended(): void
}

/** @throws InvalidAutoLockTimeoutError */
export function createAutoLock({ timeoutMs, now, scheduler, onTimeout }: AutoLockDeps): AutoLock {
  if (!ALLOWED_TIMEOUTS.includes(timeoutMs)) throw new InvalidAutoLockTimeoutError(timeoutMs)
  const immediate = timeoutMs === AUTO_LOCK_OPTIONS_MS.immediately

  let running = false
  let lastActivity = 0
  let cancel: (() => void) | null = null

  function clearTimer() {
    cancel?.()
    cancel = null
  }

  function fire(cause: LockCause) {
    running = false
    clearTimer()
    onTimeout(cause)
  }

  function restartTimer() {
    clearTimer()
    lastActivity = now()
    // "즉시"는 보이는 동안 잠그지 않고 백그라운드로 갈 때만 잠근다
    if (!immediate) cancel = scheduler.schedule(timeoutMs, () => fire('timer'))
  }

  const expired = () => !immediate && now() - lastActivity >= timeoutMs

  return {
    start() {
      running = true
      restartTimer()
    },
    stop() {
      running = false
      clearTimer()
    },
    activity() {
      if (!running) return
      // 시간이 지난 뒤의 첫 조작이 잠금을 미루지 않게 먼저 확인한다
      if (expired()) fire('expired')
      else restartTimer()
    },
    hidden() {
      if (running && immediate) fire('hidden')
    },
    visible() {
      if (running && expired()) fire('expired')
    },
    suspended() {
      if (running) fire('suspended')
    },
  }
}
