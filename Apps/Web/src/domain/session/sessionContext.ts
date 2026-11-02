// 세션 하위 모듈이 함께 읽고 바꾸는 상태 묶음과 상태만 다루는 공통 동작 (SEC-002, SEC-003). 세션 밖으로 내보내지 않는다
import type { DeviceRecord } from '../device/deviceRecord'
import { createDevicePersistence } from '../device/devicePersistence'
import { parseDeviceRecord } from '../device/deviceRecord'
import { ServerError } from '../server/serverErrors'
import type { RevisionMemory, VaultSync } from '../vault/vaultSync'
import {
  SessionExpiredError,
  SessionStateError,
  type Account,
  type OrphanedPending,
  type SyncChangeListener,
  type Unlocked,
  type VaultSessionDeps,
} from './sessionTypes'

/**
 * 세션 하나의 상태. createVaultSession이 하나 만들어 하위 모듈에 넘긴다. 필드는 기다린 뒤 늘 다시 읽는다(구조 분해해 들고 있지 않는다).
 * 키와 평문 볼트(unlocked)가 들어 있으므로 세션 모듈 밖으로 넘기지 않는다
 */
export interface SessionContext {
  readonly deps: VaultSessionDeps
  readonly device: ReturnType<typeof createDevicePersistence>
  account: Account | null
  unlocked: Unlocked | null
  lastEmail: string | null
  orphan: OrphanedPending | null
  /** 잠금·로그아웃·새 로그인마다 올린다. 기다리는 사이 값이 바뀐 작업은 결과를 버린다 */
  epoch: number
  /** 로그아웃·새 로그인마다 올린다. 잠금으로는 바뀌지 않는다 */
  generation: number
  /** 대기 내용 보내기를 한 번에 하나씩 순서대로 한다 */
  pushChain: Promise<unknown>
  /** 충돌이 아닌 분명한 거절을 받으면 잠금 해제·저장·로그인 전까지 자동으로 다시 보내지 않는다 */
  retryBlocked: boolean
  /** 이 탭이 마지막으로 읽거나 쓴 기기 기록. 쓰기 전에 다시 읽어 다른 탭이 그사이 썼는지 본다 (ADR-WEB-001) */
  lastSeen: DeviceRecord | null
  /** 기기 기록을 한 번이라도 읽었는지. 읽지 못한 채 처음 쓸 때 같은 계정의 대기 내용을 덮어쓰지 않는다 (ADR-0020) */
  deviceRead: boolean
  /** 기기 기록을 쓰려고 기다리거나 쓰는 중인 횟수. 떠남 확인에 쓴다 */
  persisting: number
  readonly lockListeners: Set<() => void>
  readonly unlockListeners: Set<() => void>
  readonly syncListeners: Set<SyncChangeListener>
  readonly remoteListeners: Set<() => void>
}

// 세션 토큰 없이 연 볼트의 동기화 객체에 넘기는 값. pushPending·syncAfterUnlock이 토큰 없이는 요청하지 않는다
export const NO_TOKEN = ''

export function createSessionContext(deps: VaultSessionDeps): SessionContext {
  const syncListeners = new Set<SyncChangeListener>()
  return {
    deps,
    device: createDevicePersistence(deps.deviceStore, (error) => {
      for (const listener of syncListeners) listener(error)
    }),
    account: null,
    unlocked: null,
    lastEmail: null,
    orphan: null,
    epoch: 0,
    generation: 0,
    pushChain: Promise.resolve(),
    retryBlocked: false,
    lastSeen: null,
    deviceRead: false,
    persisting: 0,
    lockListeners: new Set(),
    unlockListeners: new Set(),
    syncListeners,
    remoteListeners: new Set(),
  }
}

export function isUnauthorized(error: unknown): boolean {
  return error instanceof ServerError && error.code === 'UNAUTHORIZED'
}

export function notify(listeners: Set<() => void>) {
  for (const listener of listeners) listener()
}

export function notifySync(ctx: SessionContext, error: unknown) {
  for (const listener of ctx.syncListeners) listener(error)
}

export function ensureCurrent(ctx: SessionContext, startedAt: number) {
  if (ctx.epoch !== startedAt) throw new SessionStateError('기다리는 사이 잠금 또는 로그아웃되어 작업을 취소함')
}

/** 이번 로그인에서만 기준을 바꾸는 되돌림 기억. 로그아웃·다른 로그인 뒤 늦게 온 응답은 무시한다 */
function memoryForThisSignIn(ctx: SessionContext): RevisionMemory {
  const { revisionMemory } = ctx.deps
  const owner = ctx.generation
  return {
    get: () => revisionMemory.get(),
    raise: (revision) => {
      if (ctx.generation === owner) revisionMemory.raise(revision)
    },
    reset: (revision) => {
      if (ctx.generation === owner) revisionMemory.reset(revision)
    },
  }
}

export function syncFor(ctx: SessionContext, accessToken: string, encryptionKey: CryptoKey): VaultSync {
  return ctx.deps.vaultSyncFor(accessToken, encryptionKey, memoryForThisSignIn(ctx))
}

/** 쓰지 않게 된 토큰을 서버에서 폐기한다. 실패해도 토큰 만료(30일)로 정리된다 (SEC-002) */
export function revoke(ctx: SessionContext, accessToken: string) {
  ctx.deps.auth.logout(accessToken).catch(() => {
    // 폐기 요청 실패는 사용자에게 알릴 일이 아니다. 토큰은 만료로 정리된다
  })
}

/** 기다리는 사이 잠금·로그아웃됐으면 새로 받은 토큰을 폐기하고 취소한다 */
export function ensureCurrentOrRevoke(ctx: SessionContext, startedAt: number, newToken: string | null) {
  if (ctx.epoch === startedAt) return
  if (newToken !== null) revoke(ctx, newToken)
  ensureCurrent(ctx, startedAt)
}

export function readDevice(ctx: SessionContext): Promise<DeviceRecord | null> {
  return ctx.deps.deviceStore.load().then(parseDeviceRecord)
}

export function clear(ctx: SessionContext) {
  const wasUnlocked = ctx.unlocked !== null
  ctx.epoch += 1
  ctx.generation += 1
  ctx.account = null
  ctx.unlocked = null
  if (wasUnlocked) notify(ctx.lockListeners)
}

/** 로그인 상태를 지운다. 기기 기록은 부른 쪽이 정한다 */
export function resetSignIn(ctx: SessionContext) {
  clear(ctx)
  ctx.orphan = null
  ctx.deps.revisionMemory.reset(0)
  ctx.lastEmail = null
  ctx.lastSeen = null
}

/** 세션 만료. 대기 내용은 남겨 같은 계정으로 다시 로그인하면 올린다 */
export function expire(ctx: SessionContext): SessionExpiredError {
  const { account } = ctx
  if (account?.pendingSync) ctx.orphan = { email: account.email, payload: account.payload, revision: account.revision }
  clear(ctx)
  const error = new SessionExpiredError()
  notifySync(ctx, error)
  return error
}

/** 볼트가 열려 있으면 키와 평문 볼트를 버린다 */
export function lockThisTab(ctx: SessionContext): boolean {
  ctx.epoch += 1
  if (ctx.unlocked === null) return false
  ctx.unlocked = null
  notify(ctx.lockListeners)
  return true
}
