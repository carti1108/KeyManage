// 다른 탭이 쓴 기기 기록과 맞추는 판단 (ADR-WEB-001)
import { describe, expect, it } from 'vitest'
import { mergeVaults } from '../vault/mergeVaults'
import { addItem, deleteItem, emptyVault, updateItem } from '../vault/vaultData'
import type { EncryptedVaultPayload } from '../crypto/vaultCipher'
import type { DeviceRecord } from './deviceRecord'
import { mergeSyncFields, reconcileDeviceRecord } from './reconcileDeviceRecord'

const KDF = { salt: 'AAAAAAAAAAAAAAAAAAAAAA==', iterations: 600_000 }
const EMAIL = 'user@example.com'

function record(overrides: Partial<DeviceRecord> = {}): DeviceRecord {
  return {
    version: 1,
    email: EMAIL,
    kdf: KDF,
    payload: { version: 1, cipher: 'AES-256-GCM', iv: 'iv', ciphertext: 'c1', tag: 't' },
    revision: 2,
    lastSeenRevision: 2,
    pendingSync: false,
    ...overrides,
  }
}

const otherPayload: EncryptedVaultPayload = { version: 1, cipher: 'AES-256-GCM', iv: 'iv2', ciphertext: 'c2', tag: 't2' }

describe('reconcileDeviceRecord', () => {
  it('기록이 없고 이 탭이 기록을 본 적이 없으면 그대로 쓴다(처음 로그인)', () => {
    expect(reconcileDeviceRecord({ current: null, lastSeen: null, email: EMAIL, kdf: KDF, unlocked: true })).toEqual({ kind: 'write' })
  })

  it('이 탭이 본 기록이 사라졌으면 다른 탭에서 로그아웃한 것으로 본다', () => {
    expect(reconcileDeviceRecord({ current: null, lastSeen: record(), email: EMAIL, kdf: KDF, unlocked: true })).toEqual({ kind: 'loggedOutElsewhere' })
  })

  it('다른 계정의 기록이면 다른 탭에서 로그아웃한 것으로 본다', () => {
    const current = record({ email: 'other@example.com' })

    expect(reconcileDeviceRecord({ current, lastSeen: record(), email: EMAIL, kdf: KDF, unlocked: true })).toEqual({ kind: 'loggedOutElsewhere' })
  })

  it('같은 이메일인데 KDF 값이 다르면 비밀번호가 바뀐 것으로 본다', () => {
    const current = record({ kdf: { ...KDF, iterations: 700_000 } })

    expect(reconcileDeviceRecord({ current, lastSeen: record(), email: EMAIL, kdf: KDF, unlocked: true })).toEqual({ kind: 'passwordChanged' })
  })

  it('이 탭이 마지막으로 본 기록과 같으면 그대로 쓴다', () => {
    expect(reconcileDeviceRecord({ current: record(), lastSeen: record(), email: EMAIL, kdf: KDF, unlocked: false })).toEqual({ kind: 'write' })
  })

  it('다른 탭이 썼고 잠금 해제 상태면 병합한다', () => {
    const current = record({ payload: otherPayload, pendingSync: true })

    expect(reconcileDeviceRecord({ current, lastSeen: record(), email: EMAIL, kdf: KDF, unlocked: true })).toEqual({ kind: 'merge', record: current })
  })

  it('다른 탭이 썼는데 잠긴 탭이면 쓰지 않는다', () => {
    const current = record({ payload: otherPayload })

    expect(reconcileDeviceRecord({ current, lastSeen: record(), email: EMAIL, kdf: KDF, unlocked: false })).toEqual({ kind: 'skip' })
  })
})

describe('mergeSyncFields', () => {
  it('둘 중 하나라도 대기면 대기로 둔다', () => {
    expect(mergeSyncFields({ revision: 2, lastSeenRevision: 2, pendingSync: false }, record({ pendingSync: true })).pendingSync).toBe(true)
    expect(mergeSyncFields({ revision: 2, lastSeenRevision: 2, pendingSync: true }, record({ pendingSync: false })).pendingSync).toBe(true)
    expect(mergeSyncFields({ revision: 2, lastSeenRevision: 2, pendingSync: false }, record()).pendingSync).toBe(false)
  })

  it('revision은 큰 값을 쓴다', () => {
    expect(mergeSyncFields({ revision: 2, lastSeenRevision: 2, pendingSync: false }, record({ revision: 5 })).revision).toBe(5)
    expect(mergeSyncFields({ revision: 7, lastSeenRevision: 2, pendingSync: false }, record({ revision: 5 })).revision).toBe(7)
  })

  it('되돌림 감지 기준을 낮추지 않는다', () => {
    expect(mergeSyncFields({ revision: 2, lastSeenRevision: 9, pendingSync: false }, record({ lastSeenRevision: 3 })).lastSeenRevision).toBe(9)
    expect(mergeSyncFields({ revision: 2, lastSeenRevision: 3, pendingSync: false }, record({ lastSeenRevision: 9 })).lastSeenRevision).toBe(9)
  })
})

describe('탭 사이 병합 규칙', () => {
  it('다른 탭의 이른 수정보다 내 늦은 삭제를 남긴다', () => {
    const base = addItem(emptyVault(new Date('2026-10-31T00:00:00Z')), { serviceName: 'GitHub', username: 'octocat', password: 'pw' }, new Date('2026-10-31T00:00:00Z'), 'a')
    const theirs = updateItem(base, 'a', { password: 'pw-2' }, new Date('2026-10-31T00:01:00Z'))
    const mine = deleteItem(base, 'a', new Date('2026-10-31T00:02:00Z'))

    const merged = mergeVaults(mine, theirs)

    expect(merged.items.find((item) => item.id === 'a')?.deletedAt).toBe('2026-10-31T00:02:00Z')
  })
})
