// 마스터 비밀번호 변경 (SEC-004, ADR-0023)
// 현재 비밀번호를 이 기기의 볼트로 확인하고, 새 salt·지금 반복 횟수로 새 키를 만들어 화면의 볼트(대기 포함)를 다시 암호화한 뒤
// 서버에 한 요청으로 보낸다. 성공하면 새 토큰·키·KDF 값으로 바꾸고 기기 기록을 탭 간 잠금 안에서 새 값으로 덮어쓴다.
// 키·평문·비밀번호는 이 모듈 밖으로 내보내지 않는다
import type { ChangedPassword } from '../auth/authApi'
import type { UnlockKdf } from '../auth/authService'
import { checkNewMasterPassword } from '../auth/masterPassword'
import { toBase64 } from '../crypto/base64'
import { SALT_BYTES, deriveKeys, type DerivedKeys } from '../crypto/keys'
import type { EncryptedVaultPayload } from '../crypto/vaultCipher'
import { ServerError } from '../server/serverErrors'
import { ReadOnlyVaultError, VaultSyncConflictError, type SyncedVault } from '../vault/vaultSync'
import { persist, writeLocked } from './deviceSync'
import { enqueuePush } from './pendingPush'
import { NO_TOKEN, ensureCurrent, expire, isUnauthorized, notifySync, readDevice, rememberKdf, revoke, syncFor, type SessionContext } from './sessionContext'
import { SessionExpiredError, SessionStateError, WrongMasterPasswordError } from './sessionTypes'
import { verifyPassword } from './unlockSteps'

/** 새 마스터 비밀번호가 SEC-002 길이(12자)보다 짧음 */
export class NewMasterPasswordTooShortError extends Error {
  constructor() {
    super('새 마스터 비밀번호가 너무 짧음')
    this.name = 'NewMasterPasswordTooShortError'
  }
}

/** 새 마스터 비밀번호가 지금 비밀번호와 같음 (SEC-004 규칙 2) */
export class SameMasterPasswordError extends Error {
  constructor() {
    super('새 마스터 비밀번호가 지금과 같음')
    this.name = 'SameMasterPasswordError'
  }
}

/** 세션 없이 연 볼트라 서버에 연결되지 않음. 비밀번호는 서버에 연결된 상태에서만 바꾼다 (SEC-004 규칙 1) */
export class PasswordChangeOfflineError extends Error {
  constructor() {
    super('서버에 연결되지 않아 비밀번호를 바꿀 수 없음')
    this.name = 'PasswordChangeOfflineError'
  }
}

/** 서버에 보낸 새 자격 증명. 응답을 받으면 세션에 반영한다 */
interface NewCredentials {
  readonly kdf: UnlockKdf
  readonly encryptionKey: CryptoKey
  /** 보낸 볼트. data는 요청 직전 화면의 볼트이고 payload는 새 키로 암호화한 것이다 */
  readonly sent: SyncedVault
  readonly payload: EncryptedVaultPayload
}

/**
 * @throws SessionStateError(잠김·기다리는 사이 잠금·로그아웃), ReadOnlyVaultError, PasswordChangeOfflineError,
 *   NewMasterPasswordTooShortError, SameMasterPasswordError, WrongMasterPasswordError(이 기기 확인 또는 서버 AUTH_FAILED),
 *   VaultSyncConflictError, SessionExpiredError(로그아웃됨), 그 밖의 ServerError(TOO_MANY_ATTEMPTS 등)·NetworkError(결과를 알 수 없음, SEC-004 규칙 9)
 */
export async function changeMasterPassword(ctx: SessionContext, currentPassword: string, newPassword: string): Promise<void> {
  checkRequest(ctx, currentPassword, newPassword)
  // 앞서 보낸 대기 전송이 옛 토큰으로 늦게 401을 받아 이 탭을 로그아웃시키지 않도록 끝나기를 기다린다
  await ctx.pushChain
  const startedAt = ctx.epoch
  // 다른 탭이 기기 기록에 남긴 대기 내용을 먼저 합쳐 함께 다시 암호화한다 (ADR-WEB-001)
  await persist(ctx)
  ensureCurrent(ctx, startedAt)
  const account = ctx.account
  const opened = ctx.unlocked
  if (account === null || opened === null) throw new SessionStateError('잠금 해제 상태가 아님')
  if (account.accessToken === null) throw new PasswordChangeOfflineError()
  const accessToken = account.accessToken

  const { keys: currentKeys } = await verifyPassword(currentPassword, account)
  let newKeys: DerivedKeys | null = null
  try {
    ensureCurrent(ctx, startedAt)
    const salt = globalThis.crypto.getRandomValues(new Uint8Array(SALT_BYTES))
    newKeys = await deriveKeys(newPassword, salt, account.kdf.iterations)
    const kdf: UnlockKdf = { salt: toBase64(salt), iterations: account.kdf.iterations }
    // 새 토큰은 응답으로 받으므로 암호화에는 토큰 없는 동기화 객체를 쓴다
    const payload = await syncFor(ctx, NO_TOKEN, newKeys.encryptionKey).seal(opened.vault.data)
    ensureCurrent(ctx, startedAt)
    const startedGeneration = ctx.generation
    const creds: NewCredentials = { kdf, encryptionKey: newKeys.encryptionKey, sent: opened.vault, payload }
    const authHashes = { current: currentKeys.authHash, next: newKeys.authHash }
    // 요청부터 반영까지를 대기 전송 줄에 세운다. 그사이 이 탭의 저장이 보내는 전송은 반영 뒤 새 토큰·키로 나가,
    // 옛 토큰으로 401을 받아 성공한 변경을 버리지 않는다
    const run = ctx.pushChain.then(async () => {
      const changed = await request(ctx, accessToken, authHashes.current, authHashes.next, kdf, opened.vault.revision, payload)
      return adopt(ctx, changed, creds, startedGeneration)
    })
    ctx.pushChain = run.catch(() => undefined)
    // 줄 안에서 기다리면 자기 자신을 기다리게 되므로, 대기로 남긴 변경은 줄에서 나온 뒤 보낸다
    if (await run) await pushQuietly(ctx)
  } finally {
    currentKeys.authHash.fill(0)
    newKeys?.authHash.fill(0)
  }
}

