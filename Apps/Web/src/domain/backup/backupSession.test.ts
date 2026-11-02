// @vitest-environment node
// 열린 볼트에서 백업 내보내기와 복원 (VLT-004)
import { describe, expect, it, vi } from 'vitest'
import { FakeAuthApi, NOW, PASSWORD, fields, serverItemIds, setup, unlockedWithItem } from '../../test/sessionFixtures'
import { keysOf, vectors } from '../crypto/testVectors'
import { sealVault } from '../crypto/vaultCipher'
import { SessionStateError, type SessionState, type VaultSession } from '../session/vaultSession'
import { mergeVaults } from '../vault/mergeVaults'
import { InvalidVaultDataError, activeItems, addItem, deleteItem, emptyVault, formatTime, updateItem, type VaultData, type VaultItem } from '../vault/vaultData'
import { ReadOnlyVaultError, VaultTooLargeError } from '../vault/vaultSync'
import { BackupDecryptError, NewerBackupVersionError, backupFileName, createBackupFile, openBackup, parseBackup } from './backupFile'
import { restoreBackupInto, restoreItems } from './restoreBackup'

const EXPORTED_AT = new Date('2026-11-01T09:30:00Z')
const RESTORED_AT = new Date('2026-11-01T10:00:00Z')
const other = vectors.keyDerivationCases[1]

function unlockedData(session: VaultSession): VaultData {
  const state = session.state()
  if (state.status !== 'unlocked') throw new Error('잠금 해제 상태가 아님')
  return state.vault.data
}

function activeIds(data: VaultData): string[] {
  return activeItems(data)
    .map((item) => item.id)
    .sort()
}

async function saveItem(session: VaultSession, id: string) {
  await session.save(addItem(unlockedData(session), { ...fields, serviceName: id }, NOW, id))
}

async function exported(session: VaultSession) {
  return parseBackup((await session.exportBackup(EXPORTED_AT)).text)
}

/** 다른 salt·비밀번호를 쓰는 다른 계정으로 로그인한다 */
async function otherAccount() {
  const authApi = new FakeAuthApi()
  authApi.kdf = { kdf: 'PBKDF2-HMAC-SHA256', iterations: vectors.kdf.iterations, salt: other.salt }
  const ctx = setup({ authApi })
  await ctx.session.login('other@example.com', other.password)
  return ctx
}

/** 서버 볼트가 높은 스키마 버전이라 읽기 전용으로 연 계정 */
async function readOnlyAccount() {
  const ctx = setup()
  const { encryptionKey } = await keysOf(vectors.keyDerivationCases[0].name)
  ctx.vaultApi.payload = await sealVault(encryptionKey, JSON.stringify({ version: 2, updatedAt: '2026-10-30T00:00:00Z', items: [] }))
  ctx.vaultApi.revision = 1
  await ctx.session.login('user@example.com', PASSWORD)
  return ctx
}

describe('백업 내보내기', () => {
  it('잠금 해제 상태에서만 내보낸다', async () => {
    const { session } = await unlockedWithItem()
    session.lock()
    await expect(session.exportBackup(EXPORTED_AT)).rejects.toBeInstanceOf(SessionStateError)
    await expect(setup().session.exportBackup(EXPORTED_AT)).rejects.toBeInstanceOf(SessionStateError)
  })

  it('파일에는 평문·이메일·revision이 없고, 계정의 KDF 값과 새 IV로 암호화한 볼트를 담는다', async () => {
    const { session, authApi, deviceStore } = await unlockedWithItem()

    const file = await session.exportBackup(EXPORTED_AT)

    expect(file.name).toBe(backupFileName(EXPORTED_AT))
    for (const secret of ['GitHub', 'octocat', 'pw-1', 'user@example.com', 'example.com', 'revision', 'token-1']) expect(file.text).not.toContain(secret)
    const backup = parseBackup(file.text)
    expect(backup.kdf).toEqual({ salt: authApi.kdf.salt, iterations: authApi.kdf.iterations })
    expect(backup.createdAt).toBe('2026-11-01T09:30:00Z')
    expect(backup.vault.iv).not.toBe(deviceStore.record?.payload.iv)
    await expect(openBackup(backup, PASSWORD)).resolves.toEqual(unlockedData(session))
  })

  it('두 번 내보내면 매번 새 IV로 암호화한다', async () => {
    const { session } = await unlockedWithItem()

    const first = await exported(session)
    const second = await exported(session)

    expect(second.vault.iv).not.toBe(first.vault.iv)
    expect(second.vault.ciphertext).not.toBe(first.vault.ciphertext)
  })

  it('서버에 올리지 못한 대기 중인 변경까지 내보낸다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    vaultApi.offline = true
    await saveItem(session, 'b')
    expect(session.state()).toMatchObject({ pendingSync: true })

    const restored = await openBackup(await exported(session), PASSWORD)

    expect(activeIds(restored)).toEqual(['a', 'b'])
  })

  it('읽기 전용 볼트도 내보낸다(이 버전 앱으로는 복원하지 못한다)', async () => {
    const { session } = await readOnlyAccount()

    const backup = await exported(session)

    await expect(openBackup(backup, PASSWORD)).rejects.toBeInstanceOf(NewerBackupVersionError)
  })
})

