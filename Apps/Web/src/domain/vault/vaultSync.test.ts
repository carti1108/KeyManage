// @vitest-environment node
import { beforeAll, describe, expect, it } from 'vitest'
import { keysOf } from '../crypto/testVectors'
import { openVault, sealVault, type EncryptedVaultPayload } from '../crypto/vaultCipher'
import { ServerError, UnexpectedResponseError } from '../server/serverErrors'
import { InvalidVaultDataError, addItem, emptyVault, parseVaultData, type VaultData } from './vaultData'
import type { RemoteVault, VaultApi } from './vaultApi'
import {
  MAX_PAYLOAD_BYTES,
  ReadOnlyVaultError,
  RevisionRollbackError,
  VaultSyncConflictError,
  VaultTooLargeError,
  assertWithinLimit,
  createRevisionMemory,
  createVaultSync,
} from './vaultSync'

const NOW = new Date('2026-10-29T00:10:00Z')
const fields = { serviceName: 'S', username: 'u', password: 'p' }

let key: CryptoKey

beforeAll(async () => {
  key = (await keysOf('ascii-passphrase')).encryptionKey
})

// 서버처럼 revision을 관리하고, 기대 revision이 다르면 409를 낸다. 다른 기기의 저장도 흉내 낼 수 있다
class FakeVaultApi implements VaultApi {
  revision = 0
  payload: EncryptedVaultPayload | null = null
  saves = 0
  /** 저장 요청이 올 때마다 그 직전에 다른 기기가 먼저 저장하게 한다 */
  otherDeviceWritesBeforeEachSave = 0

  async get(): Promise<RemoteVault | null> {
    return this.payload === null ? null : { revision: this.revision, payload: this.payload }
  }

  async save(expectedRevision: number, payload: EncryptedVaultPayload): Promise<number> {
    this.saves += 1
    if (this.otherDeviceWritesBeforeEachSave > 0) {
      this.otherDeviceWritesBeforeEachSave -= 1
      await this.storeAsOtherDevice(addItem(await this.current(), fields, NOW, `other-${this.revision}`))
    }
    if (expectedRevision !== this.revision) throw new ServerError(409, 'VAULT_CONFLICT')
    this.payload = payload
    this.revision += 1
    return this.revision
  }

  async storeAsOtherDevice(data: VaultData) {
    this.payload = await sealVault(key, JSON.stringify(data))
    this.revision += 1
  }

  async current(): Promise<VaultData> {
    return this.payload === null ? emptyVault(NOW) : parseVaultData(await openVault(key, this.payload))
  }
}

const syncWith = (api: VaultApi, revisionMemory = createRevisionMemory()) =>
  createVaultSync({ api, encryptionKey: key, now: () => NOW, revisionMemory })

describe('볼트 불러오기 (api-spec 2.3)', () => {
  it('서버에 볼트가 없으면 revision 0의 빈 볼트로 시작한다', async () => {
    const loaded = await syncWith(new FakeVaultApi()).load()

    expect(loaded.revision).toBe(0)
    expect(loaded.data.items).toEqual([])
  })

  it('서버 볼트를 복호화해 revision과 함께 돌려준다', async () => {
    const api = new FakeVaultApi()
    await api.storeAsOtherDevice(addItem(emptyVault(NOW), fields, NOW, 'a'))

    const loaded = await syncWith(api).load()

    expect(loaded.revision).toBe(1)
    expect(loaded.data.items.map((i) => i.id)).toEqual(['a'])
  })
})

