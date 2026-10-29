import { describe, expect, it } from 'vitest'
import {
  InvalidVaultDataError,
  activeItems,
  addItem,
  deleteItem,
  emptyVault,
  isReadOnly,
  parseVaultData,
  updateItem,
  type VaultData,
} from './vaultData'
import { mergeVaults } from './mergeVaults'

const T0 = new Date('2026-10-29T00:00:00Z')
const T1 = new Date('2026-10-29T00:01:00Z')
const T2 = new Date('2026-10-29T00:02:00Z')

const fields = { serviceName: 'GitHub', username: 'octocat', password: 'pw-1', url: 'https://github.com', notes: '메모' }

function vaultWith(...items: VaultData['items']): VaultData {
  return { version: 1, updatedAt: '2026-10-29T00:00:00Z', items }
}

describe('평문 볼트 해석 (vault-spec 4장)', () => {
  it('규격에 맞는 볼트를 읽고, 모르는 필드는 그대로 남긴다', () => {
    const json = JSON.stringify({
      version: 1,
      updatedAt: '2026-10-29T00:00:00Z',
      futureTop: { a: 1 },
      items: [{ id: 'a', serviceName: 'S', username: 'u', password: 'p', createdAt: 'x', updatedAt: 'y', totp: 'SECRET' }],
    })

    const data = parseVaultData(json)

    expect(data.futureTop).toEqual({ a: 1 })
    expect(data.items[0].totp).toBe('SECRET')
    expect(JSON.parse(JSON.stringify(data))).toEqual(JSON.parse(json))
  })

  it('삭제된 항목은 id와 시각만 있어도 읽는다', () => {
    const json = JSON.stringify(vaultWith({ id: 'a', createdAt: 'x', updatedAt: 'y', deletedAt: 'z' }))

    expect(parseVaultData(json).items).toHaveLength(1)
  })

  it('필수 필드가 없거나 타입이 틀리거나 JSON이 아니면 형식 오류로 거절한다', () => {
    const bad = [
      'not json',
      JSON.stringify({ updatedAt: 'x', items: [] }),
      JSON.stringify({ version: 0, updatedAt: 'x', items: [] }),
      JSON.stringify({ version: 1.5, updatedAt: 'x', items: [] }),
      JSON.stringify({ version: 1, updatedAt: 'x', items: {} }),
      JSON.stringify(vaultWith({ id: 'a', username: 'u', password: 'p', createdAt: 'x', updatedAt: 'y' } as never)),
      JSON.stringify(vaultWith({ id: 'a', serviceName: 'S', username: 'u', password: 1, createdAt: 'x', updatedAt: 'y' } as never)),
      JSON.stringify(vaultWith({ id: 'a', serviceName: 'S', username: 'u', password: 'p', url: 3, createdAt: 'x', updatedAt: 'y' } as never)),
      JSON.stringify(vaultWith({ id: 'a', createdAt: 'x', updatedAt: 'y', deletedAt: 5 } as never)),
    ]
    for (const json of bad) {
      expect(() => parseVaultData(json), json).toThrow(InvalidVaultDataError)
    }
  })

  it('같은 id가 두 번 나오면 형식 오류로 거절한다', () => {
    const item = { id: 'a', serviceName: 'S', username: 'u', password: 'p', createdAt: 'x', updatedAt: 'y' }

    expect(() => parseVaultData(JSON.stringify(vaultWith(item, item)))).toThrow(InvalidVaultDataError)
  })

  it('앱이 아는 것보다 높은 스키마 버전은 읽기 전용이다', () => {
    expect(isReadOnly(parseVaultData(JSON.stringify({ version: 2, updatedAt: 'x', items: [] })))).toBe(true)
    expect(isReadOnly(emptyVault(T0))).toBe(false)
  })
})