describe('백업에서 복원', () => {
  it('같은 비밀번호로 연 백업의 항목을 지금 볼트에 복원해 서버에 저장하고, 복원한 항목 수를 돌려준다', async () => {
    const source = await unlockedWithItem()
    await saveItem(source.session, 'b')
    const backup = await exported(source.session)
    const target = setup()
    await target.session.login('device-2@example.com', PASSWORD)
    await saveItem(target.session, 'z')

    const count = await target.session.restoreBackup(backup, PASSWORD, RESTORED_AT)

    expect(count).toBe(2)
    expect(activeIds(unlockedData(target.session))).toEqual(['a', 'b', 'z'])
    expect(target.session.state()).toMatchObject({ pendingSync: false })
    expect(await serverItemIds(target.vaultApi)).toEqual(['a', 'b', 'z'])
  })

  it('백업 뒤에 고친 항목은 백업 내용으로, 지운 항목은 되살리고, 새로 만든 항목은 남긴다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    await saveItem(session, 'b')
    const backup = await exported(session)
    await session.save(updateItem(unlockedData(session), 'a', { username: 'edited' }, NOW))
    await session.save(deleteItem(unlockedData(session), 'b', NOW))
    await saveItem(session, 'c')

    const count = await session.restoreBackup(backup, PASSWORD, RESTORED_AT)

    expect(count).toBe(2)
    const data = unlockedData(session)
    expect(activeIds(data)).toEqual(['a', 'b', 'c'])
    expect(itemOf(data, 'a')).toMatchObject({ username: 'octocat', updatedAt: formatTime(RESTORED_AT) })
    expect(itemOf(data, 'b')).toMatchObject({ serviceName: 'b', password: 'pw-1', updatedAt: formatTime(RESTORED_AT) })
    expect(itemOf(data, 'b').deletedAt).toBeUndefined()
    expect(await serverItemIds(vaultApi)).toEqual(['a', 'b', 'c'])
  })

  it('지금 내용이 백업과 같으면 건드리지 않고, 저장하지 않고 0개', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    const backup = await exported(session)
    await saveItem(session, 'c')
    const before = unlockedData(session)
    const saves = vaultApi.saves

    const count = await session.restoreBackup(backup, PASSWORD, RESTORED_AT)

    expect(count).toBe(0)
    expect(unlockedData(session)).toBe(before)
    expect(vaultApi.saves).toBe(saves)
  })

  it('다른 계정(다른 salt·비밀번호)의 백업도 그 비밀번호로 복원한다', async () => {
    const source = await otherAccount()
    await saveItem(source.session, 'x')
    const backup = await exported(source.session)
    expect(backup.kdf.salt).toBe(other.salt)
    const { session } = await unlockedWithItem()

    await expect(session.restoreBackup(backup, PASSWORD, RESTORED_AT)).rejects.toBeInstanceOf(BackupDecryptError)
    const count = await session.restoreBackup(backup, other.password, RESTORED_AT)

    expect(count).toBe(1)
    expect(activeIds(unlockedData(session))).toEqual(['a', 'x'])
  })

  it('비밀번호가 틀리거나 파일이 변조되면 거절하고 볼트를 바꾸지 않는다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    const backup = await exported(session)
    const before = unlockedData(session)
    const saves = vaultApi.saves
    const tag = backup.vault.tag
    const tampered = { ...backup, vault: { ...backup.vault, tag: (tag[0] === 'A' ? 'B' : 'A') + tag.slice(1) } }

    await expect(session.restoreBackup(backup, 'wrong password', RESTORED_AT)).rejects.toBeInstanceOf(BackupDecryptError)
    await expect(session.restoreBackup(tampered, PASSWORD, RESTORED_AT)).rejects.toBeInstanceOf(BackupDecryptError)

    expect(unlockedData(session)).toBe(before)
    expect(vaultApi.saves).toBe(saves)
  })

  it('읽기 전용 볼트에는 복원하지 않는다', async () => {
    const source = await unlockedWithItem()
    const backup = await exported(source.session)
    const { session, vaultApi } = await readOnlyAccount()

    await expect(session.restoreBackup(backup, PASSWORD, RESTORED_AT)).rejects.toBeInstanceOf(ReadOnlyVaultError)
    expect(vaultApi.saves).toBe(0)
  })

  it('잠금 해제 상태가 아니거나 복원하는 사이 잠기면 취소한다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    const backup = await exported(session)
    const saves = vaultApi.saves

    const restoring = session.restoreBackup(backup, PASSWORD, RESTORED_AT)
    session.lock()

    await expect(restoring).rejects.toBeInstanceOf(SessionStateError)
    await expect(session.restoreBackup(backup, PASSWORD, RESTORED_AT)).rejects.toBeInstanceOf(SessionStateError)
    expect(vaultApi.saves).toBe(saves)
  })

  it('서버에 닿지 못하면 병합 결과를 동기화 대기로 두고 기기에 저장한다', async () => {
    const source = await otherAccount()
    await saveItem(source.session, 'x')
    const backup = await exported(source.session)
    const { session, vaultApi, deviceStore } = await unlockedWithItem()
    vaultApi.offline = true

    const count = await session.restoreBackup(backup, other.password, RESTORED_AT)

    expect(count).toBe(1)
    expect(session.state()).toMatchObject({ status: 'unlocked', pendingSync: true })
    expect(activeIds(unlockedData(session))).toEqual(['a', 'x'])
    expect(deviceStore.record?.pendingSync).toBe(true)
    vaultApi.offline = false
    expect(await serverItemIds(vaultApi)).toEqual(['a'])
  })
})

