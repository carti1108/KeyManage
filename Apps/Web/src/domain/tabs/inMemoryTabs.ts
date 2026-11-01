// 메모리 안에서 탭 간 잠금·알림을 흉내 낸다 (ADR-WEB-001)
// 테스트에서 여러 탭을 흉내 낼 때와, 탭 간 기능이 없는 브라우저에서 탭 하나 전제로 돌 때 쓴다
import type { TabChannel, TabLock, TabLockName, TabMessageKind, Tabs } from './tabs'

function startNow<T>(fn: () => Promise<T>): Promise<T> {
  try {
    return fn()
  } catch (error) {
    return Promise.reject(error)
  }
}

/** 이름마다 한 번에 하나씩 실행하는 잠금. 같은 잠금을 쓰는 모든 탭에 걸린다 */
function createSerialLock(): TabLock {
  const tails = new Map<TabLockName, Promise<unknown>>()
  const waiting = new Map<TabLockName, number>()

  return {
    async run<T>(name: TabLockName, fn: () => Promise<T>, options: { ifAvailable?: boolean } = {}): Promise<T | null> {
      const count = waiting.get(name) ?? 0
      if (options.ifAvailable && count > 0) return null
      waiting.set(name, count + 1)
      // 기다리는 실행이 없으면 바로 시작한다. 탭 하나 전제였던 기존 동작과 같은 시점에 실행된다
      const previous = tails.get(name)
      const result = count === 0 || previous === undefined ? startNow(fn) : previous.then(fn)
      // 실패해도 다음 실행이 잠금을 얻게 한다
      tails.set(
        name,
        result.catch(() => undefined),
      )
      try {
        return await result
      } finally {
        waiting.set(name, (waiting.get(name) ?? 1) - 1)
      }
    },
  }
}

/** 같은 허브에 붙은 Tabs끼리 잠금·메시지를 공유한다 */
export function createInMemoryTabHub(): { connect(): Tabs } {
  const lock = createSerialLock()
  const subscribers = new Set<{ readonly owner: number; readonly listener: (kind: TabMessageKind) => void }>()
  let nextId = 0

  return {
    connect(): Tabs {
      const owner = nextId++
      const channel: TabChannel = {
        post(kind) {
          for (const subscriber of [...subscribers]) if (subscriber.owner !== owner) subscriber.listener(kind)
        },
        subscribe(listener) {
          const subscriber = { owner, listener }
          subscribers.add(subscriber)
          return () => subscribers.delete(subscriber)
        },
      }
      return { lock, channel }
    },
  }
}

/** 탭 안 직렬화 잠금과 아무것도 보내지 않는 채널. 탭 하나 전제의 기존 동작이다 */
export function createSingleTabFallback(): Tabs {
  return {
    lock: createSerialLock(),
    channel: {
      post() {
        // 다른 탭에 알릴 수단이 없다
      },
      subscribe() {
        return () => undefined
      },
    },
  }
}
