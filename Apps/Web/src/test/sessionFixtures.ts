// 세션 테스트가 함께 쓰는 가짜 서버와 준비 함수
import type { AuthApi, KdfParams, LoginRequest, RegisterRequest, Session } from '../domain/auth/authApi'
import { createAuthService } from '../domain/auth/authService'
import type { EncryptedVaultPayload } from '../domain/crypto/vaultCipher'
import { vectors } from '../domain/crypto/testVectors'
import { NetworkError, ServerError } from '../domain/server/serverErrors'
import { createVaultSession } from '../domain/session/vaultSession'
import type { RemoteVault, VaultApi } from '../domain/vault/vaultApi'
import { addItem } from '../domain/vault/vaultData'
import { createRevisionMemory, createVaultSync } from '../domain/vault/vaultSync'

export const NOW = new Date('2026-10-31T00:00:00Z')
const vector = vectors.keyDerivationCases[0]
export const PASSWORD = vector.password
export const fields = { serviceName: 'GitHub', username: 'octocat', password: 'pw-1' }

export class FakeAuthApi implements AuthApi {
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

export function setup() {
  const authApi = new FakeAuthApi()
  const vaultApi = new FakeVaultApi()
  const revisionMemory = createRevisionMemory()
  let locks = 0
  let unlocks = 0
  const session = createVaultSession({
    auth: createAuthService(authApi),
    vaultSyncFor: (_token, encryptionKey) => createVaultSync({ api: vaultApi, encryptionKey, now: () => NOW, revisionMemory }),
    revisionMemory,
  })
  session.onLock(() => locks++)
  session.onUnlock(() => unlocks++)
  return { session, authApi, vaultApi, revisionMemory, locks: () => locks, unlocks: () => unlocks }
}

export async function unlockedWithItem() {
  const ctx = setup()
  await ctx.session.login('user@example.com', PASSWORD)
  const state = ctx.session.state()
  if (state.status !== 'unlocked') throw new Error('잠금 해제 상태가 아님')
  await ctx.session.save(addItem(state.vault.data, fields, NOW, 'a'))
  return ctx
}
