// @vitest-environment node
import { describe, expect, it } from 'vitest'
import type { AuthApi, KdfParams, LoginRequest, RegisterRequest, Session } from '../auth/authApi'
import { createAuthService } from '../auth/authService'
import type { EncryptedVaultPayload } from '../crypto/vaultCipher'
import { vectors } from '../crypto/testVectors'
import { NetworkError, ServerError } from '../server/serverErrors'
import type { RemoteVault, VaultApi } from '../vault/vaultApi'
import { addItem, activeItems } from '../vault/vaultData'
import { RevisionRollbackError, createRevisionMemory, createVaultSync } from '../vault/vaultSync'
import { WrongMasterPasswordError, createVaultSession } from './vaultSession'

const NOW = new Date('2026-10-31T00:00:00Z')
const vector = vectors.keyDerivationCases[0]
const PASSWORD = vector.password
const fields = { serviceName: 'GitHub', username: 'octocat', password: 'pw-1' }

class FakeAuthApi implements AuthApi {
  registered: RegisterRequest[] = []
  loggedOut: string[] = []
  kdf: KdfParams = { kdf: 'PBKDF2-HMAC-SHA256', iterations: vectors.kdf.iterations, salt: vector.salt }

  async prelogin(): Promise<KdfParams> {
    return this.kdf
  }
  async register(request: RegisterRequest): Promise<Session> {
    this.registered.push(request)
    this.kdf = { kdf: 'PBKDF2-HMAC-SHA256', iterations: request.kdfIterations, salt: request.kdfSalt }
    return { accessToken: 'token-1', expiresAt: '2026-11-30T00:00:00Z' }
  }
  async login(_request: LoginRequest): Promise<Session> {
    return { accessToken: 'token-1', expiresAt: '2026-11-30T00:00:00Z' }
  }
  async logout(accessToken: string) {
    this.loggedOut.push(accessToken)
  }
}

class FakeVaultApi implements VaultApi {
  revision = 0
  payload: EncryptedVaultPayload | null = null
  saves = 0
  offline = false

  async get(): Promise<RemoteVault | null> {
    if (this.offline) throw new NetworkError()
    return this.payload === null ? null : { revision: this.revision, payload: this.payload }
  }
  async save(expectedRevision: number, payload: EncryptedVaultPayload): Promise<number> {
    if (this.offline) throw new NetworkError()
    this.saves += 1
    if (expectedRevision !== this.revision) throw new ServerError(409, 'VAULT_CONFLICT')
    this.payload = payload
    this.revision += 1
    return this.revision
  }
}

function setup() {
  const authApi = new FakeAuthApi()
  const vaultApi = new FakeVaultApi()
  const revisionMemory = createRevisionMemory()
  let locks = 0
  let unlocks = 0
  const session = createVaultSession({
    auth: createAuthService(authApi),
    vaultSyncFor: (_token, encryptionKey) => createVaultSync({ api: vaultApi, encryptionKey, now: () => NOW, revisionMemory }),
  })
  session.onLock(() => locks++)
  session.onUnlock(() => unlocks++)
  return { session, authApi, vaultApi, locks: () => locks, unlocks: () => unlocks }
}

async function unlockedWithItem() {
  const ctx = setup()
  await ctx.session.login('user@example.com', PASSWORD)
  const state = ctx.session.state()
  if (state.status !== 'unlocked') throw new Error('잠금 해제 상태가 아님')
  await ctx.session.save(addItem(state.vault.data, fields, NOW, 'a'))
  return ctx
}

describe('모든 계정은 볼트를 가진다 (SEC-003)', () => {
  it('가입하면 바로 빈 볼트를 저장하고 잠금 해제 상태가 된다', async () => {
    const { session, vaultApi } = setup()

    await session.register('new@example.com', PASSWORD)

    expect(vaultApi.saves).toBe(1)
    expect(session.state()).toMatchObject({ status: 'unlocked', email: 'new@example.com', vault: { revision: 1 } })
  })

  it('볼트가 없는 계정으로 로그인하면 볼트를 열기 전에 빈 볼트를 저장한다', async () => {
    const { session, vaultApi } = setup()

    await session.login('user@example.com', PASSWORD)

    expect(vaultApi.saves).toBe(1)
    expect(session.state()).toMatchObject({ status: 'unlocked', vault: { revision: 1 } })
  })

  it('볼트가 있으면 그대로 연다', async () => {
    const { vaultApi } = await unlockedWithItem()
    const other = createVaultSession({
      auth: createAuthService(new FakeAuthApi()),
      vaultSyncFor: (_t, encryptionKey) => createVaultSync({ api: vaultApi, encryptionKey, now: () => NOW, revisionMemory: createRevisionMemory() }),
    })

    await other.login('user@example.com', PASSWORD)

    const state = other.state()
    expect(vaultApi.saves).toBe(2)
    expect(state.status === 'unlocked' && activeItems(state.vault.data).map((i) => i.id)).toEqual(['a'])
  })
})