function checkRequest(ctx: SessionContext, currentPassword: string, newPassword: string) {
  if (ctx.account === null || ctx.unlocked === null) throw new SessionStateError('잠금 해제 상태가 아님')
  if (ctx.unlocked.vault.readOnly) throw new ReadOnlyVaultError()
  if (ctx.account.accessToken === null) throw new PasswordChangeOfflineError()
  if (checkNewMasterPassword(newPassword, newPassword) !== null) throw new NewMasterPasswordTooShortError()
  if (newPassword === currentPassword) throw new SameMasterPasswordError()
}

async function request(
  ctx: SessionContext,
  accessToken: string,
  currentAuthHash: Uint8Array,
  newAuthHash: Uint8Array,
  kdf: UnlockKdf,
  expectedRevision: number,
  payload: EncryptedVaultPayload,
): Promise<ChangedPassword> {
  const startedGeneration = ctx.generation
  try {
    return await ctx.deps.auth.changePassword(accessToken, currentAuthHash, newAuthHash, kdf, { expectedRevision, payload })
  } catch (error) {
    if (error instanceof ServerError && error.code === 'AUTH_FAILED') throw new WrongMasterPasswordError()
    // 그사이 다른 기기가 저장했다. 다음 동기화로 맞춘 뒤 다시 시도한다
    if (error instanceof ServerError && error.code === 'VAULT_CONFLICT') throw new VaultSyncConflictError()
    if (isUnauthorized(error) && ctx.generation === startedGeneration) throw expire(ctx)
    throw error
  }
}

/**
 * 서버에서 바뀐 자격 증명을 세션과 기기 기록에 반영한다. 서버에서는 이미 바뀌었으므로 잠겼어도 반영한다.
 * 요청을 기다리는 사이 이 탭의 볼트가 바뀌었으면 새 키로 다시 암호화해 대기로 둔다
 * @returns 대기로 남긴 변경이 있는지
 */
async function adopt(ctx: SessionContext, changed: ChangedPassword, creds: NewCredentials, startedGeneration: number): Promise<boolean> {
  let vault: SyncedVault = { revision: changed.revision, data: creds.sent.data, readOnly: false, payload: creds.payload }
  let pendingSync = false
  const latest = ctx.unlocked
  if (latest !== null && latest.vault !== creds.sent) {
    try {
      const payload = await syncFor(ctx, NO_TOKEN, creds.encryptionKey).seal(latest.vault.data)
      vault = { revision: changed.revision, data: latest.vault.data, readOnly: false, payload }
      pendingSync = true
    } catch (error) {
      // 그사이 고친 볼트를 새 키로 암호화하지 못했다(용량 초과 등). 서버에 올린 볼트로 열고 사유를 알린다
      notifySync(ctx, error)
    }
  }
  const target = ctx.account
  if (target === null || ctx.generation !== startedGeneration) {
    // 기다리는 사이 로그아웃·다른 로그인이 있었다. 새 토큰은 폐기하고, 다음 로그인은 새 비밀번호로 한다
    revoke(ctx, changed.accessToken)
    throw new SessionStateError('기다리는 사이 로그아웃되어 작업을 취소함')
  }
  // 새 로그인처럼 세대를 올려, 옛 토큰으로 보낸 늦은 요청의 결과(401 등)가 새 세션을 바꾸지 못하게 한다.
  // 옛 키로 암호화하던 저장도 epoch가 바뀌어 취소된다
  ctx.generation += 1
  ctx.epoch += 1
  ctx.retryBlocked = false
  ctx.deps.revisionMemory.raise(changed.revision)
  ctx.account = { email: target.email, accessToken: changed.accessToken, kdf: creds.kdf, payload: vault.payload ?? creds.payload, revision: changed.revision, pendingSync }
  if (ctx.unlocked !== null) ctx.unlocked = { sync: syncFor(ctx, changed.accessToken, creds.encryptionKey), vault, pendingSync }
  // 옛 KDF 값의 기록은 이 탭이 덮어쓴다. 다른 탭은 바뀐 KDF 값을 보고 로그인 화면으로 간다 (SEC-003 규칙 11, ADR-WEB-001)
  const written = await ctx.deps.tabs.lock.run('device', async () => {
    const current = await readDevice(ctx).catch(() => null)
    return writeLocked(ctx, current)
  })
  // 탭 기억은 기기 기록과 맞춘다. 기록을 쓰지 못했으면 기록에는 옛 KDF 값이 남으므로 기억도 그대로 둔다.
  // 새로고침하면 옛 기록의 잠금 화면으로 열리고, 잠금 해제 때 서버에서 바뀐 것을 알아 로그인 화면으로 간다
  if (written === true) rememberKdf(ctx, target.email, creds.kdf)
  return pendingSync
}

async function pushQuietly(ctx: SessionContext) {
  try {
    if ((await enqueuePush(ctx)) === 'synced') notifySync(ctx, null)
  } catch (error) {
    // 세션 만료는 expire가 이미 알렸다
    if (!(error instanceof SessionExpiredError)) notifySync(ctx, error)
  }
}