describe('복원 예외 (VLT-004 예외)', () => {
  it('병합 결과가 볼트 한도를 넘으면 저장 실패로 거절하고 볼트를 바꾸지 않는다', async () => {
    const source = await otherAccount()
    await source.session.save(addItem(unlockedData(source.session), { ...fields, notes: 'y'.repeat(1_500_000) }, NOW, 'y'))
    const backup = await exported(source.session)
    const { session, vaultApi } = await unlockedWithItem()
    await session.save(addItem(unlockedData(session), { ...fields, notes: 'x'.repeat(2_500_000) }, NOW, 'x'))
    const before = unlockedData(session)
    const saves = vaultApi.saves

    await expect(session.restoreBackup(backup, other.password, RESTORED_AT)).rejects.toBeInstanceOf(VaultTooLargeError)

    expect(unlockedData(session)).toBe(before)
    expect(session.state()).toMatchObject({ pendingSync: false })
    expect(vaultApi.saves).toBe(saves)
  }, 20_000)

  it('백업 볼트에 같은 항목 id가 두 번 있으면 형식 오류로 거절하고 볼트를 바꾸지 않는다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    const item = { id: 'dup', ...fields, createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z' }
    const duplicated = { version: 1, updatedAt: '2026-10-01T00:00:00Z', items: [item, { ...item, username: 'second' }] }
    const { encryptionKey } = await keysOf(vectors.keyDerivationCases[0].name)
    const backup = parseBackup(createBackupFile(await sealVault(encryptionKey, JSON.stringify(duplicated)), { salt: vectors.keyDerivationCases[0].salt, iterations: vectors.kdf.iterations }, NOW).text)
    const before = unlockedData(session)
    const saves = vaultApi.saves

    await expect(session.restoreBackup(backup, PASSWORD, RESTORED_AT)).rejects.toBeInstanceOf(InvalidVaultDataError)

    expect(unlockedData(session)).toBe(before)
    expect(vaultApi.saves).toBe(saves)
  })

  it('키를 만드는 사이 다른 계정으로 바뀌면 합치지 않는다', async () => {
    const source = await unlockedWithItem()
    const backup = await exported(source.session)
    const account = await otherAccount()
    const opened = account.session.state()
    let calls = 0
    const save = vi.fn(async () => {})
    const target = {
      // 처음에는 원래 계정, 키를 만든 뒤에는 다른 계정으로 바뀐 상태를 돌려준다
      state: () => (calls++ === 0 ? opened : { ...opened, email: 'someone-else@example.com' }) as SessionState,
      save,
    }

    await expect(restoreBackupInto(target, backup, PASSWORD, RESTORED_AT)).rejects.toBeInstanceOf(SessionStateError)
    expect(save).not.toHaveBeenCalled()
  })
})

