// @vitest-environment node
// 비밀번호 바뀜을 안내한 탭은 탭 기억을 기기 기록의 KDF 값으로 맞춰 안내를 한 번만 하고,
// 비밀번호를 바꾼 탭은 기기 기록을 쓴 뒤에만 새 KDF 값을 기억한다 (SEC-003 규칙 11, SEC-004)
import { describe, expect, it } from 'vitest'
import { FakeDeviceStore, PASSWORD, setup } from '../../test/sessionFixtures'
import { MasterPasswordChangedError } from '../auth/authService'
import type { RememberedKdf, TabMemory } from '../device/tabMemory'
import { createInMemoryTabHub } from '../tabs/inMemoryTabs'
import type { Tabs } from '../tabs/tabs'

const EMAIL = 'user@example.com'
const NEW_PASSWORD = 'a brand new master password'

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

function recordKdf(deviceStore: FakeDeviceStore): RememberedKdf {
  const record = deviceStore.record
  if (record === undefined) throw new Error('기기 기록이 없음')
  return { email: record.email, salt: record.kdf.salt, iterations: record.kdf.iterations }
}

describe('비밀번호 바뀜 안내는 한 번만 (SEC-003 규칙 11)', () => {
  it('새로 열려 비밀번호 바뀜을 안내한 탭은 다음 새로고침부터 기록의 KDF 값으로 잠금 화면에서 시작하고 오프라인에서도 새 비밀번호로 연다', async () => {
    const { hub, deviceStore, a, b, bMemory } = await twoTabs()
    // 이 탭(B)이 알림을 받기 전에 새로고침된 상황: 기억은 옛 KDF 값 그대로다
    const staleMemory = new FakeTabMemory()
    staleMemory.set(bMemory.value!)
    await a.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

    const first = reload(b, hub.connect(), staleMemory)
    await expect(first.session.restore()).rejects.toBeInstanceOf(MasterPasswordChangedError)
    expect(staleMemory.value).toEqual(recordKdf(deviceStore))

    const second = reload(b, hub.connect(), staleMemory)
    await second.session.restore()
    expect(second.session.state().status).toBe('locked')
    a.authApi.offline = true
    expect(await second.session.unlock(NEW_PASSWORD)).toEqual({ offline: true })
  })

  it('열려 있던 탭이 다른 탭의 비밀번호 변경을 알게 되면 탭 기억을 새 KDF 값으로 맞춘다', async () => {
    const { deviceStore, a, b, bMemory } = await twoTabs()

    await a.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(b.session.state().status).toBe('signedOut')
    expect(bMemory.value).toEqual(recordKdf(deviceStore))
  })

  it('잠금 해제 때 서버에서 비밀번호 바뀜을 알게 되면 다른 탭 감지와 같이 정리하고 화면에 다시 읽으라고 알린다', async () => {
    const mine = setup({ tabMemory: new FakeTabMemory() })
    await mine.session.login(EMAIL, PASSWORD)
    const other = setup({ authApi: mine.authApi, vaultApi: mine.vaultApi })
    await other.session.login(EMAIL, PASSWORD)
    await other.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)
    mine.session.lock()
    let remoteChanges = 0
    mine.session.onRemoteChange(() => remoteChanges++)

    await expect(mine.session.unlock(NEW_PASSWORD)).rejects.toBeInstanceOf(MasterPasswordChangedError)

    expect(mine.session.state().status).toBe('signedOut')
    expect(remoteChanges).toBe(1)
  })
})

describe('비밀번호를 바꾼 탭의 기억 (SEC-004)', () => {
  it('기기 기록을 쓰지 못하면 새 KDF 값을 기억하지 않아 새로고침 때 다른 곳에서 바뀐 것으로 오판하지 않는다', async () => {
    const { deviceStore, a, aMemory } = await twoTabs()
    const before = aMemory.value
    deviceStore.failing = true

    await a.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

    expect(aMemory.value).toEqual(before)
    expect(a.authApi.kdf.salt).not.toBe(before?.salt)
  })

  it('기기 기록을 쓰면 새 KDF 값을 기억한다', async () => {
    const { deviceStore, a, aMemory } = await twoTabs()

    await a.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

    expect(aMemory.value).toEqual(recordKdf(deviceStore))
    expect(aMemory.value?.salt).toBe(a.authApi.kdf.salt)
  })
})
