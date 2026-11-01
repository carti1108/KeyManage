// @vitest-environment node
// 같은 계정을 연 여러 탭이 기기 기록을 덮어쓰지 않고 맞춘다 (ADR-WEB-001)
import { describe, expect, it, vi } from 'vitest'
import { FakeDeviceStore, NOW, PASSWORD, fields, setup } from '../../test/sessionFixtures'
import { fromBase64 } from '../crypto/base64'
import { deriveKeys } from '../crypto/keys'
import { openVault } from '../crypto/vaultCipher'
import type { DeviceRecord } from '../device/deviceRecord'
import { createInMemoryTabHub } from '../tabs/inMemoryTabs'
import type { TabMessageKind, Tabs } from '../tabs/tabs'
import { activeItems, addItem, parseVaultData, type VaultData } from '../vault/vaultData'
import type { VaultSession } from './vaultSession'

const EMAIL = 'user@example.com'

function unlockedData(session: VaultSession): VaultData {
  const state = session.state()
  if (state.status !== 'unlocked') throw new Error('잠금 해제 상태가 아님')
  return state.vault.data
}

function itemIds(data: VaultData): string[] {
  return activeItems(data)
    .map((item) => item.id)
    .sort()
}

function saveItem(session: VaultSession, id: string) {
  return session.save(addItem(unlockedData(session), { ...fields, serviceName: id }, NOW, id))
}

/** 기기 기록의 볼트를 마스터 비밀번호로 열어 살아 있는 항목 id를 준다 */
async function recordItemIds(record: DeviceRecord | undefined): Promise<string[]> {
  if (record === undefined) throw new Error('기기 기록이 없음')
  const { encryptionKey } = await deriveKeys(PASSWORD, fromBase64(record.kdf.salt), record.kdf.iterations)
  return itemIds(parseVaultData(await openVault(encryptionKey, record.payload)))
}

/** 같은 서버·기기 저장소·탭 허브를 쓰는 두 탭. A가 로그인한 뒤 B를 열어 잠금을 푼다 */
async function twoUnlockedTabs() {
  const hub = createInMemoryTabHub()
  const a = setup({ tabs: hub.connect() })
  const b = setup({ authApi: a.authApi, vaultApi: a.vaultApi, deviceStore: a.deviceStore, tabs: hub.connect() })
  await a.session.login(EMAIL, PASSWORD)
  await b.session.restore()
  await b.session.unlock(PASSWORD)
  return { hub, a, b, vaultApi: a.vaultApi, deviceStore: a.deviceStore }
}

function gateSaves(ctx: { vaultApi: { gate: Promise<void> | null; save: (...args: never[]) => Promise<number> } }) {
  let release!: () => void
  let entered!: () => void
  const started = new Promise<void>((resolve) => (entered = resolve))
  ctx.vaultApi.gate = new Promise<void>((resolve) => (release = resolve))
  const original = ctx.vaultApi.save.bind(ctx.vaultApi)
  ctx.vaultApi.save = (...args: never[]) => {
    entered()
    return original(...args)
  }
  return {
    started,
    release: () => {
      ctx.vaultApi.gate = null
      release()
    },
  }
}

describe('기기 기록 쓰기 (ADR-WEB-001)', () => {
  it('두 탭의 대기 내용을 모두 남긴다', async () => {
    const { a, b, vaultApi, deviceStore } = await twoUnlockedTabs()
    vaultApi.offline = true

    await saveItem(a.session, 'a')
    await saveItem(b.session, 'b')

    expect(await recordItemIds(deviceStore.record)).toEqual(['a', 'b'])
    expect(deviceStore.record?.pendingSync).toBe(true)
  })

  it('쓰기 전에 다른 탭의 기록을 메모리에 병합한다', async () => {
    const { a, b, vaultApi } = await twoUnlockedTabs()
    vaultApi.offline = true

    await saveItem(b.session, 'b')
    await saveItem(a.session, 'a')

    expect(itemIds(unlockedData(a.session))).toEqual(['a', 'b'])
  })

  it('보내는 사이 다른 탭이 대기 기록을 쓰면 보낸 뒤에도 대기로 남긴다', async () => {
    const { a, b, vaultApi, deviceStore } = await twoUnlockedTabs()
    const gate = gateSaves({ vaultApi })

    const saving = saveItem(a.session, 'a')
    await gate.started
    vaultApi.offline = true
    await saveItem(b.session, 'b')
    vaultApi.offline = false
    gate.release()
    await saving

    expect(deviceStore.record?.pendingSync).toBe(true)
    expect(await recordItemIds(deviceStore.record)).toEqual(['a', 'b'])
  })

  it('보내는 사이 바뀐 것이 없으면 보낸 뒤 대기를 푼다', async () => {
    const { a, vaultApi, deviceStore } = await twoUnlockedTabs()
    const gate = gateSaves({ vaultApi })

    const saving = saveItem(a.session, 'a')
    await gate.started
    gate.release()
    await saving

    expect(deviceStore.record?.pendingSync).toBe(false)
    expect(a.session.state()).toMatchObject({ pendingSync: false })
  })

  it('다른 탭이 로그아웃해 지운 기록을 되살리지 않고 이 탭도 로그아웃한다', async () => {
    const { a, b, deviceStore } = await twoUnlockedTabs()
    const data = unlockedData(a.session)

    await b.session.logout()
    await a.session.save(addItem(data, fields, NOW, 'late')).catch(() => undefined)

    expect(deviceStore.record).toBeUndefined()
    expect(a.session.state()).toEqual({ status: 'signedOut' })
  })

  it('같은 이메일인데 KDF 값이 다른 기록이면 쓰지 않고 로그인 화면으로 보낸다', async () => {
    const { a, vaultApi, deviceStore } = await twoUnlockedTabs()
    vaultApi.offline = true
    const changed = { ...deviceStore.record!, kdf: { ...deviceStore.record!.kdf, salt: 'AAAAAAAAAAAAAAAAAAAAAA==' } }
    deviceStore.stored = structuredClone(changed)

    await saveItem(a.session, 'a').catch(() => undefined)

    expect(a.session.state()).toEqual({ status: 'signedOut' })
    expect(deviceStore.record).toEqual(changed)
  })

  it('잠긴 탭은 다른 탭이 쓴 기록 위에 보낸 결과를 쓰지 않는다', async () => {
    const { a, b, vaultApi, deviceStore } = await twoUnlockedTabs()
    const gate = gateSaves({ vaultApi })

    const saving = saveItem(a.session, 'a')
    await gate.started
    a.session.lock('thisTab')
    vaultApi.offline = true
    await saveItem(b.session, 'b')
    const written = structuredClone(deviceStore.record)
    vaultApi.offline = false
    gate.release()
    await saving.catch(() => undefined)

    expect(deviceStore.record).toEqual(written)
  })
})

