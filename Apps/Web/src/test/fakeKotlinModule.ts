// Kotlin 빌드 결과 없이 어댑터를 검증하는 가짜 KotlinModule. Kotlin 예외는 FakeKotlinThrowable로 흉내 낸다
import { vi, type Mock } from 'vitest'
import type { JsErrorInfo, JsSessionState, KotlinModule, KotlinVaultSession } from '../adapters/kotlin/kotlinTypes'

/** Kotlin 예외처럼 Promise를 reject하는 값. errorInfo가 이것만 읽는다 */
export class FakeKotlinThrowable {
  readonly info: JsErrorInfo
  constructor(kind: string, fields: Partial<Omit<JsErrorInfo, 'kind'>> = {}) {
    this.info = { kind, message: null, status: null, code: null, retryAfterSeconds: null, detailJson: null, ...fields }
  }
}

export const SIGNED_OUT_STATE: JsSessionState = {
  status: 'signedOut',
  email: null,
  pendingSync: false,
  signedIn: false,
  storedOnDevice: true,
  revision: 0,
  readOnly: false,
  vaultJson: null,
  payloadJson: null,
}

type SessionMocks = { [K in keyof KotlinVaultSession]: Mock<KotlinVaultSession[K]> }

export interface FakeKotlin {
  readonly module: KotlinModule & { createKotlinSession: Mock<KotlinModule['createKotlinSession']>; errorInfo: Mock<KotlinModule['errorInfo']> }
  readonly session: SessionMocks
  /** onXxx로 등록된 리스너를 꺼내 부른다 */
  readonly listeners: {
    readonly remote: Set<() => void>
    readonly lock: Set<() => void>
    readonly unlock: Set<() => void>
    readonly sync: Set<(error: unknown) => void>
  }
  /** 각 onXxx가 돌려준 해제 함수 */
  readonly unsubscribes: Mock<() => void>[]
}

export function createFakeKotlin(initial: JsSessionState = SIGNED_OUT_STATE): FakeKotlin {
  const listeners = { remote: new Set<() => void>(), lock: new Set<() => void>(), unlock: new Set<() => void>(), sync: new Set<(error: unknown) => void>() }
  const unsubscribes: Mock<() => void>[] = []
  function subscribe<L>(set: Set<L>) {
    return (listener: L) => {
      set.add(listener)
      const unsubscribe = vi.fn(() => void set.delete(listener))
      unsubscribes.push(unsubscribe)
      return unsubscribe
    }
  }
  const session: SessionMocks = {
    state: vi.fn(() => initial),
    register: vi.fn(async () => undefined),
    login: vi.fn(async () => undefined),
    lock: vi.fn(),
    unlock: vi.fn(async () => false),
    reconnect: vi.fn(async () => undefined),
    save: vi.fn(async () => undefined),
    exportBackup: vi.fn(async () => '{}'),
    restoreBackup: vi.fn(async () => 0),
    changeMasterPassword: vi.fn(async () => undefined),
    deleteAccount: vi.fn(async () => undefined),
    hasUnsavedChanges: vi.fn(() => false),
    restore: vi.fn(async () => undefined),
    retryPendingSync: vi.fn(async () => undefined),
    logout: vi.fn(async () => undefined),
    pendingOnDevice: vi.fn(async () => false),
    onRemoteChange: vi.fn(subscribe(listeners.remote)),
    onLock: vi.fn(subscribe(listeners.lock)),
    onUnlock: vi.fn(subscribe(listeners.unlock)),
    onSyncChange: vi.fn(subscribe(listeners.sync)),
  }
  const module = {
    createKotlinSession: vi.fn<KotlinModule['createKotlinSession']>(() => session),
    errorInfo: vi.fn<KotlinModule['errorInfo']>((error) => (error instanceof FakeKotlinThrowable ? error.info : null)),
  }
  return { module, session, listeners, unsubscribes }
}