describe('잠금과 잠금 해제 (SEC-003)', () => {
  it('잠그면 볼트 내용이 상태에서 사라지고 잠금 알림이 간다', async () => {
    const { session, locks } = await unlockedWithItem()

    session.lock()

    expect(session.state()).toEqual({ status: 'locked', email: 'user@example.com' })
    expect(locks()).toBe(1)
  })

  it('맞는 마스터 비밀번호면 오프라인에서도 잠금을 풀고 저장해 둔 볼트를 연다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    session.lock()
    vaultApi.offline = true

    const result = await session.unlock(PASSWORD)

    const state = session.state()
    expect(result).toEqual({ offline: true })
    expect(state.status === 'unlocked' && activeItems(state.vault.data).map((i) => i.id)).toEqual(['a'])
  })

  it('온라인이면 잠금을 푼 뒤 바로 서버 볼트를 받아 온다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    session.lock()
    const before = vaultApi.saves

    expect(await session.unlock(PASSWORD)).toEqual({ offline: false })
    expect(vaultApi.saves).toBe(before)
    expect(session.state()).toMatchObject({ status: 'unlocked', vault: { revision: 2 } })
  })

  it('틀린 마스터 비밀번호로는 풀리지 않고 잠긴 상태를 유지한다', async () => {
    const { session } = await unlockedWithItem()
    session.lock()

    await expect(session.unlock('wrong password')).rejects.toBeInstanceOf(WrongMasterPasswordError)
    expect(session.state().status).toBe('locked')
  })

  it('잠금을 푼 뒤에도 이어서 저장할 수 있다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    session.lock()
    await session.unlock(PASSWORD)
    const state = session.state()
    if (state.status !== 'unlocked') throw new Error('잠금 해제 상태가 아님')

    await session.save(addItem(state.vault.data, fields, NOW, 'b'))

    expect(vaultApi.revision).toBe(3)
  })

  it('잠긴 상태나 로그인 전에는 저장할 수 없고, 잠금은 여러 번 해도 알림이 한 번만 간다', async () => {
    const { session, locks } = await unlockedWithItem()
    session.lock()
    session.lock()

    expect(locks()).toBe(1)
    await expect(session.save({ version: 1, updatedAt: '2026-10-31T00:00:00Z', items: [] })).rejects.toThrow()
    expect(() => setup().session.lock()).not.toThrow()
  })
})

describe('자동 잠금 연결용 알림', () => {
  it('로그인·가입·잠금 해제로 볼트가 열릴 때마다 잠금 해제 알림이 간다 (자동 잠금 타이머 재시작용)', async () => {
    const ctx = await unlockedWithItem()
    expect(ctx.unlocks()).toBe(1)

    ctx.session.lock()
    await ctx.session.unlock(PASSWORD)
    expect(ctx.unlocks()).toBe(2)

    const fresh = setup()
    await fresh.session.register('new@example.com', PASSWORD)
    expect(fresh.unlocks()).toBe(1)
  })

  it('저장은 잠금 해제 알림을 다시 보내지 않는다', async () => {
    const { unlocks } = await unlockedWithItem()

    expect(unlocks()).toBe(1)
  })
})

describe('잠금 해제 직후 동기화의 되돌림 경고', () => {
  it('되돌림이 감지되면 잠긴 채로 알리고, 사용자가 받아들이면 그 볼트로 연다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    session.lock()
    // 서버를 백업(revision 1)에서 되돌린 상황
    const restored = { revision: 1, payload: vaultApi.payload! }
    vaultApi.get = async () => restored

    await expect(session.unlock(PASSWORD)).rejects.toBeInstanceOf(RevisionRollbackError)
    expect(session.state().status).toBe('locked')

    await session.unlock(PASSWORD, { acceptRollback: true })
    expect(session.state()).toMatchObject({ status: 'unlocked', vault: { revision: 1 } })
  })
})

describe('로그아웃', () => {
  it('잠금 해제 상태에서 로그아웃하면 잠금 알림도 보낸다 (클립보드 비우기)', async () => {
    const { session, locks } = await unlockedWithItem()

    await session.logout()

    expect(locks()).toBe(1)
  })

  it('서버에 토큰 폐기를 요청하고 로그인 전 상태로 돌아간다', async () => {
    const { session, authApi } = await unlockedWithItem()

    await session.logout()

    expect(authApi.loggedOut).toEqual(['token-1'])
    expect(session.state()).toEqual({ status: 'signedOut' })
  })

  it('서버에 닿지 못해도 이 기기에서는 로그아웃한다', async () => {
    const { session, authApi } = await unlockedWithItem()
    authApi.logout = async () => {
      throw new NetworkError()
    }

    await expect(session.logout()).rejects.toBeInstanceOf(NetworkError)
    expect(session.state()).toEqual({ status: 'signedOut' })
  })
})
