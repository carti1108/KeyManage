import { describe, expect, it, vi } from 'vitest'
import { DeviceStorageError, type DeviceRecord, type DeviceStore } from '../../domain/device/deviceRecord'
import type { TabMemory } from '../../domain/device/tabMemory'
import { createSingleTabFallback } from '../../domain/tabs/inMemoryTabs'
import type { TabMessageKind, Tabs } from '../../domain/tabs/tabs'
import { toJsDeviceStore, toJsTabMemory, toJsTabs } from './kotlinPorts'

const RECORD: DeviceRecord = {
  version: 1,
  email: 'user@example.com',
  kdf: { salt: 'AAAAAAAAAAAAAAAAAAAAAA==', iterations: 600_000 },
  payload: { version: 1, cipher: 'AES-256-GCM', iv: 'aXY=', ciphertext: 'Y2lwaGVy', tag: 'dGFn' },
  revision: 4,
  lastSeenRevision: 4,
  pendingSync: false,
}

function fakeDeviceStore(stored: unknown = undefined) {
  const store = {
    load: vi.fn(async (): Promise<unknown> => stored),
    save: vi.fn(async (_record: DeviceRecord) => undefined),
    clear: vi.fn(async () => undefined),
  }
  return store satisfies DeviceStore
}

describe('DeviceStore를 JsDeviceStore로', () => {
  it('저장된 기록 객체를 JSON 문자열로 넘긴다', async () => {
    const js = toJsDeviceStore(fakeDeviceStore(RECORD))

    const json = await js.load()

    expect(JSON.parse(json ?? 'null')).toEqual(RECORD)
  })

  it('기록이 없으면(undefined, null) null을 넘긴다', async () => {
    expect(await toJsDeviceStore(fakeDeviceStore(undefined)).load()).toBeNull()
    expect(await toJsDeviceStore(fakeDeviceStore(null)).load()).toBeNull()
  })

  it('JSON 문자열을 기록 객체로 바꿔 저장한다', async () => {
    const store = fakeDeviceStore()

    await toJsDeviceStore(store).save(JSON.stringify(RECORD))

    expect(store.save).toHaveBeenCalledWith(RECORD)
  })

  it('형식에 맞지 않는 기록은 저장하지 않고 DeviceStorageError로 거절한다', async () => {
    const store = fakeDeviceStore()
    const js = toJsDeviceStore(store)

    await expect(js.save('{깨짐')).rejects.toBeInstanceOf(DeviceStorageError)
    await expect(js.save(JSON.stringify({ ...RECORD, version: 99 }))).rejects.toBeInstanceOf(DeviceStorageError)
    expect(store.save).not.toHaveBeenCalled()
  })

  it('지우기를 그대로 넘기고, 저장소 실패는 reject로 그대로 전달한다', async () => {
    const failure = new DeviceStorageError(new Error('용량 부족'))
    const store = { ...fakeDeviceStore(), clear: vi.fn(async () => Promise.reject(failure)) }

    await expect(toJsDeviceStore(store).clear()).rejects.toBe(failure)
  })

  it('load와 save의 저장소 실패도 그대로 reject한다', async () => {
    const failure = new DeviceStorageError(new Error('열 수 없음'))
    const store = { load: vi.fn(async () => Promise.reject(failure)), save: vi.fn(async () => Promise.reject(failure)), clear: vi.fn(async () => undefined) }
    const js = toJsDeviceStore(store)

    await expect(js.load()).rejects.toBe(failure)
    await expect(js.save(JSON.stringify(RECORD))).rejects.toBe(failure)
  })
})

