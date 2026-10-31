// @vitest-environment node
// 저장이 서버에 닿지 못했을 때 동기화 대기로 두고 나중에 올린다 (VLT-001)
import { describe, expect, it } from 'vitest'
import { NOW, PASSWORD, fields, saveFromOtherDevice, serverItemIds, unlockedWithItem } from '../../test/sessionFixtures'
import { ServerError } from '../server/serverErrors'
import { activeItems, addItem, type VaultData } from '../vault/vaultData'
import { VaultTooLargeError } from '../vault/vaultSync'
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

  it('서버 5xx 오류도 대기로 둔다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    vaultApi.save = async () => {
      throw new ServerError(503, 'UNKNOWN')
    }

    await saveItem(session, 'b')

    expect(session.state()).toMatchObject({ pendingSync: true })
    expect(itemIds(session)).toEqual(['a', 'b'])
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

  it('다른 계정으로 로그인하면 대기 내용을 버린다', async () => {
    const { session, vaultApi } = await expiredWithPending()

    await session.login('other@example.com', PASSWORD)
    await session.logout()
    await session.login('user@example.com', PASSWORD)

    expect(await serverItemIds(vaultApi)).toEqual(['a'])
  })
})
