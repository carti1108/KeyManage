// 화면에 보일 항목 고르기와 입력 검사 (VLT-001)
import { activeItems, type ItemFields, type VaultData, type VaultItem } from './vaultData'

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
