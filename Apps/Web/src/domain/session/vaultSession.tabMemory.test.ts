// @vitest-environment node
// 탭이 마지막으로 연 계정의 KDF 값을 기억해, 다른 탭이 비밀번호를 바꾼 뒤 새로 열려도 비밀번호 바뀜을 안내한다 (SEC-003 규칙 11, SEC-004 규칙 8)
import { describe, expect, it } from 'vitest'
import { FakeDeviceStore, PASSWORD, setup } from '../../test/sessionFixtures'
import { MasterPasswordChangedError } from '../auth/authService'
import type { RememberedKdf, TabMemory } from '../device/tabMemory'
import { createInMemoryTabHub } from '../tabs/inMemoryTabs'
import type { Tabs } from '../tabs/tabs'

const EMAIL = 'user@example.com'
const NEW_PASSWORD = 'a brand new master password'

/** sessionStorage처럼 새로고침해도 같은 탭에 남는 기억 */
class FakeTabMemory implements TabMemory {
  value: RememberedKdf | null = null
  get() {
    return this.value
  }
  set(value: RememberedKdf) {
    this.value = { ...value }
  }
  clear() {
    this.value = null
  }
}

/** 탭 A·B가 같은 브라우저(기기 기록·탭 허브)를 쓴다. B는 잠금을 풀어 기억을 남긴다 */
async function twoTabs() {
  const hub = createInMemoryTabHub()
  const deviceStore = new FakeDeviceStore()
  const aMemory = new FakeTabMemory()
  const bMemory = new FakeTabMemory()
  const a = setup({ deviceStore, tabs: hub.connect(), tabMemory: aMemory })
  await a.session.login(EMAIL, PASSWORD)
  const b = setup({ authApi: a.authApi, vaultApi: a.vaultApi, deviceStore, tabs: hub.connect(), tabMemory: bMemory })
  await b.session.restore()
  await b.session.unlock(PASSWORD)
  return { hub, deviceStore, a, aMemory, b, bMemory }
}

function reload(base: ReturnType<typeof setup>, tabs: Tabs, tabMemory: TabMemory) {
  return setup({ authApi: base.authApi, vaultApi: base.vaultApi, deviceStore: base.deviceStore, tabs, tabMemory })
}

describe('탭별 KDF 기억 (SEC-003 규칙 11)', () => {
  it('잠금 해제·로그인하면 이메일과 KDF 값(salt·반복 횟수)만 기억한다', async () => {
    const { a, aMemory, bMemory } = await twoTabs()

    expect(aMemory.value).toEqual({ email: EMAIL, salt: a.authApi.kdf.salt, iterations: a.authApi.kdf.iterations })
    expect(bMemory.value).toEqual(aMemory.value)
  })

  it('비밀번호를 바꾼 탭은 새 KDF 값을 기억한다', async () => {
    const { a, aMemory } = await twoTabs()

    await a.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

    expect(aMemory.value).toEqual({ email: EMAIL, salt: a.authApi.kdf.salt, iterations: a.authApi.kdf.iterations })
  })

  it('다른 탭이 비밀번호를 바꾼 뒤 이 탭이 새로 열리면 기기 기록은 남긴 채 로그아웃 상태로 비밀번호 바뀜을 알린다', async () => {
    const { hub, deviceStore, a, b, bMemory: liveMemory } = await twoTabs()
    // 이 탭(B)이 알림을 받기 전에 새로고침(탭 폐기 등)된 상황: 새로 열린 B의 기억은 옛 KDF 값 그대로다.
    // 살아 있는 B는 알림을 받아 안내한 뒤 기억을 새 값으로 맞추므로, 새로 열린 B의 기억은 따로 둔다
    const bMemory = new FakeTabMemory()
    bMemory.set(liveMemory.value!)
    await a.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)
    const recordAfterChange = deviceStore.record

    const reloaded = reload(b, hub.connect(), bMemory)
    await expect(reloaded.session.restore()).rejects.toBeInstanceOf(MasterPasswordChangedError)

    expect(reloaded.session.state().status).toBe('signedOut')
    expect(deviceStore.record).toEqual(recordAfterChange)
    await reloaded.session.login(EMAIL, NEW_PASSWORD)
    expect(bMemory.value?.salt).toBe(a.authApi.kdf.salt)
  })

  it('기억이 없는 새 탭은 지금처럼 기기 기록의 KDF 값으로 잠금 화면에서 시작한다', async () => {
    const { hub, a, b } = await twoTabs()
    await a.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

    const fresh = reload(b, hub.connect(), new FakeTabMemory())
    await fresh.session.restore()

    expect(fresh.session.state().status).toBe('locked')
    await fresh.session.unlock(NEW_PASSWORD)
  })

  it('다른 이메일의 기억은 무시한다', async () => {
    const { hub, a, b } = await twoTabs()
    await a.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)
    const memory = new FakeTabMemory()
    memory.set({ email: 'other@example.com', salt: 'AAAAAAAAAAAAAAAAAAAAAA==', iterations: 600_000 })

    const other = reload(b, hub.connect(), memory)
    await other.session.restore()

    expect(other.session.state().status).toBe('locked')
  })

  it('로그아웃하면 기억을 지운다', async () => {
    const { a, aMemory } = await twoTabs()

    await a.session.logout()

    expect(aMemory.value).toBeNull()
  })

  it('잠긴 탭이 다른 탭의 알림을 놓친 채 잠금을 풀면 SessionStateError가 아니라 비밀번호 바뀜을 알린다', async () => {
    const hub = createInMemoryTabHub()
    const deviceStore = new FakeDeviceStore()
    const a = setup({ deviceStore, tabs: hub.connect() })
    const bTabs = hub.connect()
    // 동결 등으로 알림을 받지 못하는 탭
    const deaf: Tabs = { lock: bTabs.lock, channel: { post: (kind) => bTabs.channel.post(kind), subscribe: () => () => undefined } }
    const b = setup({ authApi: a.authApi, vaultApi: a.vaultApi, deviceStore, tabs: deaf })
    await a.session.login(EMAIL, PASSWORD)
    await b.session.restore()
    await b.session.unlock(PASSWORD)
    b.session.lock('thisTab')
    await a.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

    await expect(b.session.unlock(PASSWORD)).rejects.toBeInstanceOf(MasterPasswordChangedError)
    expect(b.session.state().status).toBe('signedOut')
  })
})
