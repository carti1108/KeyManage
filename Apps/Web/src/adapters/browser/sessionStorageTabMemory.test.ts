// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { TAB_MEMORY_STORAGE_KEY, createSessionStorageTabMemory } from './sessionStorageTabMemory'

class FakeStorage {
  items = new Map<string, string>()
  getItem(key: string) {
    return this.items.get(key) ?? null
  }
  setItem(key: string, value: string) {
    this.items.set(key, value)
  }
  removeItem(key: string) {
    this.items.delete(key)
  }
}

const kdf = { email: 'user@example.com', salt: 'c2FsdHNhbHRzYWx0c2FsdA==', iterations: 600_000 }

describe('sessionStorage 탭 기억', () => {
  it('이메일·salt·반복 횟수만 저장하고 다시 읽는다', () => {
    const storage = new FakeStorage()
    const memory = createSessionStorageTabMemory(() => storage)

    memory.set(kdf)

    expect(createSessionStorageTabMemory(() => storage).get()).toEqual(kdf)
    expect(Object.keys(JSON.parse(storage.items.get(TAB_MEMORY_STORAGE_KEY) ?? '{}')).sort()).toEqual(['email', 'iterations', 'salt'])
  })

  it('지우면 기억이 없다', () => {
    const storage = new FakeStorage()
    const memory = createSessionStorageTabMemory(() => storage)
    memory.set(kdf)

    memory.clear()

    expect(memory.get()).toBeNull()
  })

  it('형식이 틀린 값은 기억이 없는 것으로 본다', () => {
    const bad = ['{', 'null', '"x"', JSON.stringify({ email: 'a', salt: 's' }), JSON.stringify({ ...kdf, iterations: '600000' }), JSON.stringify({ ...kdf, iterations: 1.5 })]
    for (const raw of bad) {
      const storage = new FakeStorage()
      storage.setItem(TAB_MEMORY_STORAGE_KEY, raw)

      expect(createSessionStorageTabMemory(() => storage).get(), raw).toBeNull()
    }
  })

  it('저장소에 접근하지 못하면 기억 없음으로 동작하고 예외를 내지 않는다', () => {
    const memory = createSessionStorageTabMemory(() => {
      throw new Error('SecurityError')
    })

    expect(() => memory.set(kdf)).not.toThrow()
    expect(memory.get()).toBeNull()
    expect(() => memory.clear()).not.toThrow()
  })
})
