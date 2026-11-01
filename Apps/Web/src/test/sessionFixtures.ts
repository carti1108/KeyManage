// 세션 테스트가 함께 쓰는 가짜 서버와 준비 함수
import type { AuthApi, KdfParams, LoginRequest, RegisterRequest, Session } from '../domain/auth/authApi'
import { createAuthService } from '../domain/auth/authService'
import type { EncryptedVaultPayload } from '../domain/crypto/vaultCipher'
import { DeviceStorageError, type DeviceRecord, type DeviceStore } from '../domain/device/deviceRecord'
import { vectors } from '../domain/crypto/testVectors'
import { NetworkError, ServerError } from '../domain/server/serverErrors'
import { createVaultSession } from '../domain/session/vaultSession'
import type { RemoteVault, VaultApi } from '../domain/vault/vaultApi'
import { activeItems, addItem } from '../domain/vault/vaultData'
import { createRevisionMemory, createVaultSync } from '../domain/vault/vaultSync'

export const NOW = new Date('2026-10-31T00:00:00Z')
const vector = vectors.keyDerivationCases[0]
export const PASSWORD = vector.password
export const fields = { serviceName: 'GitHub', username: 'octocat', password: 'pw-1' }

export class FakeAuthApi implements AuthApi {
  registered: RegisterRequest[] = []
  loggedOut: string[] = []
  kdf: KdfParams = { kdf: 'PBKDF2-HMAC-SHA256', iterations: vectors.kdf.iterations, salt: vector.salt }
  logins: LoginRequest[] = []
  offline = false

  async prelogin(): Promise<KdfParams> {
    if (this.offline) throw new NetworkError()
    return this.kdf
  }
  async register(request: RegisterRequest): Promise<Session> {
    this.registered.push(request)
    this.kdf = { kdf: 'PBKDF2-HMAC-SHA256', iterations: request.kdfIterations, salt: request.kdfSalt }
    return { accessToken: 'token-1', expiresAt: '2026-11-30T00:00:00Z' }
  }
  async login(request: LoginRequest): Promise<Session> {
    if (this.offline) throw new NetworkError()
    this.logins.push(request)
    return { accessToken: 'token-1', expiresAt: '2026-11-30T00:00:00Z' }
  }
  async logout(accessToken: string) {
    this.loggedOut.push(accessToken)
  }
}

export class FakeVaultApi implements VaultApi {
  revision = 0
  payload: EncryptedVaultPayload | null = null
  saves = 0
  offline = false
  /** 설정하면 저장 요청이 이 약속이 풀릴 때까지 기다린다 */
  gate: Promise<void> | null = null

  async get(): Promise<RemoteVault | null> {
    if (this.offline) throw new NetworkError()
    return this.payload === null ? null : { revision: this.revision, payload: this.payload }
  }
  async save(expectedRevision: number, payload: EncryptedVaultPayload): Promise<number> {
    if (this.offline) throw new NetworkError()
    if (this.gate) await this.gate
    this.saves += 1
    if (expectedRevision !== this.revision) throw new ServerError(409, 'VAULT_CONFLICT')
    this.payload = payload
    this.revision += 1
    return this.revision
  }
}

/** 메모리에 한 계정의 기록을 두는 기기 저장소. 실제처럼 복사본을 주고받는다 */
export class FakeDeviceStore implements DeviceStore {
  stored: unknown = undefined
  failing = false
  writes = 0

  async load(): Promise<unknown> {
    if (this.failing) throw new DeviceStorageError(new Error('저장소 오류'))
    return structuredClone(this.stored)
  }
  async save(record: DeviceRecord): Promise<void> {
    if (this.failing) throw new DeviceStorageError(new Error('저장소 오류'))
    this.writes += 1
    this.stored = structuredClone(record)
  }
  async clear(): Promise<void> {
    if (this.failing) throw new DeviceStorageError(new Error('저장소 오류'))
    this.stored = undefined
  }

  get record(): DeviceRecord | undefined {
    return this.stored as DeviceRecord | undefined
  }
}

interface SetupDeps {
  readonly authApi?: FakeAuthApi
  readonly vaultApi?: FakeVaultApi
  readonly deviceStore?: FakeDeviceStore
}

export function setup({ authApi = new FakeAuthApi(), vaultApi = new FakeVaultApi(), deviceStore = new FakeDeviceStore() }: SetupDeps = {}) {
  const revisionMemory = createRevisionMemory()
  let locks = 0
  let unlocks = 0
  const session = createVaultSession({
    auth: createAuthService(authApi),
    vaultSyncFor: (_token, encryptionKey) => createVaultSync({ api: vaultApi, encryptionKey, now: () => NOW, revisionMemory }),
    revisionMemory,
    deviceStore,
  })
  session.onLock(() => locks++)
  session.onUnlock(() => unlocks++)
  return { session, authApi, vaultApi, deviceStore, revisionMemory, locks: () => locks, unlocks: () => unlocks }
}

/** 같은 서버와 기기 저장소로 앱을 새로 연다(새로고침·재시작). 메모리 상태는 모두 사라진다 */
export async function restart(ctx: Pick<ReturnType<typeof setup>, 'authApi' | 'vaultApi' | 'deviceStore'>) {
  const next = setup({ authApi: ctx.authApi, vaultApi: ctx.vaultApi, deviceStore: ctx.deviceStore })
  await next.session.restore()
  return next
}

export async function unlockedWithItem() {
  const ctx = setup()
  await ctx.session.login('user@example.com', PASSWORD)
  const state = ctx.session.state()
  if (state.status !== 'unlocked') throw new Error('잠금 해제 상태가 아님')
  await ctx.session.save(addItem(state.vault.data, fields, NOW, 'a'))
  return ctx
}

/** 다른 기기(자기 되돌림 기준을 가진 별도 세션)로 같은 서버 볼트를 연다 */
async function openOnOtherDevice(vaultApi: FakeVaultApi) {
  const revisionMemory = createRevisionMemory()
  const other = createVaultSession({
    auth: createAuthService(new FakeAuthApi()),
    vaultSyncFor: (_token, encryptionKey) => createVaultSync({ api: vaultApi, encryptionKey, now: () => NOW, revisionMemory }),
    revisionMemory,
    deviceStore: new FakeDeviceStore(),
  })
  await other.login('device-2@example.com', PASSWORD)
  const state = other.state()
  if (state.status !== 'unlocked') throw new Error('잠금 해제 상태가 아님')
  return { other, data: state.vault.data }
}

/** 서버 볼트의 살아 있는 항목 id (정렬) */
export async function serverItemIds(vaultApi: FakeVaultApi): Promise<string[]> {
  const { data } = await openOnOtherDevice(vaultApi)
  return activeItems(data)
    .map((item) => item.id)
    .sort()
}

/** 다른 기기에서 항목을 하나 더해 저장한다 */
export async function saveFromOtherDevice(vaultApi: FakeVaultApi, id: string): Promise<void> {
  const { other, data } = await openOnOtherDevice(vaultApi)
  await other.save(addItem(data, { ...fields, serviceName: id }, NOW, id))
}