describe('Tabs를 JsTabs로', () => {
  it('lockRun은 이름과 ifAvailable을 넘기고 fn의 결과를 돌려준다', async () => {
    const tabs = createSingleTabFallback()
    const run = vi.spyOn(tabs.lock, 'run')

    const result = await toJsTabs(tabs).lockRun('device', false, async () => 42)

    expect(result).toBe(42)
    expect(run).toHaveBeenCalledWith('device', expect.any(Function), { ifAvailable: false })
  })

  it('ifAvailable인데 잠금을 못 얻으면 fn을 실행하지 않고 null을 돌려준다', async () => {
    const tabs = createSingleTabFallback()
    const js = toJsTabs(tabs)
    let release: () => void = () => undefined
    const holding = js.lockRun('sync', false, () => new Promise<void>((resolve) => (release = resolve)))
    const fn = vi.fn(async () => 'ran')

    const second = await js.lockRun('sync', true, fn)
    release()
    await holding

    expect(second).toBeNull()
    expect(fn).not.toHaveBeenCalled()
  })

  it('fn의 실패는 그대로 reject한다', async () => {
    const failure = new Error('실패')

    await expect(toJsTabs(createSingleTabFallback()).lockRun('device', false, async () => Promise.reject(failure))).rejects.toBe(failure)
  })

  it('모르는 잠금 이름은 거절한다', () => {
    const js = toJsTabs(createSingleTabFallback())

    expect(() => js.lockRun('other' as 'device', false, async () => 1)).toThrow(TypeError)
  })

  it('post는 알림 종류를 채널로 보내고, 모르는 종류는 거절한다', () => {
    const tabs = createSingleTabFallback()
    const post = vi.spyOn(tabs.channel, 'post')
    const js = toJsTabs(tabs)

    js.post('locked')

    expect(post).toHaveBeenCalledWith('locked')
    expect(() => js.post('mystery')).toThrow(TypeError)
  })

  it('subscribe는 받은 알림을 넘기고, 해제하면 더 받지 않는다', () => {
    const listeners = new Set<(kind: TabMessageKind) => void>()
    const tabs: Tabs = {
      lock: createSingleTabFallback().lock,
      channel: {
        post: vi.fn(),
        subscribe: (listener) => {
          listeners.add(listener)
          return () => listeners.delete(listener)
        },
      },
    }
    const received: string[] = []

    const unsubscribe = toJsTabs(tabs).subscribe((kind) => received.push(kind))
    listeners.forEach((listener) => listener('record-changed'))
    unsubscribe()
    listeners.forEach((listener) => listener('activity'))

    expect(received).toEqual(['record-changed'])
    expect(listeners.size).toBe(0)
  })
})

describe('TabMemory를 JsTabMemory로', () => {
  const KDF = { email: 'user@example.com', salt: 'AAAAAAAAAAAAAAAAAAAAAA==', iterations: 600_000 }

  function fakeMemory(initial: typeof KDF | null = null) {
    const memory = { get: vi.fn((): typeof KDF | null => initial), set: vi.fn(), clear: vi.fn() }
    return memory satisfies TabMemory
  }

  it('기억이 없으면 null, 있으면 세 필드만 담은 JSON을 준다', () => {
    expect(toJsTabMemory(fakeMemory()).get()).toBeNull()

    const json = toJsTabMemory(fakeMemory(KDF)).get()

    expect(JSON.parse(json ?? 'null')).toEqual(KDF)
  })

  it('JSON을 객체로 바꿔 기억하고, 지우기를 넘긴다', () => {
    const memory = fakeMemory()
    const js = toJsTabMemory(memory)

    js.set(JSON.stringify(KDF))
    js.clear()

    expect(memory.set).toHaveBeenCalledWith(KDF)
    expect(memory.clear).toHaveBeenCalledOnce()
  })

  it('형식에 맞지 않는 JSON은 기억하지 않고 거절한다', () => {
    const memory = fakeMemory()
    const js = toJsTabMemory(memory)

    expect(() => js.set('{깨짐')).toThrow(TypeError)
    expect(() => js.set(JSON.stringify({ email: 'a@b.c', salt: 1, iterations: 1 }))).toThrow(TypeError)
    expect(memory.set).not.toHaveBeenCalled()
  })
})
