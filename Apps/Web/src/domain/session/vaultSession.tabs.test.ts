// @vitest-environment node
// 같은 계정을 연 여러 탭이 기기 기록을 덮어쓰지 않고 맞춘다 (ADR-WEB-001)
import { describe, expect, it } from 'vitest'
import { NOW, PASSWORD, fields, setup } from '../../test/sessionFixtures'
import { fromBase64 } from '../crypto/base64'
import { deriveKeys } from '../crypto/keys'
import { openVault } from '../crypto/vaultCipher'
import type { DeviceRecord } from '../device/deviceRecord'
import { createInMemoryTabHub } from '../tabs/inMemoryTabs'
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
    a.session.lock()
    vaultApi.offline = true
    await saveItem(b.session, 'b')
    const written = structuredClone(deviceStore.record)
    vaultApi.offline = false
    gate.release()
    await saving.catch(() => undefined)

    expect(deviceStore.record).toEqual(written)
  })
})
