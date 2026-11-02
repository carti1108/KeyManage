// 세션 테스트가 함께 쓰는 가짜 서버와 준비 함수
import type { AuthApi, ChangePasswordRequest, ChangedPassword, DeleteAccountRequest, KdfParams, LoginRequest, RegisterRequest, Session } from '../domain/auth/authApi'
import { createAuthService } from '../domain/auth/authService'
import type { EncryptedVaultPayload } from '../domain/crypto/vaultCipher'
import { DeviceStorageError, type DeviceRecord, type DeviceStore } from '../domain/device/deviceRecord'
import type { TabMemory } from '../domain/device/tabMemory'
import { vectors } from '../domain/crypto/testVectors'
import { NetworkError, ServerError } from '../domain/server/serverErrors'
import { createVaultSession } from '../domain/session/vaultSession'
import { createSingleTabFallback } from '../domain/tabs/inMemoryTabs'
import type { Tabs } from '../domain/tabs/tabs'
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
  /** 비밀번호 변경이 함께 교체할 같은 서버의 볼트. setup이 넣는다 */
  vaultApi: FakeVaultApi | null = null
  /** 서버가 아는 인증 해시. 가입·로그인 요청의 값을 따르고, 비밀번호 변경은 이 값과 비교한다 */
  authHash: string | null = null
  changes: { accessToken: string; request: ChangePasswordRequest }[] = []
  /** 비밀번호 변경으로 폐기된 토큰 */
  revoked = new Set<string>()
  /** 설정하면 비밀번호 변경을 이 오류로 거절한다 */
  changeError: unknown = null
  /** 설정하면 비밀번호 변경 요청이 이 약속이 풀릴 때까지 기다린다 */
  changeGate: Promise<void> | null = null
  deletions: { accessToken: string; request: DeleteAccountRequest }[] = []
  /** 설정하면 계정 삭제를 이 오류로 거절한다 */
  deleteError: unknown = null
  /** 설정하면 계정 삭제 요청이 이 약속이 풀릴 때까지 기다린다 */
  deleteGate: Promise<void> | null = null
  /** 계정을 지웠음. 지운 계정으로는 로그인할 수 없고 prelogin은 가짜 salt를 준다 */
  deleted = false
  private issued = 1

  private token(): string {
    return `token-${this.issued}`
  }

  async prelogin(): Promise<KdfParams> {
    if (this.offline) throw new NetworkError()
    return this.kdf
  }
  async register(request: RegisterRequest): Promise<Session> {
    this.registered.push(request)
    this.deleted = false
    this.kdf = { kdf: 'PBKDF2-HMAC-SHA256', iterations: request.kdfIterations, salt: request.kdfSalt }
    this.authHash = request.authHash
    return { accessToken: this.token(), expiresAt: '2026-11-30T00:00:00Z' }
  }
  async login(request: LoginRequest): Promise<Session> {
    if (this.offline) throw new NetworkError()
    this.logins.push(request)
    if (this.deleted) throw new ServerError(401, 'AUTH_FAILED')
    this.authHash = request.authHash
    return { accessToken: this.token(), expiresAt: '2026-11-30T00:00:00Z' }
  }
  async logout(accessToken: string) {
    this.loggedOut.push(accessToken)
  }
  /** 실제 서버처럼 현재 인증 해시와 revision을 확인한 뒤 KDF 값·인증 해시·볼트를 함께 바꾸고 이전 토큰을 모두 폐기한다 */
  async changePassword(accessToken: string, request: ChangePasswordRequest): Promise<ChangedPassword> {
    if (this.offline) throw new NetworkError()
    this.changes.push({ accessToken, request })
    if (this.changeGate) await this.changeGate
    if (this.changeError !== null) throw this.changeError
    if (this.revoked.has(accessToken)) throw new ServerError(401, 'UNAUTHORIZED')
    if (this.authHash !== null && request.currentAuthHash !== this.authHash) throw new ServerError(401, 'AUTH_FAILED')
    const vault = this.vaultApi
    if (vault === null) throw new Error('비밀번호 변경에 쓸 볼트 API가 없음')
    if (request.vault.expectedRevision !== vault.revision) throw new ServerError(409, 'VAULT_CONFLICT')
    vault.payload = request.vault.payload
    vault.revision += 1
    this.kdf = { kdf: 'PBKDF2-HMAC-SHA256', iterations: request.kdf.iterations, salt: request.kdf.salt }
    this.authHash = request.newAuthHash
    for (let n = 1; n <= this.issued; n += 1) this.revoked.add(`token-${n}`)
    this.issued += 1
    return { accessToken: this.token(), expiresAt: '2026-12-02T00:00:00Z', revision: vault.revision }
  }
  /** 실제 서버처럼 인증 해시를 확인한 뒤 계정·볼트를 지우고 이 계정의 토큰을 모두 폐기한다 (api-spec 2.5.6) */
  async deleteAccount(accessToken: string, request: DeleteAccountRequest): Promise<void> {
    if (this.offline) throw new NetworkError()
    this.deletions.push({ accessToken, request })
    if (this.deleteGate) await this.deleteGate
    if (this.deleteError !== null) throw this.deleteError
    if (this.deleted || this.revoked.has(accessToken)) throw new ServerError(401, 'UNAUTHORIZED')
    if (this.authHash !== null && request.authHash !== this.authHash) throw new ServerError(401, 'AUTH_FAILED')
    this.deleted = true
    this.authHash = null
    // 없는 이메일의 prelogin은 서버 비밀키로 만든 가짜 salt를 준다 (api-spec 2.5.1)
    this.kdf = { ...this.kdf, salt: 'ZmFrZS1zYWx0LTE2Ynl0ZQ==' }
    for (let n = 1; n <= this.issued; n += 1) this.revoked.add(`token-${n}`)
    if (this.vaultApi !== null) {
      this.vaultApi.payload = null
      this.vaultApi.revision = 0
    }
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
  /** 남은 횟수만큼 읽기만 실패한다 (열기 시간 초과 등) */
  failLoads = 0
  writes = 0

  async load(): Promise<unknown> {
    if (this.failing || this.failLoads > 0) {
      this.failLoads = Math.max(0, this.failLoads - 1)
      throw new DeviceStorageError(new Error('저장소 오류'))
    }
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
  /** 같은 브라우저의 다른 탭과 공유하는 잠금·알림. 없으면 탭 하나 */
  readonly tabs?: Tabs
  /** 이 탭만의 기억(새로고침해도 남음). 없으면 세션의 기본값 */
  readonly tabMemory?: TabMemory
}

export function setup({ authApi = new FakeAuthApi(), vaultApi = new FakeVaultApi(), deviceStore = new FakeDeviceStore(), tabs = createSingleTabFallback(), tabMemory }: SetupDeps = {}) {
  authApi.vaultApi ??= vaultApi
  const revisionMemory = createRevisionMemory()
  let locks = 0
  let unlocks = 0
  const session = createVaultSession({
    auth: createAuthService(authApi),
    vaultSyncFor: (_token, encryptionKey, memory) => createVaultSync({ api: vaultApi, encryptionKey, now: () => NOW, revisionMemory: memory }),
    revisionMemory,
    deviceStore,
    tabs,
    ...(tabMemory === undefined ? {} : { tabMemory }),
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
    vaultSyncFor: (_token, encryptionKey, memory) => createVaultSync({ api: vaultApi, encryptionKey, now: () => NOW, revisionMemory: memory }),
    revisionMemory,
    deviceStore: new FakeDeviceStore(),
    tabs: createSingleTabFallback(),
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
