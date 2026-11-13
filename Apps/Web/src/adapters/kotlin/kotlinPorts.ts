// 웹 포트(DeviceStore, Tabs, TabMemory)를 Kotlin이 받는 Js 포트(JSON 문자열·원시 값)로 바꾼다
// 포트 구현(IndexedDB, navigator.locks, BroadcastChannel, sessionStorage)은 그대로 쓴다
import { DeviceStorageError, parseDeviceRecord, type DeviceStore } from '../../domain/device/deviceRecord'
import type { RememberedKdf, TabMemory } from '../../domain/device/tabMemory'
import { isTabMessageKind, type TabLockName, type Tabs } from '../../domain/tabs/tabs'
import type { JsDeviceStore, JsTabMemory, JsTabs } from './kotlinTypes'

function recordOf(json: string): ReturnType<typeof parseDeviceRecord> {
  try {
    return parseDeviceRecord(JSON.parse(json))
  } catch {
    return null
  }
}

/** 읽은 값은 믿지 않는 쪽이 Kotlin이므로 기록 객체를 그대로 JSON으로 넘긴다. 쓸 때는 웹 규격(parseDeviceRecord)을 한 번 더 확인한다 */
export function toJsDeviceStore(store: DeviceStore): JsDeviceStore {
  return {
    async load() {
      const value = await store.load()
      return value === null || value === undefined ? null : JSON.stringify(value)
    },
    async save(recordJson) {
      const record = recordOf(recordJson)
      if (record === null) throw new DeviceStorageError(new TypeError('Kotlin이 넘긴 기기 기록이 규격에 맞지 않음'))
      await store.save(record)
    },
    clear: () => store.clear(),
  }
}

function isLockName(name: string): name is TabLockName {
  return name === 'device' || name === 'sync'
}

export function toJsTabs(tabs: Tabs): JsTabs {
  return {
    lockRun(name, ifAvailable, fn) {
      if (!isLockName(name)) throw new TypeError(`모르는 탭 잠금 이름: ${String(name)}`)
      return tabs.lock.run(name, fn, { ifAvailable })
    },
    post(kind) {
      if (!isTabMessageKind(kind)) throw new TypeError(`모르는 탭 알림 종류: ${String(kind)}`)
      tabs.channel.post(kind)
    },
    subscribe(listener) {
      const unsubscribe = tabs.channel.subscribe((kind) => listener(kind))
      return () => void unsubscribe()
    },
  }
}

function rememberedOf(json: string): RememberedKdf | null {
  let value: unknown
  try {
    value = JSON.parse(json)
  } catch {
    return null
  }
  if (typeof value !== 'object' || value === null) return null
  const { email, salt, iterations } = value as Record<string, unknown>
  if (typeof email !== 'string' || typeof salt !== 'string' || !Number.isSafeInteger(iterations)) return null
  return { email, salt, iterations: iterations as number }
}

export function toJsTabMemory(memory: TabMemory): JsTabMemory {
  return {
    get() {
      const remembered = memory.get()
      return remembered === null ? null : JSON.stringify({ email: remembered.email, salt: remembered.salt, iterations: remembered.iterations })
    },
    set(json) {
      const remembered = rememberedOf(json)
      if (remembered === null) throw new TypeError('Kotlin이 넘긴 탭 기억이 {email, salt, iterations} 규격에 맞지 않음')
      memory.set(remembered)
    },
    clear: () => memory.clear(),
  }
}