/** 받는 메시지를 끊을 수 있는 탭. 동결돼 메시지를 놓친 탭을 흉내 낸다 */
function mutable(tabs: Tabs) {
  let muted = false
  const wrapped: Tabs = {
    lock: tabs.lock,
    channel: {
      post: (kind) => tabs.channel.post(kind),
      subscribe: (listener: (kind: TabMessageKind) => void) => tabs.channel.subscribe((kind) => muted || listener(kind)),
    },
  }
  return { tabs: wrapped, mute: () => (muted = true) }
}

/** 다른 탭이 보낸 메시지의 처리(복호화 등)가 끝나기를 잠깐 기다린다 */
const settle = () => new Promise((resolve) => setTimeout(resolve, 50))

describe('다른 탭에 알리기 (ADR-WEB-001)', () => {
  it('한 탭에서 잠그면 모든 탭이 잠긴다', async () => {
    const { a, b } = await twoUnlockedTabs()

    a.session.lock()

    expect(b.session.state().status).toBe('locked')
  })

  it('이 탭만 잠그면 다른 탭에 알리지 않는다', async () => {
    const { a, b } = await twoUnlockedTabs()

    a.session.lock('thisTab')

    expect(a.session.state().status).toBe('locked')
    expect(b.session.state().status).toBe('unlocked')
  })

  it('한 탭에서 로그아웃하면 모든 탭이 로그아웃하고 기록은 지운 채로 둔다', async () => {
    const { a, b, deviceStore } = await twoUnlockedTabs()
    const writes = deviceStore.writes

    await b.session.logout()
    await settle()

    expect(a.session.state()).toEqual({ status: 'signedOut' })
    expect(deviceStore.record).toBeUndefined()
    expect(deviceStore.writes).toBe(writes)
    expect(a.authApi.loggedOut).toHaveLength(2)
  })

  it('다른 계정으로 로그인하면 이전 계정의 다른 탭은 로그아웃한다', async () => {
    const { a, b, deviceStore } = await twoUnlockedTabs()

    await b.session.login('other@example.com', PASSWORD)
    await settle()

    expect(a.session.state()).toEqual({ status: 'signedOut' })
    expect(deviceStore.record?.email).toBe('other@example.com')
  })

  it('잠긴 탭은 다른 탭이 쓴 기록으로 암호화된 볼트를 바꾼다', async () => {
    const { a, b, vaultApi } = await twoUnlockedTabs()
    a.session.lock()
    await b.session.unlock(PASSWORD)
    vaultApi.offline = true

    await saveItem(b.session, 'b')
    await vi.waitFor(() => expect(a.session.state()).toMatchObject({ status: 'locked', pendingSync: true }))

    await a.session.unlock(PASSWORD)
    expect(itemIds(unlockedData(a.session))).toEqual(['b'])
  })

  it('알림을 놓쳤어도 잠금을 풀 때 최신 기기 기록으로 연다', async () => {
    const hub = createInMemoryTabHub()
    const tabA = mutable(hub.connect())
    const a = setup({ tabs: tabA.tabs })
    const b = setup({ authApi: a.authApi, vaultApi: a.vaultApi, deviceStore: a.deviceStore, tabs: hub.connect() })
    await a.session.login(EMAIL, PASSWORD)
    await b.session.restore()
    await b.session.unlock(PASSWORD)
    a.session.lock('thisTab')
    tabA.mute()
    a.vaultApi.offline = true

    await saveItem(b.session, 'b')
    expect(a.session.state()).toMatchObject({ status: 'locked', pendingSync: false })
    await a.session.unlock(PASSWORD)

    expect(itemIds(unlockedData(a.session))).toEqual(['b'])
  })

  it('로그아웃 확인용으로 다른 탭이 남긴 대기 내용도 알려 준다', async () => {
    const hub = createInMemoryTabHub()
    const tabA = mutable(hub.connect())
    const a = setup({ tabs: tabA.tabs })
    const b = setup({ authApi: a.authApi, vaultApi: a.vaultApi, deviceStore: a.deviceStore, tabs: hub.connect() })
    await a.session.login(EMAIL, PASSWORD)
    await b.session.restore()
    await b.session.unlock(PASSWORD)
    tabA.mute()
    a.vaultApi.offline = true

    expect(await a.session.pendingOnDevice()).toBe(false)
    await saveItem(b.session, 'b')

    expect(a.session.state()).toMatchObject({ pendingSync: false })
    expect(await a.session.pendingOnDevice()).toBe(true)
  })

  it('로그인 전 탭은 메시지를 무시한다', async () => {
    const { hub, a, vaultApi } = await twoUnlockedTabs()
    const c = setup({ authApi: a.authApi, vaultApi, deviceStore: a.deviceStore, tabs: hub.connect() })
    vaultApi.offline = true

    await saveItem(a.session, 'a')
    a.session.lock()
    await a.session.logout()
    await settle()

    expect(c.session.state()).toEqual({ status: 'signedOut' })
    expect(c.locks()).toBe(0)
  })

  it('다른 탭 때문에 상태가 바뀌면 알린다', async () => {
    const { a, b, vaultApi } = await twoUnlockedTabs()
    const changes = vi.fn()
    a.session.onRemoteChange(changes)
    vaultApi.offline = true

    await saveItem(b.session, 'b')

    await vi.waitFor(() => expect(itemIds(unlockedData(a.session))).toEqual(['b']))
    expect(changes).toHaveBeenCalled()
  })
})

