// @vitest-environment node
// 웹 기기 저장: 재시작 뒤 잠금 화면, 세션 없는 잠금 해제, 한 계정만 보관 (ADR-0019, SEC-003, VLT-001)
import { describe, expect, it } from 'vitest'
import { NOW, PASSWORD, fields, restart, serverItemIds, setup, unlockedWithItem } from '../../test/sessionFixtures'
import { MasterPasswordChangedError } from '../auth/authService'
import { DeviceStorageError } from '../device/deviceRecord'
import { activeItems, addItem, type VaultData } from '../vault/vaultData'
import { RevisionRollbackError } from '../vault/vaultSync'
import { WrongMasterPasswordError, type VaultSession } from './vaultSession'

const NEW_SALT = 'AAAAAAAAAAAAAAAAAAAAAA=='

function unlockedData(session: VaultSession): VaultData {
  const state = session.state()
  if (state.status !== 'unlocked') throw new Error('잠금 해제 상태가 아님')
  return state.vault.data
}

function itemIds(session: VaultSession): string[] {
  return activeItems(unlockedData(session))
    .map((item) => item.id)
    .sort()
}

async function saveItem(session: VaultSession, id: string) {
  await session.save(addItem(unlockedData(session), { ...fields, serviceName: id }, NOW, id))
}

/** 항목 a를 올린 뒤 오프라인에서 항목 b를 저장해 기기에 대기로 남긴다 */
async function pendingOnDevice() {
  const ctx = await unlockedWithItem()
  ctx.vaultApi.offline = true
  await saveItem(ctx.session, 'b')
  return ctx
}

describe('기기에 저장하는 값', () => {
  it('로그인하면 이메일·KDF 값·암호화된 볼트·revision을 저장하고, 토큰·인증 해시·평문은 저장하지 않는다', async () => {
    const { deviceStore, authApi } = await unlockedWithItem()

    expect(deviceStore.record).toMatchObject({ version: 1, email: 'user@example.com', revision: 2, lastSeenRevision: 2, pendingSync: false })
    const stored = JSON.stringify(deviceStore.record)
    expect(stored).not.toContain('token-1')
    expect(stored).not.toContain(authApi.logins[0].authHash)
    expect(stored).not.toContain('octocat')
  })

  it('오프라인 저장은 대기 표시와 함께 기기에 남는다', async () => {
    const { deviceStore } = await pendingOnDevice()

    expect(deviceStore.record).toMatchObject({ revision: 2, pendingSync: true })
  })
})

describe('다시 열기 (SEC-003)', () => {
  it('기기 기록이 있으면 잠금 화면으로 시작한다', async () => {
    const ctx = await unlockedWithItem()

    const { session } = await restart(ctx)

    expect(session.state()).toEqual({ status: 'locked', email: 'user@example.com', pendingSync: false })
  })

  it('기기 기록이 없거나 깨졌으면 로그인 전으로 시작한다', async () => {
    const ctx = setup()
    ctx.deviceStore.stored = { version: 1, email: 'user@example.com' }

    const { session } = await restart(ctx)

    expect(session.state()).toEqual({ status: 'signedOut' })
  })

  it('온라인이면 잠금을 풀 때 바로 로그인해 서버와 맞춘다', async () => {
    const ctx = await unlockedWithItem()
    const { session, authApi } = await restart(ctx)
    const loginsBefore = authApi.logins.length

    expect(await session.unlock(PASSWORD)).toEqual({ offline: false })

    expect(authApi.logins).toHaveLength(loginsBefore + 1)
    expect(session.state()).toMatchObject({ status: 'unlocked', signedIn: true, vault: { revision: 2 } })
  })

  it('틀린 비밀번호면 로그인 요청을 보내지 않고 잠긴 채 둔다', async () => {
    const ctx = await unlockedWithItem()
    const { session, authApi } = await restart(ctx)
    const loginsBefore = authApi.logins.length

    await expect(session.unlock('wrong password')).rejects.toBeInstanceOf(WrongMasterPasswordError)
    expect(authApi.logins).toHaveLength(loginsBefore)
  })

  it('오프라인이면 저장한 볼트로 열고 세션 없이 둔다. 저장은 기기에 대기로 남는다', async () => {
    const ctx = await unlockedWithItem()
    const { session, authApi, vaultApi, deviceStore } = await restart(ctx)
    authApi.offline = true
    vaultApi.offline = true

    expect(await session.unlock(PASSWORD)).toEqual({ offline: true })
    await saveItem(session, 'b')

    expect(session.state()).toMatchObject({ status: 'unlocked', signedIn: false, pendingSync: true })
    expect(itemIds(session)).toEqual(['a', 'b'])
    expect(deviceStore.record).toMatchObject({ pendingSync: true })
  })

  it('세션 없이 연 뒤 비밀번호를 다시 입력하면 로그인하고 대기 내용을 올린다', async () => {
    const ctx = await unlockedWithItem()
    const { session, authApi, vaultApi } = await restart(ctx)
    authApi.offline = true
    vaultApi.offline = true
    await session.unlock(PASSWORD)
    await saveItem(session, 'b')
    authApi.offline = false
    vaultApi.offline = false

    await expect(session.reconnect('wrong password')).rejects.toBeInstanceOf(WrongMasterPasswordError)
    await session.reconnect(PASSWORD)

    expect(session.state()).toMatchObject({ signedIn: true, pendingSync: false })
    expect(await serverItemIds(vaultApi)).toEqual(['a', 'b'])
  })

  it('재시작 뒤에도 대기 내용을 이어 받아, 잠금을 풀면 올린다', async () => {
    const ctx = await pendingOnDevice()
    const { session, vaultApi } = await restart(ctx)
    vaultApi.offline = false

    expect(session.state()).toMatchObject({ status: 'locked', pendingSync: true })
    await session.unlock(PASSWORD)

    expect(await serverItemIds(vaultApi)).toEqual(['a', 'b'])
    expect(session.state()).toMatchObject({ pendingSync: false })
  })

  it('재시작 뒤에도 되돌림 감지 기준을 이어 받는다', async () => {
    const ctx = await unlockedWithItem()
    // 서버가 백업에서 복구되어 이 기기가 본 revision 2보다 낮은 1로 돌아갔다
    ctx.vaultApi.revision = 1
    const { session } = await restart(ctx)

    await expect(session.unlock(PASSWORD)).rejects.toBeInstanceOf(RevisionRollbackError)
  })
})

