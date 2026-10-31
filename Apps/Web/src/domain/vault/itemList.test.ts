import { describe, expect, it } from 'vitest'
import { checkItemFields, listItems } from './itemList'
import { addItem, deleteItem, emptyVault, type VaultData } from './vaultData'

const NOW = new Date('2026-10-31T00:00:00Z')

function vaultWith(...names: string[]): VaultData {
  return names.reduce(
    (data, name, index) => addItem(data, { serviceName: name, username: `${name.toLowerCase()}@me`, password: 'pw', url: `https://${name.toLowerCase()}.com` }, NOW, `id-${index}`),
    emptyVault(NOW),
  )
}

describe('listItems', () => {
  it('삭제된 항목을 빼고 한국어 사전순(한글 먼저, 영문은 대소문자 무시)으로 정렬한다', () => {
    const data = deleteItem(vaultWith('GitHub', '네이버', 'apple', 'Zoom', '구글'), 'id-3', NOW)

    expect(listItems(data, '').map((item) => item.serviceName)).toEqual(['구글', '네이버', 'apple', 'GitHub'])
  })

  it('이름이 같으면 id 순으로 정렬해 순서가 흔들리지 않는다', () => {
    const data = vaultWith('Same', 'Same')

    expect(listItems(data, '').map((item) => item.id)).toEqual(['id-0', 'id-1'])
  })

  it('서비스 이름·아이디·URL에서 대소문자 구분 없이 찾는다', () => {
    const data = vaultWith('GitHub', 'Google', 'Naver')

    expect(listItems(data, '  git ').map((item) => item.serviceName)).toEqual(['GitHub'])
    expect(listItems(data, 'NAVER@').map((item) => item.serviceName)).toEqual(['Naver'])
    expect(listItems(data, 'google.com').map((item) => item.serviceName)).toEqual(['Google'])
  })

  it('비밀번호와 메모는 검색 대상이 아니다', () => {
    const data = addItem(emptyVault(NOW), { serviceName: 'A', username: '', password: 'secret123', notes: 'secret123' }, NOW, 'a')

    expect(listItems(data, 'secret123')).toEqual([])
  })
})

describe('checkItemFields (VLT-001)', () => {
  it('서비스 이름이 공백뿐이면 missingServiceName', () => {
    expect(checkItemFields({ serviceName: '   ', username: '', password: '' })).toBe('missingServiceName')
  })

  it('서비스 이름만 있으면 나머지는 비어도 된다', () => {
    expect(checkItemFields({ serviceName: 'GitHub', username: '', password: '' })).toBeNull()
  })
})
