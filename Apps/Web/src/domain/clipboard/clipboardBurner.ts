// 클립보드 자동 소각 (COM-001). 복사하고 30초 뒤, 또는 잠기면 바로 빈 값으로 덮어쓴다
// 웹은 탭이 활성일 때만 쓸 수 있어 비우기가 실패할 수 있다. 실패하면 기다렸다가 retry()로 다시 비운다
import type { Scheduler } from '../lock/autoLock'

export const BURN_AFTER_MS = 30_000

export interface ClipboardWriter {
  write(text: string): Promise<void>
}

export type BurnState =
  | { readonly status: 'idle' }
  /** expiresAt: 비울 시각(now 기준 밀리초) */
  | { readonly status: 'counting'; readonly expiresAt: number }
  /** 비우지 못해 다음 기회(탭으로 돌아옴)를 기다림 */
  | { readonly status: 'pendingBurn' }

export interface ClipboardBurnerDeps {
  readonly clipboard: ClipboardWriter
  readonly scheduler: Scheduler
  readonly now: () => number
}

const IDLE: BurnState = { status: 'idle' }

export function createClipboardBurner({ clipboard, scheduler, now }: ClipboardBurnerDeps) {
  let current: BurnState = IDLE
  let cancelTimer: (() => void) | null = null
  // 복사할 때마다 늘린다. 늦게 끝난 비우기가 그 뒤에 복사한 새 값의 상태를 덮어쓰지 않게 한다
  let generation = 0
  const listeners = new Set<() => void>()

  function set(next: BurnState) {
    current = next
    for (const listener of listeners) listener()
  }

  function stopTimer() {
    cancelTimer?.()
    cancelTimer = null
  }

  async function burn(): Promise<void> {
    stopTimer()
    const startedAt = generation
    try {
      await clipboard.write('')
      if (startedAt === generation) set(IDLE)
    } catch {
      if (startedAt === generation) set({ status: 'pendingBurn' })
    }
  }

  return {
    state: (): BurnState => current,

    /** @throws 클립보드에 쓰지 못했을 때. 이때는 타이머를 시작하지 않는다 */
    async copy(text: string): Promise<void> {
      await clipboard.write(text)
      generation += 1
      stopTimer()
      const expiresAt = now() + BURN_AFTER_MS
      cancelTimer = scheduler.schedule(BURN_AFTER_MS, () => void burn())
      set({ status: 'counting', expiresAt })
    },

    /** 탭으로 돌아왔을 때 부른다. 비우지 못한 것이 있으면 다시 비운다 */
    async retry(): Promise<void> {
      if (current.status === 'pendingBurn') await burn()
    },

    /** 잠길 때 부른다. 30초를 기다리지 않고 바로 비운다 */
    async clear(): Promise<void> {
      if (current.status !== 'idle') await burn()
    },

    subscribe(listener: () => void): () => void {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
  }
}

export type ClipboardBurner = ReturnType<typeof createClipboardBurner>