describe('복원 규칙 (VLT-004 규칙 6·7)', () => {
  const created = new Date('2026-10-01T00:00:00Z')
  const afterBackup = new Date('2026-10-20T00:00:00Z')
  const backupData = addItem(addItem(addItem(emptyVault(created), fields, created, 'a'), fields, created, 'b'), fields, created, 'gone')
  const backup = deleteItem(backupData, 'gone', created)

  it('고친 항목은 백업 내용으로, 지운 항목은 되살리며 수정 시각은 복원 시각이고 삭제 표시는 없다', () => {
    const current = deleteItem(updateItem(backup, 'a', { username: 'edited', url: 'https://x' }, afterBackup), 'b', afterBackup)

    const { data, restored } = restoreItems(current, backup, RESTORED_AT)

    expect(restored).toBe(2)
    expect(itemOf(data, 'a')).toEqual({ ...itemOf(backup, 'a'), updatedAt: formatTime(RESTORED_AT) })
    expect(itemOf(data, 'b')).toEqual({ ...itemOf(backup, 'b'), updatedAt: formatTime(RESTORED_AT) })
  })

  it('백업에서 지워진 항목과 백업에 없는 항목은 지금 상태 그대로 둔다', () => {
    const revived = { ...backup, items: backup.items.map((item) => (item.id === 'gone' ? { ...itemOf(backupData, 'gone'), username: 'alive' } : item)) }
    const current = addItem(revived, fields, afterBackup, 'new')

    const { data, restored } = restoreItems(current, backup, RESTORED_AT)

    expect(restored).toBe(0)
    expect(data).toBe(current)
  })

  it('백업에만 있는 항목(다른 계정 백업 등)은 더한다', () => {
    const current = addItem(emptyVault(created), fields, created, 'mine')

    const { data, restored } = restoreItems(current, backup, RESTORED_AT)

    expect(restored).toBe(2)
    expect(data.items.map((item) => item.id)).toEqual(['mine', 'a', 'b'])
  })

  it('보이는 내용이 같으면 시각·필드 순서가 달라도 건드리지 않는다', () => {
    const reordered = { ...backup, items: backup.items.map((item) => ({ ...Object.fromEntries(Object.entries(item).reverse()), updatedAt: formatTime(afterBackup) }) as VaultItem) }

    const { data, restored } = restoreItems(reordered, backup, RESTORED_AT)

    expect(restored).toBe(0)
    expect(data).toBe(reordered)
  })

  it('항목의 모르는 필드는 백업 것을, 볼트 최상위 필드는 지금 볼트 것을 쓴다', () => {
    const backupWithField = { ...backup, note: 'backup-top', items: backup.items.map((item) => (item.id === 'a' ? { ...item, future: 'backup' } : item)) }
    const current = { ...backup, note: 'current-top', items: backup.items.map((item) => (item.id === 'a' ? { ...item, future: 'current' } : item)) }

    const { data, restored } = restoreItems(current, backupWithField, RESTORED_AT)

    expect(restored).toBe(1)
    expect(itemOf(data, 'a').future).toBe('backup')
    expect(data.note).toBe('current-top')
    expect(data.version).toBe(current.version)
    expect(data.updatedAt).toBe(formatTime(RESTORED_AT))
  })

  it('백업 볼트에 같은 항목 id가 두 번 있으면 지금 볼트를 바꾸기 전에 형식 오류로 거절한다', () => {
    const duplicated = { ...backup, items: [...backup.items, { ...itemOf(backup, 'a'), username: 'second' }] }

    expect(() => restoreItems(backup, duplicated, RESTORED_AT)).toThrow(InvalidVaultDataError)
  })

  it('복원 시각이 지금 항목과 같은 초 안이면(밀리초는 잘림) 1초 뒤로 잡아 동률이 되지 않게 한다', () => {
    const current = updateItem(backup, 'a', { username: 'edited' }, new Date('2026-11-01T10:00:00Z'))
    expect(itemOf(current, 'a').updatedAt).toBe('2026-11-01T10:00:00Z')

    const { data } = restoreItems(current, backup, new Date('2026-11-01T10:00:00.700Z'))

    expect(itemOf(data, 'a').updatedAt).toBe('2026-11-01T10:00:01Z')
  })

  it('지금 항목의 시각이 복원 시각보다 늦으면(기기 시계 차이) 그보다 1초 뒤로 잡아 병합에서 이기게 한다', () => {
    const future = new Date('2026-12-01T00:00:00Z')
    const current = updateItem(backup, 'a', { username: 'edited' }, future)

    const { data } = restoreItems(current, backup, RESTORED_AT)

    expect(itemOf(data, 'a').updatedAt).toBe('2026-12-01T00:00:01Z')
  })

  it('복원한 내용은 다른 기기의 복원 전 수정과 동기화 병합해도 남는다', () => {
    const otherDevice = updateItem(backup, 'a', { username: 'other-device' }, afterBackup)
    const current = deleteItem(backup, 'a', afterBackup)
    const { data } = restoreItems(current, backup, RESTORED_AT)

    for (const merged of [mergeVaults(data, otherDevice), mergeVaults(otherDevice, data)]) {
      expect(itemOf(merged, 'a')).toMatchObject({ username: 'octocat', updatedAt: formatTime(RESTORED_AT) })
      expect(itemOf(merged, 'a').deletedAt).toBeUndefined()
    }
  })
})

function itemOf(data: VaultData, id: string): VaultItem {
  const item = data.items.find((candidate) => candidate.id === id)
  if (item === undefined) throw new Error(`항목 없음: ${id}`)
  return item
}
