import { afterEach, describe, expect, it } from 'vitest'
import { AUTO_LOCK_OPTIONS_MS, DEFAULT_AUTO_LOCK_MS, InvalidAutoLockTimeoutError } from '../../domain/lock/autoLock'
import { AUTO_LOCK_STORAGE_KEY, createLocalStorageAutoLockSetting } from './localStorageAutoLockSetting'

type StorageLike = Pick<Storage, 'getItem' | 'setItem'>

function memoryStorage(initial: Record<string, string> = {}): StorageLike & { values: Map<string, string> } {
  const values = new Map(Object.entries(initial))
  return {
    values,
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => void values.set(key, value),
  }
}

function brokenStorage(): StorageLike {
  return {
    getItem: () => {
      throw new DOMException('막힘', 'SecurityError')
    },
    setItem: () => {
      throw new DOMException('가득 참', 'QuotaExceededError')
    },
  }
}

function otherTabWrites(key: string | null, newValue: string | null) {
  window.dispatchEvent(new StorageEvent('storage', { key, newValue }))
}

describe('localStorage 자동 잠금 설정 (ADR-WEB-002)', () => {
  const offs: (() => void)[] = []
  afterEach(() => offs.splice(0).forEach((off) => off()))

  it('저장값이 없으면 기본 5분', () => {
    expect(createLocalStorageAutoLockSetting(() => memoryStorage()).read()).toBe(DEFAULT_AUTO_LOCK_MS)
  })

  it('저장한 선택지를 읽는다', () => {
    const storage = memoryStorage({ [AUTO_LOCK_STORAGE_KEY]: '900000' })
    expect(createLocalStorageAutoLockSetting(() => storage).read()).toBe(AUTO_LOCK_OPTIONS_MS.fifteenMinutes)
  })

  it('선택지가 아닌 저장값이면 기본 5분', () => {
    const storage = memoryStorage({ [AUTO_LOCK_STORAGE_KEY]: '12345' })
    expect(createLocalStorageAutoLockSetting(() => storage).read()).toBe(DEFAULT_AUTO_LOCK_MS)
  })

  it('저장소를 얻거나 읽다가 예외가 나면 기본 5분', () => {
    expect(createLocalStorageAutoLockSetting(brokenStorage).read()).toBe(DEFAULT_AUTO_LOCK_MS)
    const unavailable = () => {
      throw new DOMException('막힘', 'SecurityError')
    }
    expect(createLocalStorageAutoLockSetting(unavailable).read()).toBe(DEFAULT_AUTO_LOCK_MS)
  })

  it('밀리초 문자열로 같은 키에 저장한다', () => {
    const storage = memoryStorage()
    const setting = createLocalStorageAutoLockSetting(() => storage)

    setting.write(AUTO_LOCK_OPTIONS_MS.oneHour)

    expect(storage.values.get(AUTO_LOCK_STORAGE_KEY)).toBe('3600000')
    expect(createLocalStorageAutoLockSetting(() => storage).read()).toBe(AUTO_LOCK_OPTIONS_MS.oneHour)
  })

  it('쓰기에 실패해도 던지지 않고 이번 실행에는 바꾼 값을 쓴다', () => {
    const setting = createLocalStorageAutoLockSetting(brokenStorage)

    expect(() => setting.write(AUTO_LOCK_OPTIONS_MS.oneMinute)).not.toThrow()
    expect(setting.read()).toBe(AUTO_LOCK_OPTIONS_MS.oneMinute)
  })

  it('선택지가 아닌 값은 쓰지 않고 던진다', () => {
    const storage = memoryStorage()
    const setting = createLocalStorageAutoLockSetting(() => storage)

    expect(() => setting.write(123)).toThrow(InvalidAutoLockTimeoutError)
    expect(storage.values.has(AUTO_LOCK_STORAGE_KEY)).toBe(false)
  })

  it('다른 탭이 같은 키를 바꾸면 검증한 값을 알린다', () => {
    const received: number[] = []
    const setting = createLocalStorageAutoLockSetting(() => memoryStorage())
    offs.push(setting.subscribe((ms) => received.push(ms)))

    otherTabWrites(AUTO_LOCK_STORAGE_KEY, '60000')
    otherTabWrites(AUTO_LOCK_STORAGE_KEY, 'garbage')

    expect(received).toEqual([AUTO_LOCK_OPTIONS_MS.oneMinute, DEFAULT_AUTO_LOCK_MS])
    expect(setting.read()).toBe(DEFAULT_AUTO_LOCK_MS)
  })

  it('다른 탭의 받은 값은 이 탭의 이후 읽기에도 쓴다', () => {
    const setting = createLocalStorageAutoLockSetting(brokenStorage)
    offs.push(setting.subscribe(() => undefined))

    otherTabWrites(AUTO_LOCK_STORAGE_KEY, '900000')

    expect(setting.read()).toBe(AUTO_LOCK_OPTIONS_MS.fifteenMinutes)
  })

  it('다른 키나 전체 지우기(key 없음) 이벤트는 무시한다', () => {
    const received: number[] = []
    const setting = createLocalStorageAutoLockSetting(() => memoryStorage())
    offs.push(setting.subscribe((ms) => received.push(ms)))

    otherTabWrites('other.key', '60000')
    otherTabWrites(null, null)

    expect(received).toEqual([])
  })

  it('구독을 해제하면 더 받지 않는다', () => {
    const received: number[] = []
    const setting = createLocalStorageAutoLockSetting(() => memoryStorage())
    const off = setting.subscribe((ms) => received.push(ms))

    off()
    otherTabWrites(AUTO_LOCK_STORAGE_KEY, '60000')

    expect(received).toEqual([])
  })
})