/** 공유 기기 저장소 위에서 이 탭의 쓰기만 실패시킬 수 있는 저장소 (저장 공간 부족 등) */
class TabDeviceStore extends FakeDeviceStore {
  failWrites = false
  private readonly shared: FakeDeviceStore
  constructor(shared: FakeDeviceStore) {
    super()
    this.shared = shared
  }
  override async load(): Promise<unknown> {
    return this.shared.load()
  }
  override async save(record: DeviceRecord): Promise<void> {
    if (this.failWrites) throw new Error('저장 공간 부족')
    return this.shared.save(record)
  }
  override async clear(): Promise<void> {
    return this.shared.clear()
  }
}

describe('기기에 쓰지 못한 대기 내용 (ADR-WEB-001)', () => {
  it('쓰기 실패 뒤 잠갔다 풀어도 메모리의 대기 내용을 잃지 않는다', async () => {
    const shared = new FakeDeviceStore()
    const deviceStore = new TabDeviceStore(shared)
    const tab = setup({ deviceStore })
    await tab.session.login(EMAIL, PASSWORD)
    tab.vaultApi.offline = true
    deviceStore.failWrites = true

    await saveItem(tab.session, 'x')
    tab.session.lock('thisTab')
    deviceStore.failWrites = false
    await tab.session.unlock(PASSWORD)

    expect(itemIds(unlockedData(tab.session))).toEqual(['x'])
    expect(await recordItemIds(shared.record)).toEqual(['x'])
  })

  it('쓰기에 실패한 잠긴 탭은 다른 탭의 기록으로 대기 내용을 덮지 않고, 풀 때 합친다', async () => {
    const hub = createInMemoryTabHub()
    const shared = new FakeDeviceStore()
    const storeB = new TabDeviceStore(shared)
    const a = setup({ deviceStore: shared, tabs: hub.connect() })
    const b = setup({ authApi: a.authApi, vaultApi: a.vaultApi, deviceStore: storeB, tabs: hub.connect() })
    await a.session.login(EMAIL, PASSWORD)
    await b.session.restore()
    await b.session.unlock(PASSWORD)
    a.vaultApi.offline = true
    storeB.failWrites = true

    await saveItem(b.session, 'y')
    b.session.lock('thisTab')
    await saveItem(a.session, 'a')
    await settle()
    storeB.failWrites = false
    await b.session.unlock(PASSWORD)

    expect(itemIds(unlockedData(b.session))).toEqual(['a', 'y'])
    expect(await recordItemIds(shared.record)).toEqual(['a', 'y'])
  })
})

describe('로그아웃 확인 (ADR-WEB-001)', () => {
  it('기기 기록을 읽지 못하면 대기 내용이 있다고 보고 확인을 받는다', async () => {
    const { a, deviceStore } = await twoUnlockedTabs()
    deviceStore.failLoads = 1

    expect(await a.session.pendingOnDevice()).toBe(true)
  })
})
