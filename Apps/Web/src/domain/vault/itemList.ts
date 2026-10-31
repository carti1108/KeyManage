// 화면에 보일 항목 고르기와 입력 검사 (VLT-001)
import { activeItems, type ItemChanges, type ItemFields, type VaultData, type VaultItem } from './vaultData'

const collator = new Intl.Collator('ko', { sensitivity: 'base', numeric: true })

/** 삭제되지 않은 항목을 서비스 이름순으로. query는 서비스 이름·아이디·URL에서 대소문자 없이 찾는다 */
export function listItems(data: VaultData, query: string): VaultItem[] {
  const needle = query.trim().toLowerCase()
  // 비밀번호·메모는 검색하지 않는다. 화면에 보이지 않는 값으로 항목이 걸리면 혼란스럽다
  const matches = (item: VaultItem) =>
    needle === '' || [item.serviceName, item.username, item.url].some((value) => value?.toLowerCase().includes(needle))
  return activeItems(data)
    .filter(matches)
    .sort((a, b) => collator.compare(a.serviceName ?? '', b.serviceName ?? '') || a.id.localeCompare(b.id))
}

export type ItemFieldsProblem = 'missingServiceName'

export function checkItemFields(fields: ItemFields): ItemFieldsProblem | null {
  return fields.serviceName.trim() === '' ? 'missingServiceName' : null
}

const REQUIRED_KEYS = ['serviceName', 'username', 'password'] as const
const OPTIONAL_KEYS = ['url', 'notes'] as const

/**
 * 폼에 입력한 값과 기존 항목을 비교해 바뀐 필드만 돌려준다. 바뀐 것이 없으면 null.
 * 병합은 항목 단위라 아무것도 고치지 않고 저장해도 변경 시각이 올라가 그 사이 다른 기기가 고친 항목을 덮어쓴다.
 * 그래서 null이면 저장하지 않는다. 비운 선택 필드는 null(필드 제거)로 바꿔 빈 문자열을 남기지 않는다 (vault-spec 4장)
 */
export function itemChanges(item: VaultItem, fields: ItemFields): ItemChanges | null {
  const changes: Record<string, string | null> = {}
  for (const key of REQUIRED_KEYS) {
    if (fields[key] !== (item[key] ?? '')) changes[key] = fields[key]
  }
  for (const key of OPTIONAL_KEYS) {
    const after = fields[key] ?? ''
    if (after !== (item[key] ?? '')) changes[key] = after === '' ? null : after
  }
  return Object.keys(changes).length === 0 ? null : (changes as ItemChanges)
}
