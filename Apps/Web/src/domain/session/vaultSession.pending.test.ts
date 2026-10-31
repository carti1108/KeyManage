// @vitest-environment node
// 저장이 서버에 닿지 못했을 때 동기화 대기로 두고 나중에 올린다 (VLT-001)
import { describe, expect, it } from 'vitest'
import { NOW, PASSWORD, fields, saveFromOtherDevice, serverItemIds, unlockedWithItem } from '../../test/sessionFixtures'
import { NetworkError, ServerError, UnexpectedResponseError } from '../server/serverErrors'
import { activeItems, addItem, type VaultData } from '../vault/vaultData'
import { RevisionMismatchError, RevisionRollbackError, VaultSyncConflictError, VaultTooLargeError } from '../vault/vaultSync'
import { SessionExpiredError, type VaultSession } from './vaultSession'

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

/** 항목 a를 올린 뒤 오프라인에서 항목 b를 저장해 대기 상태를 만든다 */
async function pendingWithItemB() {
  const ctx = await unlockedWithItem()
  const syncResults: unknown[] = []
  ctx.session.onSyncChange((error) => syncResults.push(error))
  ctx.vaultApi.offline = true
  await saveItem(ctx.session, 'b')
  return { ...ctx, syncResults }
}

describe('저장이 서버에 닿지 못하면 동기화 대기로 둔다', () => {
  it('네트워크 오류면 저장은 성공하고, 수정 내용이 대기 표시와 함께 보인다', async () => {
    const { session, vaultApi } = await pendingWithItemB()

    expect(session.state()).toMatchObject({ status: 'unlocked', pendingSync: true, vault: { revision: 2 } })
    expect(itemIds(session)).toEqual(['a', 'b'])
    expect(vaultApi.revision).toBe(2)
  })

  it('서버 5xx, 요청 제한(429), 응답 형식 오류도 대기로 둔다', async () => {
    for (const failure of [new ServerError(503, 'UNKNOWN'), new ServerError(429, 'TOO_MANY_ATTEMPTS'), new UnexpectedResponseError('형식 오류')]) {
      const { session, vaultApi } = await unlockedWithItem()
      vaultApi.save = async () => {
        throw failure
      }

      await saveItem(session, 'b')

      expect(session.state()).toMatchObject({ pendingSync: true })
      expect(itemIds(session)).toEqual(['a', 'b'])
    }
  })

  it('잠기거나 세션이 만료돼도 대기 중임을 알 수 있다 (떠남 확인·로그아웃 확인용)', async () => {
    const { session, vaultApi } = await pendingWithItemB()

    session.lock()
    expect(session.state()).toEqual({ status: 'locked', email: 'user@example.com', pendingSync: true })
    expect(session.hasPendingSync()).toBe(true)

    vaultApi.offline = false
    vaultApi.get = async () => {
      throw new ServerError(401, 'UNAUTHORIZED')
    }
    await expect(session.unlock(PASSWORD)).rejects.toBeInstanceOf(SessionExpiredError)
    expect(session.hasPendingSync()).toBe(true)
  })

  it('용량 초과처럼 분명한 거절은 대기로 두지 않고 오류를 낸다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    vaultApi.offline = true
    const huge = addItem(unlockedData(session), { ...fields, notes: 'x'.repeat(6_000_000) }, NOW, 'big')

    await expect(session.save(huge)).rejects.toBeInstanceOf(VaultTooLargeError)
    expect(session.state()).toMatchObject({ pendingSync: false })
    expect(itemIds(session)).toEqual(['a'])
  })

  it('온라인 저장이 성공하면 대기 표시가 없다', async () => {
    const { session } = await unlockedWithItem()

    expect(session.state()).toMatchObject({ pendingSync: false, vault: { revision: 2 } })
  })
})

