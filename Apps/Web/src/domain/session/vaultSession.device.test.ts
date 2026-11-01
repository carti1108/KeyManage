// @vitest-environment node
// 웹 기기 저장: 재시작 뒤 잠금 화면, 세션 없는 잠금 해제, 한 계정만 보관 (ADR-0019, SEC-003, VLT-001)
import { describe, expect, it } from 'vitest'
import { NOW, PASSWORD, fields, restart, serverItemIds, setup, unlockedWithItem } from '../../test/sessionFixtures'
import { MasterPasswordChangedError } from '../auth/authService'
import { DeviceStorageError } from '../device/deviceRecord'
import { ServerError } from '../server/serverErrors'
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

  it('비밀번호를 다시 입력했는데 서버 볼트가 되돌아갔으면 세션 없이 두고 토큰을 폐기하며, 받아들이면 그 볼트로 맞춘다', async () => {
    const ctx = await unlockedWithItem()
    const { session, authApi, vaultApi } = await restart(ctx)
    authApi.offline = true
    vaultApi.offline = true
    await session.unlock(PASSWORD)
    authApi.offline = false
    vaultApi.offline = false
    vaultApi.revision = 1

    await expect(session.reconnect(PASSWORD)).rejects.toBeInstanceOf(RevisionRollbackError)
    expect(session.state()).toMatchObject({ signedIn: false })
    expect(authApi.loggedOut).toHaveLength(1)

    await session.reconnect(PASSWORD, { acceptRollback: true })
    expect(session.state()).toMatchObject({ signedIn: true })
  })

  it('세션 없이 잠금을 풀 때 서버가 다른 이유로 거절하면 볼트는 열고 오류를 알린다', async () => {
    const ctx = await unlockedWithItem()
    const { session, authApi } = await restart(ctx)
    const results: unknown[] = []
    session.onSyncChange((error) => results.push(error))
    authApi.prelogin = async () => {
      throw new ServerError(403, 'ORIGIN_NOT_ALLOWED')
    }

    expect(await session.unlock(PASSWORD)).toEqual({ offline: true })
    expect(session.state()).toMatchObject({ status: 'unlocked', signedIn: false })
    expect(results.at(-1)).toBeInstanceOf(ServerError)
  })

  it('다시 입력했는데 서버가 거절하면 실제 사유를 낸다 (연결 실패로 뭉뚱그리지 않는다)', async () => {
    const ctx = await unlockedWithItem()
    const { session, authApi, vaultApi } = await restart(ctx)
    authApi.offline = true
    vaultApi.offline = true
    await session.unlock(PASSWORD)
    authApi.offline = false
    vaultApi.offline = false

    for (const failure of [new ServerError(403, 'ORIGIN_NOT_ALLOWED'), new ServerError(503, 'UNKNOWN')]) {
      authApi.prelogin = async () => {
        throw failure
      }
      await expect(session.reconnect(PASSWORD)).rejects.toBe(failure)
    }
    expect(session.state()).toMatchObject({ signedIn: false })
  })

  it('재시작 뒤 같은 볼트로 잠금을 풀면 기기 기록을 다시 쓰지 않는다', async () => {
    const ctx = await unlockedWithItem()
    const { session, deviceStore } = await restart(ctx)
    const writes = deviceStore.writes

    await session.unlock(PASSWORD)

    expect(deviceStore.writes).toBe(writes)
  })

  it('재시작 때 기기 기록을 읽지 못했어도, 같은 계정으로 로그인하면 기록을 다시 읽어 대기 내용을 병합해 올린다', async () => {
    const ctx = await pendingOnDevice()
    ctx.vaultApi.offline = false
    ctx.deviceStore.failLoads = 1
    const next = setup({ authApi: ctx.authApi, vaultApi: ctx.vaultApi, deviceStore: ctx.deviceStore })
    await expect(next.session.restore()).rejects.toBeInstanceOf(DeviceStorageError)
    expect(next.session.state()).toEqual({ status: 'signedOut' })

    await next.session.login('user@example.com', PASSWORD)

    expect(itemIds(next.session)).toEqual(['a', 'b'])
    expect(await serverItemIds(ctx.vaultApi)).toEqual(['a', 'b'])
    expect(ctx.deviceStore.record).toMatchObject({ pendingSync: false })
  })

  it('기기 기록을 끝내 읽지 못하면 로그인해도 기기에 쓰지 않고 저장 실패를 알린다', async () => {
    const ctx = await pendingOnDevice()
    ctx.vaultApi.offline = false
    const before = ctx.deviceStore.stored
    ctx.deviceStore.failLoads = 3
    const next = setup({ authApi: ctx.authApi, vaultApi: ctx.vaultApi, deviceStore: ctx.deviceStore })
    await next.session.restore().catch(() => undefined)

    await next.session.login('user@example.com', PASSWORD)

    expect(ctx.deviceStore.stored).toEqual(before)
    expect(next.session.state()).toMatchObject({ storedOnDevice: false })
  })

  it('늦게 읽힌 기기 기록에 같은 계정의 대기 내용이 있으면 덮어쓰지 않고, 새로고침하면 병합해 올린다', async () => {
    const ctx = await pendingOnDevice()
    ctx.vaultApi.offline = false
    const before = ctx.deviceStore.stored
    // 시작할 때와 로그인할 때는 읽지 못하고, 처음 쓰기 직전에야 읽힌다
    ctx.deviceStore.failLoads = 2
    const next = setup({ authApi: ctx.authApi, vaultApi: ctx.vaultApi, deviceStore: ctx.deviceStore })
    await next.session.restore().catch(() => undefined)

    await next.session.login('user@example.com', PASSWORD)

    expect(ctx.deviceStore.stored).toEqual(before)
    expect(next.session.state()).toMatchObject({ storedOnDevice: false })

    const reopened = await restart(ctx)
    await reopened.session.unlock(PASSWORD)
    expect(await serverItemIds(ctx.vaultApi)).toEqual(['a', 'b'])
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

  it('같은 이메일로 다시 로그인했을 때 KDF 값이 바뀌어도 되돌림 검사는 그대로 하고, 받아들인 뒤에야 기기 사본을 바꾼다', async () => {
    const ctx = await pendingOnDevice()
    const { session, deviceStore, vaultApi, authApi } = await restart(ctx)
    // 서버가 salt만 바꾸고 빈 볼트를 준다. 비밀번호 변경이 아니라 서버 쪽 유실·조작일 수 있다
    authApi.kdf = { ...authApi.kdf, salt: NEW_SALT }
    vaultApi.offline = false
    vaultApi.payload = null
    vaultApi.revision = 0

    await expect(session.login('user@example.com', PASSWORD)).rejects.toBeInstanceOf(RevisionRollbackError)
    expect(deviceStore.record).toMatchObject({ pendingSync: true })
    expect(deviceStore.record?.kdf.salt).not.toBe(NEW_SALT)

    await session.login('user@example.com', PASSWORD, { acceptRollback: true })

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

  it('다른 계정의 인증이 성공하면 볼트를 준비하지 못해도 이전 기록을 지운다', async () => {
    const ctx = await unlockedWithItem()
    const { session, vaultApi, deviceStore } = await restart(ctx)
    vaultApi.get = async () => {
      throw new ServerError(500, 'INTERNAL_ERROR')
    }

    await expect(session.login('other@example.com', PASSWORD)).rejects.toBeInstanceOf(ServerError)

    expect(deviceStore.stored).toBeUndefined()
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

  it('로그아웃할 때 기록을 지우지 못하면 로그아웃은 하고 실패를 알린다', async () => {
    const { session, deviceStore } = await unlockedWithItem()
    deviceStore.failing = true

    await expect(session.logout()).rejects.toBeInstanceOf(DeviceStorageError)

    expect(session.state()).toEqual({ status: 'signedOut' })
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
    expect(session.state()).toMatchObject({ storedOnDevice: false })
  })

  it('다음 저장이 기기에 들어가면 저장 실패 표시를 내린다', async () => {
    const { session, deviceStore } = await unlockedWithItem()
    deviceStore.failing = true
    await saveItem(session, 'b')
    deviceStore.failing = false

    await saveItem(session, 'c')

    expect(session.state()).toMatchObject({ storedOnDevice: true })
  })

  it('대기 내용이 기기에 저장됐으면 떠남 확인이 필요 없다', async () => {
    const { session } = await pendingOnDevice()

    expect(session.hasUnsavedChanges()).toBe(false)
  })
})

describe('이전 계정의 늦은 응답', () => {
  it('로그아웃 뒤 다른 계정으로 로그인하면 이전 계정의 늦은 저장 응답이 되돌림 감지 기준을 올리지 않는다', async () => {
    const { session, vaultApi, revisionMemory } = await unlockedWithItem()
    const realSave = vaultApi.save.bind(vaultApi)
    let release = () => {}
    const gate = new Promise<void>((resolve) => (release = resolve))
    let reached = () => {}
    const requested = new Promise<void>((resolve) => (reached = resolve))
    vaultApi.save = async (expectedRevision, payload) => {
      vaultApi.save = realSave
      reached()
      await gate
      return realSave(expectedRevision, payload)
    }
    const saving = saveItem(session, 'b').catch(() => undefined)
    await requested
    await session.logout()
    await session.login('other@example.com', PASSWORD)
    const seen = revisionMemory.get()

    release()
    await saving

    expect(revisionMemory.get()).toBe(seen)
  })
})
