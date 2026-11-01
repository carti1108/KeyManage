import { describe, expect, it } from 'vitest'
import { createInMemoryTabHub, createSingleTabFallback } from './inMemoryTabs'
import type { TabMessageKind } from './tabs'

function deferred() {
  let resolve!: () => void
  const promise = new Promise<void>((r) => (resolve = r))
  return { promise, resolve }
}

describe('같은 허브에 붙은 탭 (ADR-WEB-001)', () => {
  it('두 탭의 잠금 구간이 겹치지 않는다', async () => {
    const hub = createInMemoryTabHub()
    const a = hub.connect()
    const b = hub.connect()
    const events: string[] = []
    const gate = deferred()

    const first = a.lock.run('device', async () => {
      events.push('a:start')
      await gate.promise
      events.push('a:end')
      return 'a'
    })
    const second = b.lock.run('device', async () => {
      events.push('b:start')
      return 'b'
    })
    await Promise.resolve()
    gate.resolve()

    expect(await Promise.all([first, second])).toEqual(['a', 'b'])
    expect(events).toEqual(['a:start', 'a:end', 'b:start'])
  })

  it('잠금 이름이 다르면 서로 기다리지 않는다', async () => {
    const hub = createInMemoryTabHub()
    const gate = deferred()
    const held = hub.connect().lock.run('device', () => gate.promise)

    expect(await hub.connect().lock.run('sync', async () => 'sync')).toBe('sync')
    gate.resolve()
    await held
  })

  it('ifAvailable이면 다른 탭이 잡고 있는 동안 실행하지 않고 null을 준다', async () => {
    const hub = createInMemoryTabHub()
    const gate = deferred()
    const held = hub.connect().lock.run('sync', () => gate.promise)
    let ran = false

    const result = await hub.connect().lock.run('sync', async () => (ran = true), { ifAvailable: true })

    expect(result).toBeNull()
    expect(ran).toBe(false)
    gate.resolve()
    await held
  })

  it('앞선 실행이 실패해도 다음 실행은 잠금을 얻는다', async () => {
    const tabs = createInMemoryTabHub().connect()

    await expect(tabs.lock.run('device', async () => Promise.reject(new Error('x')))).rejects.toThrow('x')
    expect(await tabs.lock.run('device', async () => 1)).toBe(1)
  })

  it('보낸 메시지는 다른 탭에만 닿는다', () => {
    const hub = createInMemoryTabHub()
    const a = hub.connect()
    const b = hub.connect()
    const toA: TabMessageKind[] = []
    const toB: TabMessageKind[] = []
    a.channel.subscribe((kind) => toA.push(kind))
    b.channel.subscribe((kind) => toB.push(kind))

    a.channel.post('locked')

    expect(toA).toEqual([])
    expect(toB).toEqual(['locked'])
  })

  it('구독을 해제하면 더 받지 않는다', () => {
    const hub = createInMemoryTabHub()
    const a = hub.connect()
    const received: TabMessageKind[] = []
    const unsubscribe = hub.connect().channel.subscribe((kind) => received.push(kind))

    unsubscribe()
    a.channel.post('activity')

    expect(received).toEqual([])
  })
})

describe('탭 간 기능이 없는 브라우저 대체', () => {
  it('탭 안에서는 잠금 구간을 순서대로 실행하고 아무것도 보내지 않는다', async () => {
    const tabs = createSingleTabFallback()
    const events: string[] = []
    const gate = deferred()
    const received: TabMessageKind[] = []
    tabs.channel.subscribe((kind) => received.push(kind))

    const first = tabs.lock.run('device', async () => {
      events.push('1:start')
      await gate.promise
      events.push('1:end')
    })
    const second = tabs.lock.run('device', async () => {
      events.push('2:start')
    })
    tabs.channel.post('locked')
    gate.resolve()
    await Promise.all([first, second])

    expect(events).toEqual(['1:start', '1:end', '2:start'])
    expect(received).toEqual([])
  })
})
