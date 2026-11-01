import { IDBFactory } from 'fake-indexeddb'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { DEVICE_RECORD_VERSION, DeviceStorageError, type DeviceRecord } from '../../domain/device/deviceRecord'
import { OPEN_TIMEOUT_MS, createIndexedDbDeviceStore } from './indexedDbDeviceStore'

const record: DeviceRecord = {
  version: DEVICE_RECORD_VERSION,
  email: 'user@example.com',
  kdf: { salt: 'AAAAAAAAAAAAAAAAAAAAAA==', iterations: 600_000 },
  payload: { version: 1, cipher: 'AES-256-GCM', iv: 'aXY=', ciphertext: 'Y3Q=', tag: 'dGFn' },
  revision: 3,
  lastSeenRevision: 4,
  pendingSync: true,
}

describe('IndexedDB 기기 저장소 (ADR-0019)', () => {
  it('저장한 적이 없으면 undefined를 읽는다', async () => {
    const store = createIndexedDbDeviceStore(new IDBFactory())

    expect(await store.load()).toBeUndefined()
  })

  it('저장한 기록을 다시 열어도 읽는다', async () => {
    const factory = new IDBFactory()
    await createIndexedDbDeviceStore(factory).save(record)

    expect(await createIndexedDbDeviceStore(factory).load()).toEqual(record)
  })

  it('다시 저장하면 한 계정의 기록만 남는다', async () => {
    const store = createIndexedDbDeviceStore(new IDBFactory())
    await store.save(record)

    await store.save({ ...record, email: 'other@example.com', pendingSync: false })

    expect(await store.load()).toMatchObject({ email: 'other@example.com', pendingSync: false })
  })

  it('지우면 다시 읽을 때 없다', async () => {
    const store = createIndexedDbDeviceStore(new IDBFactory())
    await store.save(record)

    await store.clear()

    expect(await store.load()).toBeUndefined()
  })

  afterEach(() => vi.useRealTimers())

  it('다른 탭이 열기를 막으면 기다리지 않고 DeviceStorageError를 낸다', async () => {
    const request = {} as IDBOpenDBRequest
    const blocking = { open: () => (queueMicrotask(() => request.onblocked?.({} as IDBVersionChangeEvent)), request) } as unknown as globalThis.IDBFactory

    await expect(createIndexedDbDeviceStore(blocking).load()).rejects.toBeInstanceOf(DeviceStorageError)
  })

  it('열기가 끝나지 않으면 제한 시간 뒤 DeviceStorageError를 낸다 (빈 화면에 멈추지 않게)', async () => {
    vi.useFakeTimers()
    const silent = { open: () => ({}) as IDBOpenDBRequest } as unknown as globalThis.IDBFactory

    const loading = expect(createIndexedDbDeviceStore(silent).load()).rejects.toBeInstanceOf(DeviceStorageError)
    await vi.advanceTimersByTimeAsync(OPEN_TIMEOUT_MS)

    await loading
  })

  it('저장소를 열지 못하면 DeviceStorageError를 낸다', async () => {
    const broken = { open: () => { throw new Error('사생활 보호 모드') } } as unknown as globalThis.IDBFactory
    const store = createIndexedDbDeviceStore(broken)

    await expect(store.load()).rejects.toBeInstanceOf(DeviceStorageError)
    await expect(store.save(record)).rejects.toBeInstanceOf(DeviceStorageError)
  })
})