describe('볼트 저장과 충돌 병합 (VLT-001)', () => {
  it('저장하면 서버에는 암호문만 가고 새 revision을 돌려받는다', async () => {
    const api = new FakeVaultApi()
    const sync = syncWith(api)
    const loaded = await sync.load()

    const saved = await sync.save(loaded, addItem(loaded.data, fields, NOW, 'a'))

    expect(saved.revision).toBe(1)
    expect(JSON.stringify(api.payload)).not.toContain('"password"')
    expect((await api.current()).items.map((i) => i.id)).toEqual(['a'])
  })

  it('다른 기기가 먼저 저장해 409가 나면 최신 볼트와 병합해 다시 저장한다', async () => {
    const api = new FakeVaultApi()
    const sync = syncWith(api)
    const loaded = await sync.load()
    api.otherDeviceWritesBeforeEachSave = 1

    const saved = await sync.save(loaded, addItem(loaded.data, fields, NOW, 'mine'))

    expect(api.saves).toBe(2)
    expect(saved.revision).toBe(api.revision)
    expect((await api.current()).items.map((i) => i.id).sort()).toEqual(['mine', 'other-0'])
    expect(saved.data.items.map((i) => i.id).sort()).toEqual(['mine', 'other-0'])
  })

  it('재시도 3번까지 충돌하면 성공하고, 4번째 요청까지 충돌하면 충돌 오류를 낸다', async () => {
    const succeeds = new FakeVaultApi()
    succeeds.otherDeviceWritesBeforeEachSave = 3
    const sync = syncWith(succeeds)
    await expect(sync.save(await sync.load(), emptyVault(NOW))).resolves.toBeDefined()
    expect(succeeds.saves).toBe(4)

    const fails = new FakeVaultApi()
    fails.otherDeviceWritesBeforeEachSave = 4
    const failingSync = syncWith(fails)
    await expect(failingSync.save(await failingSync.load(), emptyVault(NOW))).rejects.toBeInstanceOf(VaultSyncConflictError)
    expect(fails.saves).toBe(4)
  })

  it('충돌이 아닌 서버 오류는 재시도하지 않고 그대로 알린다', async () => {
    const api = new FakeVaultApi()
    api.save = async () => {
      api.saves += 1
      throw new ServerError(401, 'UNAUTHORIZED')
    }
    const sync = syncWith(api)

    await expect(sync.save(await sync.load(), emptyVault(NOW))).rejects.toMatchObject({ code: 'UNAUTHORIZED' })
    expect(api.saves).toBe(1)
  })

  it('규격에 맞지 않는 볼트는 서버에 보내지 않는다 (다른 기기가 열 수 없게 되므로)', async () => {
    const api = new FakeVaultApi()
    const sync = syncWith(api)
    const loaded = await sync.load()
    const broken = { ...loaded.data, items: [{ id: 'a', serviceName: 'S', username: 'u', createdAt: NOW.toISOString(), updatedAt: NOW.toISOString() }] }

    await expect(sync.save(loaded, broken)).rejects.toBeInstanceOf(InvalidVaultDataError)
    expect(api.saves).toBe(0)
  })

  it('저장 응답의 revision이 기대 값보다 정확히 1 크지 않으면 믿지 않는다', async () => {
    const api = new FakeVaultApi()
    api.save = async () => 5
    const sync = syncWith(api)

    await expect(sync.save(await sync.load(), emptyVault(NOW))).rejects.toBeInstanceOf(UnexpectedResponseError)
  })

  it('충돌 뒤 받아 온 서버 볼트가 높은 스키마 버전이면 병합한 볼트도 저장하지 않는다', async () => {
    const api = new FakeVaultApi()
    const sync = syncWith(api)
    const loaded = await sync.load()
    await api.storeAsOtherDevice({ version: 2, updatedAt: '2026-10-29T00:00:00Z', items: [] })

    await expect(sync.save(loaded, emptyVault(NOW))).rejects.toBeInstanceOf(ReadOnlyVaultError)
    expect(api.saves).toBe(1)
  })

  it('높은 스키마 버전의 볼트는 저장하지 않는다', async () => {
    const api = new FakeVaultApi()
    await api.storeAsOtherDevice({ version: 2, updatedAt: '2026-10-29T00:00:00Z', items: [] })
    const sync = syncWith(api)
    const loaded = await sync.load()

    expect(loaded.readOnly).toBe(true)
    await expect(sync.save(loaded, loaded.data)).rejects.toBeInstanceOf(ReadOnlyVaultError)
    expect(api.saves).toBe(0)
  })
})