describe('대기 내용 다시 보내기', () => {
  it('대기 중에 다시 수정하면 이어서 쌓이고, 연결되면 모두 올라가 대기가 풀린다', async () => {
    const { session, vaultApi, syncResults } = await pendingWithItemB()
    await saveItem(session, 'c')
    vaultApi.offline = false

    await session.retryPendingSync()

    expect(session.state()).toMatchObject({ pendingSync: false, vault: { revision: 3 } })
    expect(await serverItemIds(vaultApi)).toEqual(['a', 'b', 'c'])
    expect(syncResults).toEqual([null])
  })

  it('그사이 다른 기기가 저장했으면 병합해서 올린다', async () => {
    const { session, vaultApi } = await pendingWithItemB()
    vaultApi.offline = false
    await saveFromOtherDevice(vaultApi, 'x')

    await session.retryPendingSync()

    expect(itemIds(session)).toEqual(['a', 'b', 'x'])
    expect(await serverItemIds(vaultApi)).toEqual(['a', 'b', 'x'])
    expect(session.state()).toMatchObject({ pendingSync: false })
  })

  it('다시 네트워크 오류면 조용히 대기를 유지한다', async () => {
    const { session, syncResults } = await pendingWithItemB()

    await session.retryPendingSync()

    expect(session.state()).toMatchObject({ pendingSync: true })
    expect(syncResults).toEqual([])
  })

  it('분명한 거절을 받으면 알리고, 저장·잠금 해제 전까지 자동으로 다시 보내지 않는다', async () => {
    const { session, vaultApi, syncResults } = await pendingWithItemB()
    vaultApi.offline = false
    vaultApi.save = async () => {
      throw new ServerError(400, 'INVALID_PARAMETER')
    }

    await session.retryPendingSync()
    await session.retryPendingSync()

    expect(syncResults).toHaveLength(1)
    expect(syncResults[0]).toBeInstanceOf(ServerError)
    expect(session.state()).toMatchObject({ pendingSync: true })
  })

  it('저장 응답의 revision이 맞지 않으면 잠시 실패로 숨기지 않고 오류를 낸다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    vaultApi.save = async () => 99

    await expect(saveItem(session, 'b')).rejects.toBeInstanceOf(RevisionMismatchError)
  })

  it('보내는 중에 잠겨도 올리기에 성공하면 잠금 화면의 대기 표시를 내리고 알린다', async () => {
    const { session, vaultApi, syncResults } = await pendingWithItemB()
    vaultApi.offline = false
    const realSave = vaultApi.save.bind(vaultApi)
    let release = () => {}
    const gate = new Promise<void>((resolve) => (release = resolve))
    let reached = () => {}
    const requested = new Promise<void>((resolve) => (reached = resolve))
    vaultApi.save = async (expectedRevision, payload) => {
      reached()
      await gate
      return realSave(expectedRevision, payload)
    }

    const retrying = session.retryPendingSync()
    await requested
    session.lock()
    release()
    await retrying

    expect(session.state()).toMatchObject({ status: 'locked', pendingSync: false })
    expect(syncResults).toEqual([null])
  })

  it('대기가 없으면 요청을 보내지 않는다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    const before = vaultApi.saves

    await session.retryPendingSync()

    expect(vaultApi.saves).toBe(before)
  })

  it('다시 보내는 중에 수정해도 순서대로 보내 둘 다 올라간다', async () => {
    const { session, vaultApi } = await pendingWithItemB()
    vaultApi.offline = false
    let release = () => {}
    vaultApi.gate = new Promise((resolve) => (release = resolve))

    const retrying = session.retryPendingSync()
    const saving = saveItem(session, 'c')
    release()
    vaultApi.gate = null
    await Promise.all([retrying, saving])

    expect(await serverItemIds(vaultApi)).toEqual(['a', 'b', 'c'])
    expect(session.state()).toMatchObject({ pendingSync: false })
  })
})

describe('대기 중 잠금과 잠금 해제', () => {
  it('잠갔다가 오프라인에서 풀면 대기 내용이 그대로 보인다', async () => {
    const { session } = await pendingWithItemB()
    session.lock()

    expect(await session.unlock(PASSWORD)).toEqual({ offline: true })
    expect(itemIds(session)).toEqual(['a', 'b'])
    expect(session.state()).toMatchObject({ pendingSync: true })
  })

  it('잠갔다가 온라인에서 풀면 서버 볼트로 덮지 않고 대기 내용을 병합해 올린다', async () => {
    const { session, vaultApi } = await pendingWithItemB()
    session.lock()
    vaultApi.offline = false
    await saveFromOtherDevice(vaultApi, 'x')

    expect(await session.unlock(PASSWORD)).toEqual({ offline: false })
    expect(itemIds(session)).toEqual(['a', 'b', 'x'])
    expect(await serverItemIds(vaultApi)).toEqual(['a', 'b', 'x'])
    expect(session.state()).toMatchObject({ pendingSync: false })
  })
})

describe('대기 중 잠금 해제의 되돌림 검사', () => {
  it('대기 내용이 있어도 먼저 서버 볼트를 받아, 기준과 같은 revision으로 되돌아간 서버를 잡는다', async () => {
    const { session, vaultApi, revisionMemory } = await pendingWithItemB()
    session.lock()
    vaultApi.offline = false
    // 이 기기는 revision 3까지 봤지만 서버는 백업에서 복구되어 2로 돌아갔다
    revisionMemory.raise(3)

    await expect(session.unlock(PASSWORD)).rejects.toBeInstanceOf(RevisionRollbackError)
  })
})

