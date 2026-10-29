// 복호화된 평문 볼트(VaultData) 해석과 항목 편집 (vault-spec 4장, VLT-001)
// 모든 편집은 원본을 바꾸지 않고 새 볼트를 돌려준다. 모르는 필드는 지우지 않는다

/** 이 앱이 아는 볼트 스키마 버전. 더 높은 버전은 읽기 전용으로 연다 */
export const VAULT_SCHEMA_VERSION = 1

export interface VaultItem {
  readonly id: string
  readonly serviceName?: string
  readonly username?: string
  readonly password?: string
  readonly url?: string
  readonly notes?: string
  readonly createdAt: string
  readonly updatedAt: string
  readonly deletedAt?: string
  /** 새 버전 앱이 추가한 필드. 그대로 보존한다 */
  readonly [unknownField: string]: unknown
}

export interface VaultData {
  readonly version: number
  readonly updatedAt: string
  readonly items: readonly VaultItem[]
  readonly [unknownField: string]: unknown
}

export interface ItemFields {
  readonly serviceName: string
  readonly username: string
  readonly password: string
  readonly url?: string
  readonly notes?: string
}

/** 복호화는 됐지만 내용이 규격과 다름. 볼트를 열지 않고 서버 볼트도 덮어쓰지 않는다 */
export class InvalidVaultDataError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'InvalidVaultDataError'
  }
}

/** 수정하려는 항목이 없거나 이미 삭제됨 */
export class VaultItemNotFoundError extends Error {
  constructor(id: string) {
    super(`항목 없음: ${id}`)
    this.name = 'VaultItemNotFoundError'
  }
}

const REQUIRED_ITEM_TEXT = ['serviceName', 'username', 'password'] as const
const OPTIONAL_ITEM_TEXT = ['url', 'notes'] as const
const TIMESTAMPS = ['createdAt', 'updatedAt'] as const

/** @throws InvalidVaultDataError */
export function parseVaultData(json: string): VaultData {
  let raw: unknown
  try {
    raw = JSON.parse(json)
  } catch {
    throw new InvalidVaultDataError('JSON이 아님')
  }
  if (!isRecord(raw)) throw new InvalidVaultDataError('볼트가 객체가 아님')
  if (!Number.isInteger(raw.version) || (raw.version as number) < 1) throw new InvalidVaultDataError('version이 1 이상의 정수가 아님')
  if (typeof raw.updatedAt !== 'string') throw new InvalidVaultDataError('updatedAt이 문자열이 아님')
  if (!Array.isArray(raw.items)) throw new InvalidVaultDataError('items가 배열이 아님')

  const ids = new Set<string>()
  for (const item of raw.items) {
    validateItem(item)
    if (ids.has(item.id)) throw new InvalidVaultDataError(`같은 id가 두 번 나옴: ${item.id}`)
    ids.add(item.id)
  }
  return raw as VaultData
}

export function isReadOnly(data: VaultData): boolean {
  return data.version > VAULT_SCHEMA_VERSION
}

export function emptyVault(now: Date): VaultData {
  return { version: VAULT_SCHEMA_VERSION, updatedAt: formatTime(now), items: [] }
}

/** 삭제 표시가 없는 항목 */
export function activeItems(data: VaultData): VaultItem[] {
  return data.items.filter((item) => item.deletedAt === undefined)
}

export function addItem(data: VaultData, fields: ItemFields, now: Date, id: string = globalThis.crypto.randomUUID()): VaultData {
  const time = formatTime(now)
  return { ...data, updatedAt: time, items: [...data.items, { id, ...fields, createdAt: time, updatedAt: time }] }
}

/** @throws VaultItemNotFoundError */
export function updateItem(data: VaultData, id: string, changes: Partial<ItemFields>, now: Date): VaultData {
  const time = formatTime(now)
  return replaceItem(data, id, time, (item) => ({ ...item, ...changes, updatedAt: time }))
}

/** 내용을 지우고 삭제 표시만 남긴다. 다른 기기와 병합할 때 삭제가 되살아나지 않게 하기 위해서다 (VLT-001) */
export function deleteItem(data: VaultData, id: string, now: Date): VaultData {
  const time = formatTime(now)
  return replaceItem(data, id, time, (item) => ({ id: item.id, createdAt: item.createdAt, updatedAt: time, deletedAt: time }))
}

/** vault-spec의 시각 형식 YYYY-MM-DDTHH:mm:ssZ */
export function formatTime(date: Date): string {
  return date.toISOString().replace(/\.\d{3}Z$/, 'Z')
}

function replaceItem(data: VaultData, id: string, time: string, change: (item: VaultItem) => VaultItem): VaultData {
  const index = data.items.findIndex((item) => item.id === id && item.deletedAt === undefined)
  if (index === -1) throw new VaultItemNotFoundError(id)
  return { ...data, updatedAt: time, items: data.items.map((item, i) => (i === index ? change(item) : item)) }
}

function validateItem(item: unknown): asserts item is VaultItem {
  if (!isRecord(item) || typeof item.id !== 'string') throw new InvalidVaultDataError('항목 id가 문자열이 아님')
  const fail = (field: string) => new InvalidVaultDataError(`항목 ${item.id}의 ${field} 형식이 틀림`)
  for (const field of TIMESTAMPS) if (typeof item[field] !== 'string') throw fail(field)
  if (item.deletedAt !== undefined) {
    if (typeof item.deletedAt !== 'string') throw fail('deletedAt')
    // 삭제된 항목은 내용이 지워져 있으므로 나머지 필드를 요구하지 않는다
    return
  }
  for (const field of REQUIRED_ITEM_TEXT) if (typeof item[field] !== 'string') throw fail(field)
  for (const field of OPTIONAL_ITEM_TEXT) if (item[field] !== undefined && typeof item[field] !== 'string') throw fail(field)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
