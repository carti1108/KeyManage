// 백업을 열어 백업에 있던 항목을 백업 시점 내용으로 되돌리고 일반 저장 흐름으로 저장한다 (VLT-004 규칙 5~10, ADR-0022 결정 5)
// 동기화 병합(mergeVaults)과 달리 복원은 사용자의 명시적 되돌리기이므로, 되돌린 항목의 수정 시각을 복원 시각으로 올려 병합에서도 이기게 한다
import type { SessionState } from '../session/sessionTypes'
import { SessionStateError } from '../session/sessionTypes'
import { InvalidVaultDataError, formatTime, type VaultData, type VaultItem } from '../vault/vaultData'
import { ReadOnlyVaultError } from '../vault/vaultSync'
import { openBackup, type VaultBackup } from './backupFile'

/** 복원이 쓰는 세션 기능. 저장은 서버에 닿지 못하면 동기화 대기로 둔다 (VLT-002) */
export interface RestoreTarget {
  state(): SessionState
  save(data: VaultData): Promise<void>
}

export interface RestoredVault {
  readonly data: VaultData
  /** 실제로 되돌리거나 되살리거나 더한 항목 수 */
  readonly restored: number
}

type UnlockedState = Extract<SessionState, { status: 'unlocked' }>

// 같은 항목의 변경 시각은 늘 이전보다 커야 병합에서 이긴다 (vaultData의 nextChangeTime과 같은 규칙)
const SECOND_MS = 1000
// "보이는 내용"을 비교할 때 빼는 필드. 시각은 내용이 아니고, 삭제 표시는 따로 본다
const NOT_CONTENT = new Set(['createdAt', 'updatedAt', 'deletedAt'])

/**
 * @param now 복원 시각. 되돌린 항목의 수정 시각이 된다
 * @returns 복원한 항목 수(restoreItems). 0이면 저장하지 않는다
 * @throws ReadOnlyVaultError, SessionStateError(잠김·계정 바뀜), openBackup의 오류, save의 오류
 */
export async function restoreBackupInto(target: RestoreTarget, backup: VaultBackup, password: string, now: Date): Promise<number> {
  const before = writableUnlocked(target)
  const backupData = await openBackup(backup, password)
  // 키를 만드는 사이 잠기거나 다른 계정으로 바뀌었으면 복원하지 않는다. 그사이 고친 내용은 지금 볼트에 있다
  const current = writableUnlocked(target)
  if (current.email !== before.email) throw new SessionStateError('복원하는 사이 계정이 바뀜')
  const { data, restored } = restoreItems(current.vault.data, backupData, now)
  if (restored > 0) await target.save(data)
  return restored
}

/**
 * 백업에서 지워지지 않은 항목을 백업 시점 내용으로 되돌린다. 고친 항목은 백업 내용으로, 지운 항목은 되살리고,
 * 지금 볼트에 없는 항목은 더한다. 수정 시각은 복원 시각(지금 항목보다 늦게)으로 정하고 삭제 표시는 없앤다.
 * 백업에서 지워진 항목, 백업에 없는 항목, 보이는 내용이 백업과 같은 항목은 그대로 둔다.
 * 항목의 모르는 필드는 백업 것을, 볼트 최상위 필드는 지금 볼트 것을 쓴다
 * @returns 바뀐 것이 없으면 data는 current 그대로다
 * @throws InvalidVaultDataError 백업에 같은 항목 id가 두 번 이상 있음(VLT-001 형식 오류). 지금 볼트를 바꾸기 전에 거절한다
 */
export function restoreItems(current: VaultData, backup: VaultData, now: Date): RestoredVault {
  assertUniqueIds(backup)
  const currentById = new Map(current.items.map((item) => [item.id, item]))
  const replaced = new Map<string, VaultItem>()
  const added: VaultItem[] = []
  for (const saved of backup.items) {
    if (!isActive(saved)) continue
    const existing = currentById.get(saved.id)
    if (existing !== undefined && isActive(existing) && sameContent(existing, saved)) continue
    const restored = { ...saved, updatedAt: restoreTime(existing, now) }
    if (existing === undefined) added.push(restored)
    else replaced.set(saved.id, restored)
  }
  const count = replaced.size + added.length
  if (count === 0) return { data: current, restored: 0 }

  const items = [...current.items.map((item) => replaced.get(item.id) ?? item), ...added]
  const updatedAt = [current.updatedAt, ...items.map((item) => item.updatedAt)].reduce((a, b) => (toMillis(b) > toMillis(a) ? b : a))
  return { data: { ...current, updatedAt, items }, restored: count }
}

// openBackup(parseVaultData)도 같은 검사를 하지만, 이 함수만 쓰는 곳에서도 중복 항목이 그대로 더해지지 않게 한다
function assertUniqueIds(data: VaultData): void {
  const ids = new Set<string>()
  for (const { id } of data.items) {
    if (ids.has(id)) throw new InvalidVaultDataError(`같은 id가 두 번 나옴: ${id}`)
    ids.add(id)
  }
}

function restoreTime(existing: VaultItem | undefined, now: Date): string {
  if (existing === undefined) return formatTime(now)
  const last = Math.max(toMillis(existing.updatedAt), existing.deletedAt === undefined ? -Infinity : toMillis(existing.deletedAt))
  return formatTime(new Date(Math.max(now.getTime(), last + SECOND_MS)))
}

/** 시각·삭제 표시를 뺀 모든 필드(모르는 필드 포함)를 필드 순서와 상관없이 비교한다 */
function sameContent(a: VaultItem, b: VaultItem): boolean {
  return canonical(contentOf(a)) === canonical(contentOf(b))
}

function contentOf(item: VaultItem): Record<string, unknown> {
  return Object.fromEntries(Object.entries(item).filter(([key]) => !NOT_CONTENT.has(key)))
}

function canonical(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(canonical).join(',')}]`
  if (typeof value === 'object' && value !== null) {
    const entries = Object.entries(value).sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
    return `{${entries.map(([key, inner]) => `${JSON.stringify(key)}:${canonical(inner)}`).join(',')}}`
  }
  return JSON.stringify(value) ?? 'undefined'
}

function isActive(item: VaultItem): boolean {
  return item.deletedAt === undefined
}

// 읽을 수 없는 시각은 가장 오래된 것으로 본다 (mergeVaults와 같다)
function toMillis(time: string): number {
  const millis = Date.parse(time)
  return Number.isNaN(millis) ? -Infinity : millis
}

function writableUnlocked(target: RestoreTarget): UnlockedState {
  const state = target.state()
  if (state.status !== 'unlocked') throw new SessionStateError('잠금 해제 상태가 아님')
  if (state.vault.readOnly) throw new ReadOnlyVaultError()
  return state
}
