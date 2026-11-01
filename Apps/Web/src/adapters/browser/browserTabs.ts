// navigator.locks와 BroadcastChannel로 탭 간 잠금·알림을 만든다 (ADR-WEB-001)
// 둘 중 하나라도 없는 브라우저는 탭 하나 전제의 기존 동작으로 대체한다
import { createSingleTabFallback } from '../../domain/tabs/inMemoryTabs'
import { isTabMessageKind, type TabChannel, type TabLock, type TabMessageKind, type Tabs } from '../../domain/tabs/tabs'

const LOCK_PREFIX = 'keymanage-'
const CHANNEL_NAME = 'keymanage'

export interface BrowserTabsEnv {
  readonly locks: LockManager | undefined
  readonly Channel: typeof BroadcastChannel | undefined
}

function defaultEnv(): BrowserTabsEnv {
  return {
    locks: typeof navigator === 'undefined' ? undefined : navigator.locks,
    Channel: typeof BroadcastChannel === 'undefined' ? undefined : BroadcastChannel,
  }
}

function lockFrom(locks: LockManager): TabLock {
  return {
    async run<T>(name: 'device' | 'sync', fn: () => Promise<T>, options: { ifAvailable?: boolean } = {}): Promise<T | null> {
      const ifAvailable = options.ifAvailable ?? false
      return locks.request(`${LOCK_PREFIX}${name}`, { ifAvailable }, async (lock) => (lock === null ? null : fn()))
    },
  }
}

// 다른 탭이 보낸 값은 믿지 않는다. 종류와 보낸 탭 id 모양만 맞으면 받는다
function parseMessage(data: unknown): { kind: TabMessageKind; from: string } | null {
  if (typeof data !== 'object' || data === null) return null
  const { kind, from } = data as Record<string, unknown>
  if (!isTabMessageKind(kind) || typeof from !== 'string') return null
  return { kind, from }
}

function channelFrom(Channel: typeof BroadcastChannel): TabChannel {
  const id = crypto.randomUUID()
  const channel = new Channel(CHANNEL_NAME)
  const listeners = new Set<(kind: TabMessageKind) => void>()
  channel.onmessage = (event: MessageEvent) => {
    const message = parseMessage(event.data)
    if (message === null || message.from === id) return
    for (const listener of [...listeners]) listener(message.kind)
  }
  return {
    post(kind) {
      channel.postMessage({ kind, from: id })
    },
    subscribe(listener) {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
  }
}

export function createBrowserTabs(env: BrowserTabsEnv = defaultEnv()): Tabs {
  if (env.locks === undefined || env.Channel === undefined) return createSingleTabFallback()
  return { lock: lockFrom(env.locks), channel: channelFrom(env.Channel) }
}
