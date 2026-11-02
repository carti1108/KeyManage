// 계정 삭제 (SEC-005, ADR-0024)
// 비밀번호를 이 기기의 볼트로 확인하고 지금 계정의 KDF 값으로 만든 인증 해시를 서버에 보낸다.
// 성공하면 이 기기의 기록과 탭 기억을 지우고, 같은 브라우저의 다른 탭도 로그아웃시킨다. 자동 잠금 같은 기기 설정은 건드리지 않는다.
// 키·평문·비밀번호는 이 모듈 밖으로 내보내지 않는다
import { ServerError } from '../server/serverErrors'
import { expire, isUnauthorized, readDevice, resetSignIn, type SessionContext } from './sessionContext'
import { SessionStateError, WrongMasterPasswordError } from './sessionTypes'
import { verifyPassword } from './unlockSteps'

/** 세션 없이 연 볼트라 서버에 연결되지 않음. 계정은 서버에 연결된 상태에서만 지운다 (SEC-005 규칙 1) */
export class AccountDeletionOfflineError extends Error {
  constructor() {
    super('서버에 연결되지 않아 계정을 지울 수 없음')
    this.name = 'AccountDeletionOfflineError'
  }
}

/** 계정은 서버에서 지웠지만 이 기기의 기록을 지우지 못함. 이 탭은 이미 로그아웃됐다 (SEC-005 사용자 문구) */
export class AccountDeletedDeviceNotClearedError extends Error {
  constructor() {
    super('계정은 지웠지만 기기 기록을 지우지 못함')
    this.name = 'AccountDeletedDeviceNotClearedError'
  }
}

/**
 * @throws SessionStateError(잠김·기다리는 사이 로그아웃), AccountDeletionOfflineError, WrongMasterPasswordError(이 기기 확인 또는 서버 AUTH_FAILED),
 *   SessionExpiredError(로그아웃됨), AccountDeletedDeviceNotClearedError(지웠고 로그아웃됨),
 *   그 밖의 ServerError(TOO_MANY_ATTEMPTS 등)·NetworkError(결과를 알 수 없음, SEC-005 예외 2)
 */
export async function deleteAccount(ctx: SessionContext, password: string): Promise<void> {
  checkRequest(ctx)
  // 앞서 보낸 대기 전송이 끝난 뒤 지운다. 지운 뒤 늦게 나간 저장이 401을 받아 정리 순서가 꼬이지 않게 한다
  await ctx.pushChain
  const startedAt = ctx.epoch
  const account = ctx.account
  if (account === null || ctx.unlocked === null) throw new SessionStateError('잠금 해제 상태가 아님')
  if (account.accessToken === null) throw new AccountDeletionOfflineError()
  const accessToken = account.accessToken

  // 지금 계정의 KDF 값으로 키를 만들어 이 기기의 볼트로 먼저 확인한다. 틀리면 서버에 보내지 않는다
  const { keys } = await verifyPassword(password, account)
  try {
    if (ctx.epoch !== startedAt) throw new SessionStateError('기다리는 사이 잠금 또는 로그아웃되어 작업을 취소함')
    const startedGeneration = ctx.generation
    // 요청을 대기 전송 줄에 세워, 그사이 이 탭의 저장이 옛 토큰으로 나가지 않게 한다
    const run = ctx.pushChain.then(() => request(ctx, accessToken, keys.authHash, startedGeneration))
    ctx.pushChain = run.catch(() => undefined)
    await run
    // 서버에서는 이미 지워졌으니 완료다. 기다리는 사이 잠갔어도 정리한다.
    // 그사이 로그아웃(다른 탭 등)됐으면 지금 상태는 건드리지 않고, 기기 기록이 지운 계정의 것일 때만 지운다
    if (ctx.generation !== startedGeneration) return clearDeletedRecord(ctx, account.email)
    await signOutDeleted(ctx)
  } finally {
    keys.authHash.fill(0)
  }
}

function checkRequest(ctx: SessionContext) {
  if (ctx.account === null || ctx.unlocked === null) throw new SessionStateError('잠금 해제 상태가 아님')
  if (ctx.account.accessToken === null) throw new AccountDeletionOfflineError()
}

async function request(ctx: SessionContext, accessToken: string, authHash: Uint8Array, startedGeneration: number): Promise<void> {
  try {
    await ctx.deps.auth.deleteAccount(accessToken, authHash)
  } catch (error) {
    if (error instanceof ServerError && error.code === 'AUTH_FAILED') throw new WrongMasterPasswordError()
    // 토큰이 무효면 다른 곳에서 이미 지웠거나 세션이 끝났다. 세션 만료 흐름을 따른다
    if (isUnauthorized(error) && ctx.generation === startedGeneration) throw expire(ctx)
    throw error
  }
}

/** 기기 기록이 지운 계정의 것이면 지운다. 읽지 못하면 공용 기기에 남지 않도록 지운다 */
async function clearDeletedRecord(ctx: SessionContext, email: string) {
  const cleared = await ctx.deps.tabs.lock.run('device', async () => {
    const record = await readDevice(ctx).catch(() => undefined)
    if (record === null || (record !== undefined && record.email !== email)) return true
    return ctx.device.clear()
  })
  if (cleared !== true) throw new AccountDeletedDeviceNotClearedError()
}

/** 로그인 상태와 탭 기억을 지우고, 이 기기의 기록을 지운 뒤 다른 탭을 로그아웃시킨다 (SEC-005 규칙 5) */
async function signOutDeleted(ctx: SessionContext) {
  resetSignIn(ctx)
  const cleared = (await ctx.deps.tabs.lock.run('device', () => ctx.device.clear())) === true
  ctx.deps.tabs.channel.post('logged-out')
  if (!cleared) throw new AccountDeletedDeviceNotClearedError()
}
