import { describe, expect, it } from 'vitest'
import type { TabMessageKind } from '../../domain/tabs/tabs'
import { createBrowserTabs, type BrowserTabsEnv } from './browserTabs'

// 같은 이름의 채널끼리 메시지를 주고받는 가짜 BroadcastChannel. 보낸 채널 자신은 받지 않는다
class FakeChannel {
  static open: FakeChannel[] = []
  static sent: unknown[] = []
  onmessage: ((event: MessageEvent) => void) | null = null
  readonly name: string

  constructor(name: string) {
    this.name = name
    FakeChannel.open.push(this)
  }
  postMessage(data: unknown) {
    FakeChannel.sent.push(data)
    for (const other of FakeChannel.open) if (other !== this && other.name === this.name) other.onmessage?.({ data } as MessageEvent)
  }
  close() {
    FakeChannel.open = FakeChannel.open.filter((channel) => channel !== this)
  }
  static deliver(data: unknown) {
    for (const channel of FakeChannel.open) channel.onmessage?.({ data } as MessageEvent)
  }
}

const fakeLocks = {
  request: async (_name: string, _options: unknown, callback: (lock: unknown) => Promise<unknown>) => callback({}),
} as unknown as LockManager

function env(overrides: Partial<BrowserTabsEnv> = {}): BrowserTabsEnv {
  FakeChannel.open = []
  FakeChannel.sent = []
  return { locks: fakeLocks, Channel: FakeChannel as unknown as typeof BroadcastChannel, ...overrides }
}

describe('브라우저 탭 간 잠금·알림 (ADR-WEB-001)', () => {
  it('메시지에는 종류와 보낸 탭 id만 담는다', () => {
    const tabs = createBrowserTabs(env())

    tabs.channel.post('record-changed')

    expect(FakeChannel.sent).toHaveLength(1)
    expect(Object.keys(FakeChannel.sent[0] as object).sort()).toEqual(['from', 'kind'])
  })

  it('다른 탭이 보낸 메시지를 받는다', () => {
    const deps = env()
    const a = createBrowserTabs(deps)
    const b = createBrowserTabs(deps)
    const received: TabMessageKind[] = []
    b.channel.subscribe((kind) => received.push(kind))

    a.channel.post('logged-out')

    expect(received).toEqual(['logged-out'])
  })

  it('형식이 틀린 메시지는 버린다', () => {
    const tabs = createBrowserTabs(env())
    const received: TabMessageKind[] = []
    tabs.channel.subscribe((kind) => received.push(kind))

    for (const data of [{ kind: 'x', from: 'other' }, null, 'locked', { kind: 'locked' }, { kind: 'locked', from: 1 }]) FakeChannel.deliver(data)

    expect(received).toEqual([])
  })

  it('자기가 보낸 id의 메시지는 받지 않는다', () => {
    const tabs = createBrowserTabs(env())
    const received: TabMessageKind[] = []
    tabs.channel.subscribe((kind) => received.push(kind))
    tabs.channel.post('activity')

    FakeChannel.deliver(FakeChannel.sent[0])

    expect(received).toEqual([])
  })

  it('잠금은 이름에 접두사를 붙여 요청하고 ifAvailable을 넘긴다', async () => {
    const requests: unknown[][] = []
    const locks = {
      request: async (name: string, options: LockOptions, callback: (lock: Lock | null) => Promise<unknown>) => {
        requests.push([name, options])
        return callback(options.ifAvailable ? null : ({} as Lock))
      },
    } as unknown as LockManager
    const tabs = createBrowserTabs(env({ locks }))

    expect(await tabs.lock.run('device', async () => 1)).toBe(1)
    expect(await tabs.lock.run('sync', async () => 2, { ifAvailable: true })).toBeNull()
    expect(requests).toEqual([
      ['keymanage-device', { ifAvailable: false }],
      ['keymanage-sync', { ifAvailable: true }],
    ])
  })

  it('navigator.locks가 없으면 탭 하나 전제로 대체한다', async () => {
    const tabs = createBrowserTabs(env({ locks: undefined }))

    tabs.channel.post('locked')

    expect(FakeChannel.sent).toEqual([])
    expect(await tabs.lock.run('device', async () => 'ran')).toBe('ran')
  })

  it('BroadcastChannel이 없으면 탭 하나 전제로 대체한다', async () => {
    const tabs = createBrowserTabs(env({ Channel: undefined }))

    expect(await tabs.lock.run('device', async () => 'ran')).toBe('ran')
  })
})