describe('대기 중 세션 만료(401)', () => {
  async function expiredWithPending() {
    const ctx = await pendingWithItemB()
    const realSave = ctx.vaultApi.save.bind(ctx.vaultApi)
    ctx.vaultApi.offline = false
    ctx.vaultApi.save = async () => {
      throw new ServerError(401, 'UNAUTHORIZED')
    }
    await ctx.session.retryPendingSync()
    ctx.vaultApi.save = realSave
    return ctx
  }

  it('로그아웃 상태로 바꾸고 만료를 알린다', async () => {
    const { session, syncResults, locks } = await expiredWithPending()

    expect(session.state()).toEqual({ status: 'signedOut' })
    expect(syncResults).toHaveLength(1)
    expect(syncResults[0]).toBeInstanceOf(SessionExpiredError)
    expect(locks()).toBe(1)
  })

  it('같은 계정으로 다시 로그인하면 대기 내용을 병합해 올린다', async () => {
    const { session, vaultApi } = await expiredWithPending()
    await saveFromOtherDevice(vaultApi, 'x')

    await session.login('User@Example.com', PASSWORD)

    expect(itemIds(session)).toEqual(['a', 'b', 'x'])
    expect(await serverItemIds(vaultApi)).toEqual(['a', 'b', 'x'])
    expect(session.state()).toMatchObject({ pendingSync: false })
  })

  it('다른 이메일로 로그인에 실패하면 대기 내용을 버리지 않는다', async () => {
    const { session, vaultApi, authApi } = await expiredWithPending()
    authApi.login = async () => {
      throw new ServerError(401, 'AUTH_FAILED')
    }
    await expect(session.login('typo@example.co', PASSWORD)).rejects.toBeInstanceOf(ServerError)
    authApi.login = async () => ({ accessToken: 'token-1', expiresAt: '2026-11-30T00:00:00Z' })

    await session.login('user@example.com', PASSWORD)

    expect(await serverItemIds(vaultApi)).toEqual(['a', 'b'])
  })

  it('다시 로그인할 때 충돌이 이어지면 로그인을 실패시키고 대기 내용을 남겨 다음 로그인 때 다시 올린다', async () => {
    const { session, vaultApi } = await expiredWithPending()
    const realSave = vaultApi.save.bind(vaultApi)
    vaultApi.save = async () => {
      throw new ServerError(409, 'VAULT_CONFLICT')
    }
    await expect(session.login('user@example.com', PASSWORD)).rejects.toBeInstanceOf(VaultSyncConflictError)
    vaultApi.save = realSave

    await session.login('user@example.com', PASSWORD)

    expect(await serverItemIds(vaultApi)).toEqual(['a', 'b'])
  })

  it('다시 로그인할 때 서버가 받을 수 없는 대기 내용이면 서버 볼트로 열고, 대기 내용을 버린 뒤 알린다', async () => {
    const { session, vaultApi, syncResults } = await expiredWithPending()
    vaultApi.save = async () => {
      throw new ServerError(413, 'VAULT_TOO_LARGE')
    }

    await session.login('user@example.com', PASSWORD)

    expect(itemIds(session)).toEqual(['a'])
    expect(session.state()).toMatchObject({ pendingSync: false })
    expect(session.hasPendingSync()).toBe(false)
    expect(syncResults.at(-1)).toBeInstanceOf(ServerError)
  })

  it('다시 로그인할 때 서버에 닿지 못하면 받아 온 서버 볼트와 병합한 대기로 연다', async () => {
    const { session, vaultApi } = await expiredWithPending()
    await saveFromOtherDevice(vaultApi, 'x')
    vaultApi.save = async () => {
      throw new NetworkError()
    }

    await session.login('user@example.com', PASSWORD)

    expect(itemIds(session)).toEqual(['a', 'b', 'x'])
    expect(session.state()).toMatchObject({ pendingSync: true })
  })

  it('잠금 해제 중에 로그아웃하고 다른 계정으로 로그인하면, 늦게 온 401이 새 세션을 끝내지 않는다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    session.lock()
    const realGet = vaultApi.get.bind(vaultApi)
    let release = () => {}
    const gate = new Promise<void>((resolve) => (release = resolve))
    let reached = () => {}
    const requested = new Promise<void>((resolve) => (reached = resolve))
    vaultApi.get = async () => {
      vaultApi.get = realGet
      reached()
      await gate
      throw new ServerError(401, 'UNAUTHORIZED')
    }

    const unlocking = session.unlock(PASSWORD).catch((error: unknown) => error)
    await requested
    await session.logout()
    await session.login('other@example.com', PASSWORD)
    release()
    await unlocking

    expect(session.state()).toMatchObject({ status: 'unlocked', email: 'other@example.com' })
  })

  it('다른 계정으로 로그인하면 대기 내용을 버린다', async () => {
    const { session, vaultApi } = await expiredWithPending()

    await session.login('other@example.com', PASSWORD)
    await session.logout()
    await session.login('user@example.com', PASSWORD)

    expect(await serverItemIds(vaultApi)).toEqual(['a'])
  })
})