describe('항목 추가·수정·삭제', () => {
  it('추가한 항목은 생성·수정 시각이 같고 볼트 수정 시각도 바뀐다', () => {
    const data = addItem(emptyVault(T0), fields, T1, 'id-1')

    expect(data.items[0]).toEqual({ id: 'id-1', ...fields, createdAt: '2026-10-29T00:01:00Z', updatedAt: '2026-10-29T00:01:00Z' })
    expect(data.updatedAt).toBe('2026-10-29T00:01:00Z')
  })

  it('수정은 바꾼 필드와 수정 시각만 바꾸고 모르는 필드는 남긴다', () => {
    const before = vaultWith({ id: 'a', ...fields, createdAt: 'c', updatedAt: 'u', totp: 'SECRET' })

    const after = updateItem(before, 'a', { password: 'pw-2' }, T2)

    expect(after.items[0]).toEqual({ id: 'a', ...fields, password: 'pw-2', createdAt: 'c', updatedAt: '2026-10-29T00:02:00Z', totp: 'SECRET' })
    // 원본은 바뀌지 않는다
    expect(before.items[0].password).toBe('pw-1')
  })

  it('삭제한 항목에는 비밀번호·메모·모르는 필드가 남지 않고 목록에서 빠진다', () => {
    const before = vaultWith({ id: 'a', ...fields, createdAt: 'c', updatedAt: 'u', totp: 'SECRET' })

    const after = deleteItem(before, 'a', T2)

    expect(after.items).toEqual([{ id: 'a', createdAt: 'c', updatedAt: '2026-10-29T00:02:00Z', deletedAt: '2026-10-29T00:02:00Z' }])
    expect(activeItems(after)).toEqual([])
  })

  it('없는 항목이나 삭제된 항목은 수정하지 않는다', () => {
    const data = deleteItem(addItem(emptyVault(T0), fields, T0, 'a'), 'a', T1)

    expect(() => updateItem(data, 'a', { password: 'x' }, T2)).toThrow()
    expect(() => updateItem(data, 'missing', { password: 'x' }, T2)).toThrow()
  })
})

describe('항목 병합 (VLT-001)', () => {
  const item = (id: string, updatedAt: string, extra: object = {}) => ({ id, ...fields, createdAt: 'c', updatedAt, ...extra })

  it('한쪽에만 있는 항목은 모두 남는다', () => {
    const merged = mergeVaults(vaultWith(item('local', 'T1')), vaultWith(item('remote', 'T1')))

    expect(merged.items.map((i) => i.id).sort()).toEqual(['local', 'remote'])
  })

  it('양쪽 모두 수정했으면 나중에 수정한 쪽을 고른다', () => {
    const local = vaultWith(item('a', '2026-10-29T00:02:00Z', { password: 'local' }))
    const remote = vaultWith(item('a', '2026-10-29T00:01:00Z', { password: 'remote' }))

    expect(mergeVaults(local, remote).items[0].password).toBe('local')
    expect(mergeVaults(remote, local).items[0].password).toBe('local')
  })

  it('삭제가 수정보다 나중이면 삭제가, 수정이 나중이면 수정이 남는다', () => {
    const edited = vaultWith(item('a', '2026-10-29T00:01:00Z'))
    const deletedLater = vaultWith({ id: 'a', createdAt: 'c', updatedAt: '2026-10-29T00:02:00Z', deletedAt: '2026-10-29T00:02:00Z' })
    const deletedEarlier = vaultWith({ id: 'a', createdAt: 'c', updatedAt: '2026-10-29T00:00:30Z', deletedAt: '2026-10-29T00:00:30Z' })

    expect(mergeVaults(edited, deletedLater).items[0].deletedAt).toBeDefined()
    expect(mergeVaults(deletedLater, edited).items[0].deletedAt).toBeDefined()
    expect(mergeVaults(edited, deletedEarlier).items[0].deletedAt).toBeUndefined()
  })

  it('최종 변경 시각이 같으면 서버 쪽을 고른다', () => {
    const local = vaultWith(item('a', '2026-10-29T00:01:00Z', { password: 'local' }))
    const remote = vaultWith(item('a', '2026-10-29T00:01:00Z', { password: 'remote' }))

    expect(mergeVaults(local, remote).items[0].password).toBe('remote')
  })

  it('볼트 수정 시각은 둘 중 늦은 값, 모르는 최상위 필드는 서버 쪽을 따른다', () => {
    const local = { ...vaultWith(), updatedAt: '2026-10-29T00:05:00Z', extra: 'local' }
    const remote = { ...vaultWith(), updatedAt: '2026-10-29T00:01:00Z', extra: 'remote' }

    const merged = mergeVaults(local, remote)

    expect(merged.updatedAt).toBe('2026-10-29T00:05:00Z')
    expect(merged.extra).toBe('remote')
  })
})
