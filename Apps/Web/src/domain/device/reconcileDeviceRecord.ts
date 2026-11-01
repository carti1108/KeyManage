// 기기 기록을 쓰기 직전, 다른 탭이 그사이 쓴 기록과 어떻게 맞출지 정한다 (ADR-WEB-001)
// 브라우저를 모르는 순수 판단이다. 탭 간 잠금 안에서 기록을 다시 읽은 뒤 부른다
import type { UnlockKdf } from '../auth/authService'
import type { EncryptedVaultPayload } from '../crypto/vaultCipher'
import type { DeviceRecord } from './deviceRecord'

export type Reconciliation =
  /** 그대로 써도 된다. 기록이 이 탭이 마지막으로 본 것과 같거나, 처음 쓴다 */
  | { readonly kind: 'write' }
  /** 다른 탭이 썼다. 잠금 해제 상태라 병합한 뒤 쓴다 */
  | { readonly kind: 'merge'; readonly record: DeviceRecord }
  /** 잠긴 탭인데 다른 탭이 썼다. 키가 없어 병합할 수 없으니 쓰지 않는다 */
  | { readonly kind: 'skip' }
  /** 이 탭이 본 기록이 사라졌거나 다른 계정의 기록이다. 다른 탭에서 로그아웃·계정 전환이 있었다 */
  | { readonly kind: 'loggedOutElsewhere' }
  /** 같은 이메일인데 KDF 값이 다르다. 다른 탭에서 바뀐 비밀번호로 로그인했다 (SEC-003) */
  | { readonly kind: 'passwordChanged' }

export interface ReconcileInput {
  /** 잠금 안에서 방금 읽은 기록 */
  readonly current: DeviceRecord | null
  /** 이 탭이 마지막으로 읽거나 쓴 기록 */
  readonly lastSeen: DeviceRecord | null
  readonly email: string
  readonly kdf: UnlockKdf
  readonly unlocked: boolean
}

export interface SyncFields {
  readonly revision: number
  readonly lastSeenRevision: number
  readonly pendingSync: boolean
}

export function sameKdf(a: UnlockKdf, b: UnlockKdf): boolean {
  return a.salt === b.salt && a.iterations === b.iterations
}

export function samePayload(a: EncryptedVaultPayload, b: EncryptedVaultPayload): boolean {
  return a.version === b.version && a.cipher === b.cipher && a.iv === b.iv && a.ciphertext === b.ciphertext && a.tag === b.tag
}

export function sameRecord(a: DeviceRecord, b: DeviceRecord): boolean {
  return (
    a.email === b.email &&
    sameKdf(a.kdf, b.kdf) &&
    samePayload(a.payload, b.payload) &&
    a.revision === b.revision &&
    a.lastSeenRevision === b.lastSeenRevision &&
    a.pendingSync === b.pendingSync
  )
}

export function reconcileDeviceRecord({ current, lastSeen, email, kdf, unlocked }: ReconcileInput): Reconciliation {
  if (current === null) return lastSeen === null ? { kind: 'write' } : { kind: 'loggedOutElsewhere' }
  // 이 탭이 본 그대로면 다른 탭이 쓰지 않았다. 이 탭이 새 비밀번호로 로그인해 옛 KDF 기록을 바꾸는 경우도 여기서 쓴다
  if (lastSeen !== null && sameRecord(current, lastSeen)) return { kind: 'write' }
  if (current.email !== email) return { kind: 'loggedOutElsewhere' }
  if (!sameKdf(current.kdf, kdf)) return { kind: 'passwordChanged' }
  return unlocked ? { kind: 'merge', record: current } : { kind: 'skip' }
}

/** 병합 결과의 동기화 필드. pendingSync는 OR, revision·lastSeenRevision은 큰 값(되돌림 기준을 낮추지 않음) */
export function mergeSyncFields(mine: SyncFields, theirs: DeviceRecord): SyncFields {
  return {
    revision: Math.max(mine.revision, theirs.revision),
    lastSeenRevision: Math.max(mine.lastSeenRevision, theirs.lastSeenRevision),
    pendingSync: mine.pendingSync || theirs.pendingSync,
  }
}