describe('revision 되돌림 감지 (VLT-001)', () => {
  it('서버 revision이 마지막으로 본 값보다 낮으면 멈추고, 사용자가 확인하면 받아들인다', async () => {
    const api = new FakeVaultApi()
    await api.storeAsOtherDevice(emptyVault(NOW))
    await api.storeAsOtherDevice(emptyVault(NOW))
    const sync = syncWith(api)
    await sync.load()
    // 서버를 백업(revision 1)에서 되돌린 상황
    api.revision = 1

    await expect(sync.load()).rejects.toMatchObject(new RevisionRollbackError(2, 1))
    await expect(sync.load({ acceptRollback: true })).resolves.toMatchObject({ revision: 1 })
  })

  it('잠금 해제·재로그인으로 새로 만든 동기화도 이 기기가 본 revision을 기억한다', async () => {
    const api = new FakeVaultApi()
    await api.storeAsOtherDevice(emptyVault(NOW))
    await api.storeAsOtherDevice(emptyVault(NOW))
    const memory = createRevisionMemory()
    await syncWith(api, memory).load()
    api.revision = 1

    await expect(syncWith(api, memory).load()).rejects.toBeInstanceOf(RevisionRollbackError)
  })

  it('불러오기 응답보다 저장이 먼저 끝나도 되돌림으로 오해하지 않는다', async () => {
    const api = new FakeVaultApi()
    await api.storeAsOtherDevice(emptyVault(NOW))
    const sync = syncWith(api)
    const loaded = await sync.load()
    // 불러오기 요청을 보낸 시점의 서버 상태(revision 1)를 늦게 돌려준다
    const stale = await api.get()
    let release!: () => void
    const gate = new Promise<void>((resolve) => (release = resolve))
    api.get = async () => {
      await gate
      return stale
    }

    const slowLoad = sync.load()
    await sync.save(loaded, emptyVault(NOW))
    release()

    await expect(slowLoad).resolves.toMatchObject({ revision: 1 })
  })

  it('서버 볼트가 사라진 것도 되돌림으로 본다', async () => {
    const api = new FakeVaultApi()
    await api.storeAsOtherDevice(emptyVault(NOW))
    const sync = syncWith(api)
    await sync.load()
    api.payload = null
    api.revision = 0

    await expect(sync.load()).rejects.toBeInstanceOf(RevisionRollbackError)
  })

  it('충돌 뒤 받아 온 볼트가 되돌아가 있으면 병합하지 않고 멈춘다', async () => {
    const api = new FakeVaultApi()
    await api.storeAsOtherDevice(emptyVault(NOW))
    await api.storeAsOtherDevice(emptyVault(NOW))
    const sync = syncWith(api)
    const loaded = await sync.load()
    api.revision = 1

    await expect(sync.save(loaded, loaded.data)).rejects.toBeInstanceOf(RevisionRollbackError)
    expect(api.saves).toBe(1)
  })
})

describe('볼트 용량 (VLT-001)', () => {
  const payloadOfSize = (bytes: number): EncryptedVaultPayload => {
    const empty = { version: 1, cipher: 'AES-256-GCM', iv: '', ciphertext: '', tag: '' } as const
    return { ...empty, ciphertext: 'A'.repeat(bytes - JSON.stringify(empty).length) }
  }

  it('페이로드 JSON이 5,242,880바이트이면 허용하고 1바이트라도 넘으면 보내기 전에 거절한다', () => {
    expect(() => assertWithinLimit(payloadOfSize(MAX_PAYLOAD_BYTES))).not.toThrow()
    expect(() => assertWithinLimit(payloadOfSize(MAX_PAYLOAD_BYTES + 1))).toThrow(VaultTooLargeError)
  })
})