describe('비밀번호가 바뀌었을 때 (SEC-003)', () => {
  it('서버 KDF 값이 저장한 값과 다르면 알리고 로그인 화면으로 보낸다', async () => {
    const ctx = await unlockedWithItem()
    const { session, authApi } = await restart(ctx)
    authApi.kdf = { ...authApi.kdf, salt: 'AAAAAAAAAAAAAAAAAAAAAA==' }

    await expect(session.unlock(PASSWORD)).rejects.toBeInstanceOf(MasterPasswordChangedError)
    expect(session.state()).toEqual({ status: 'signedOut' })
  })

  it('같은 이메일로 다시 로그인했을 때 KDF 값이 바뀌었으면 기기 사본과 대기 내용을 버리고 서버 볼트로 연다', async () => {
    const ctx = await pendingOnDevice()
    const { session, deviceStore, vaultApi, authApi } = await restart(ctx)
    // 다른 기기에서 비밀번호를 바꿔 새 salt로 볼트를 새로 암호화한 상황
    authApi.kdf = { ...authApi.kdf, salt: NEW_SALT }
    vaultApi.offline = false
    vaultApi.payload = null
    vaultApi.revision = 0

    await session.login('user@example.com', PASSWORD)

    expect(deviceStore.record).toMatchObject({ kdf: { salt: NEW_SALT }, pendingSync: false })
    expect(itemIds(session)).toEqual([])
  })
})

describe('기기에는 한 계정만 둔다 (VLT-001)', () => {
  it('다른 계정의 로그인이 성공하면 이전 기록을 지우고 새 계정을 저장한다', async () => {
    const ctx = await unlockedWithItem()
    const { session, deviceStore } = await restart(ctx)

    await session.login('other@example.com', PASSWORD)

    expect(deviceStore.record?.email).toBe('other@example.com')
  })

  it('로그인에 실패하면 기록을 지우지 않는다', async () => {
    const ctx = await unlockedWithItem()
    const { session, authApi, deviceStore } = await restart(ctx)
    authApi.login = async () => {
      throw new Error('인증 실패')
    }

    await expect(session.login('other@example.com', PASSWORD)).rejects.toThrow()

    expect(deviceStore.record?.email).toBe('user@example.com')
  })

  it('로그아웃하면 기록을 지운다', async () => {
    const { session, deviceStore } = await unlockedWithItem()

    await session.logout()

    expect(deviceStore.stored).toBeUndefined()
  })

  it('잠금 화면에서 로그아웃해도 기록을 지운다', async () => {
    const ctx = await unlockedWithItem()
    const { session, deviceStore } = await restart(ctx)

    await session.logout()

    expect(deviceStore.stored).toBeUndefined()
    expect(session.state()).toEqual({ status: 'signedOut' })
  })
})

describe('기기 저장 실패 (VLT-001)', () => {
  it('저장에 실패해도 볼트는 쓰던 그대로 두고, 실패를 알리고, 떠남 확인이 필요하다고 답한다', async () => {
    const { session, vaultApi, deviceStore } = await unlockedWithItem()
    const results: unknown[] = []
    session.onSyncChange((error) => results.push(error))
    deviceStore.failing = true
    vaultApi.offline = true

    await saveItem(session, 'b')

    expect(itemIds(session)).toEqual(['a', 'b'])
    expect(results.at(-1)).toBeInstanceOf(DeviceStorageError)
    expect(session.hasUnsavedChanges()).toBe(true)
  })

  it('대기 내용이 기기에 저장됐으면 떠남 확인이 필요 없다', async () => {
    const { session } = await pendingOnDevice()

    expect(session.hasUnsavedChanges()).toBe(false)
  })
})
