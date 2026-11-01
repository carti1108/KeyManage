// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { FakeAuthApi, FakeDeviceStore, FakeVaultApi, NOW, PASSWORD, fields, setup, unlockedWithItem } from '../../test/sessionFixtures'
import { createAuthService } from '../auth/authService'
import { NetworkError, ServerError, UnexpectedResponseError } from '../server/serverErrors'
import { addItem, activeItems } from '../vault/vaultData'
import { RevisionRollbackError, createRevisionMemory, createVaultSync } from '../vault/vaultSync'
import { SessionExpiredError, SessionStateError, VaultSetupError, WrongMasterPasswordError, createVaultSession } from './vaultSession'

describe('모든 계정은 볼트를 가진다 (SEC-003)', () => {
  it('가입하면 바로 빈 볼트를 저장하고 잠금 해제 상태가 된다', async () => {
    const { session, vaultApi } = setup()

    await session.register('new@example.com', PASSWORD)

    expect(vaultApi.saves).toBe(1)
    expect(session.state()).toMatchObject({ status: 'unlocked', email: 'new@example.com', vault: { revision: 1 } })
  })

  it('볼트가 없는 계정으로 로그인하면 볼트를 열기 전에 빈 볼트를 저장한다', async () => {
    const { session, vaultApi } = setup()

    await session.login('user@example.com', PASSWORD)

    expect(vaultApi.saves).toBe(1)
    expect(session.state()).toMatchObject({ status: 'unlocked', vault: { revision: 1 } })
  })

  it('볼트가 있으면 그대로 연다', async () => {
    const { vaultApi } = await unlockedWithItem()
    const memory = createRevisionMemory()
    const other = createVaultSession({
      auth: createAuthService(new FakeAuthApi()),
      vaultSyncFor: (_t, encryptionKey) => createVaultSync({ api: vaultApi, encryptionKey, now: () => NOW, revisionMemory: memory }),
      revisionMemory: memory,
      deviceStore: new FakeDeviceStore(),
    })

    await other.login('user@example.com', PASSWORD)

    const state = other.state()
    expect(vaultApi.saves).toBe(2)
    expect(state.status === 'unlocked' && activeItems(state.vault.data).map((i) => i.id)).toEqual(['a'])
  })
})

describe('잠금과 잠금 해제 (SEC-003)', () => {
  it('잠그면 볼트 내용이 상태에서 사라지고 잠금 알림이 간다', async () => {
    const { session, locks } = await unlockedWithItem()

    session.lock()

    expect(session.state()).toEqual({ status: 'locked', email: 'user@example.com', pendingSync: false })
    expect(locks()).toBe(1)
  })

  it('맞는 마스터 비밀번호면 오프라인에서도 잠금을 풀고 저장해 둔 볼트를 연다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    session.lock()
    vaultApi.offline = true

    const result = await session.unlock(PASSWORD)

    const state = session.state()
    expect(result).toEqual({ offline: true })
    expect(state.status === 'unlocked' && activeItems(state.vault.data).map((i) => i.id)).toEqual(['a'])
  })

  it('온라인이면 잠금을 푼 뒤 바로 서버 볼트를 받아 온다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    session.lock()
    const before = vaultApi.saves

    expect(await session.unlock(PASSWORD)).toEqual({ offline: false })
    expect(vaultApi.saves).toBe(before)
    expect(session.state()).toMatchObject({ status: 'unlocked', vault: { revision: 2 } })
  })

  it('틀린 마스터 비밀번호로는 풀리지 않고 잠긴 상태를 유지한다', async () => {
    const { session } = await unlockedWithItem()
    session.lock()

    await expect(session.unlock('wrong password')).rejects.toBeInstanceOf(WrongMasterPasswordError)
    expect(session.state().status).toBe('locked')
  })

  it('잠금을 푼 뒤에도 이어서 저장할 수 있다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    session.lock()
    await session.unlock(PASSWORD)
    const state = session.state()
    if (state.status !== 'unlocked') throw new Error('잠금 해제 상태가 아님')

    await session.save(addItem(state.vault.data, fields, NOW, 'b'))

    expect(vaultApi.revision).toBe(3)
  })

  it('잠긴 상태나 로그인 전에는 저장할 수 없고, 잠금은 여러 번 해도 알림이 한 번만 간다', async () => {
    const { session, locks } = await unlockedWithItem()
    session.lock()
    session.lock()

    expect(locks()).toBe(1)
    await expect(session.save({ version: 1, updatedAt: '2026-10-31T00:00:00Z', items: [] })).rejects.toThrow()
    expect(() => setup().session.lock()).not.toThrow()
  })
})

describe('자동 잠금 연결용 알림', () => {
  it('로그인·가입·잠금 해제로 볼트가 열릴 때마다 잠금 해제 알림이 간다 (자동 잠금 타이머 재시작용)', async () => {
    const ctx = await unlockedWithItem()
    expect(ctx.unlocks()).toBe(1)

    ctx.session.lock()
    await ctx.session.unlock(PASSWORD)
    expect(ctx.unlocks()).toBe(2)

    const fresh = setup()
    await fresh.session.register('new@example.com', PASSWORD)
    expect(fresh.unlocks()).toBe(1)
  })

  it('저장은 잠금 해제 알림을 다시 보내지 않는다', async () => {
    const { unlocks } = await unlockedWithItem()

    expect(unlocks()).toBe(1)
  })
})

describe('잠금 해제 직후 동기화의 되돌림 경고', () => {
  it('되돌림이 감지되면 잠긴 채로 알리고, 사용자가 받아들이면 그 볼트로 연다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    session.lock()
    // 서버를 백업(revision 1)에서 되돌린 상황
    const restored = { revision: 1, payload: vaultApi.payload! }
    vaultApi.get = async () => restored

    await expect(session.unlock(PASSWORD)).rejects.toBeInstanceOf(RevisionRollbackError)
    expect(session.state().status).toBe('locked')

    await session.unlock(PASSWORD, { acceptRollback: true })
    expect(session.state()).toMatchObject({ status: 'unlocked', vault: { revision: 1 } })
  })
})

describe('로그아웃', () => {
  it('잠금 해제 상태에서 로그아웃하면 잠금 알림도 보낸다 (클립보드 비우기)', async () => {
    const { session, locks } = await unlockedWithItem()

    await session.logout()

    expect(locks()).toBe(1)
  })

  it('서버에 토큰 폐기를 요청하고 로그인 전 상태로 돌아간다', async () => {
    const { session, authApi } = await unlockedWithItem()

    await session.logout()

    expect(authApi.loggedOut).toEqual(['token-1'])
    expect(session.state()).toEqual({ status: 'signedOut' })
  })

  it('서버에 닿지 못해도 이 기기에서는 로그아웃한다', async () => {
    const { session, authApi } = await unlockedWithItem()
    authApi.logout = async () => {
      throw new NetworkError()
    }

    await expect(session.logout()).rejects.toBeInstanceOf(NetworkError)
    expect(session.state()).toEqual({ status: 'signedOut' })
  })
})

function gateSaves(vaultApi: FakeVaultApi) {
  let release!: () => void
  vaultApi.gate = new Promise<void>((resolve) => (release = resolve))
  return () => {
    vaultApi.gate = null
    release()
  }
}

describe('진행 중인 작업과 잠금·로그아웃이 겹칠 때', () => {
  it('저장을 기다리는 동안 잠그면, 저장이 끝나도 다시 열리지 않는다', async () => {
    const { session, vaultApi, unlocks } = await unlockedWithItem()
    const state = session.state()
    if (state.status !== 'unlocked') throw new Error('잠금 해제 상태가 아님')
    const release = gateSaves(vaultApi)

    const saving = session.save(addItem(state.vault.data, fields, NOW, 'b'))
    session.lock()
    release()

    await expect(saving).rejects.toBeInstanceOf(SessionStateError)
    expect(session.state().status).toBe('locked')
    expect(unlocks()).toBe(1)
  })

  it('잠금 해제를 기다리는 동안 잠그거나 로그아웃하면, 끝나도 열리지 않는다', async () => {
    const locked = await unlockedWithItem()
    locked.session.lock()
    const unlocking = locked.session.unlock(PASSWORD)
    locked.session.lock()
    await expect(unlocking).rejects.toBeInstanceOf(SessionStateError)
    expect(locked.session.state().status).toBe('locked')

    const out = await unlockedWithItem()
    out.session.lock()
    const unlocking2 = out.session.unlock(PASSWORD)
    await out.session.logout()
    await expect(unlocking2).rejects.toBeInstanceOf(SessionStateError)
    expect(out.session.state()).toEqual({ status: 'signedOut' })
  })

  it('로그인을 기다리는 동안 로그아웃하면, 끝나도 계정이 되살아나지 않는다', async () => {
    const { session, vaultApi } = setup()
    const release = gateSaves(vaultApi)

    const loggingIn = session.login('user@example.com', PASSWORD)
    await new Promise((resolve) => setTimeout(resolve, 0))
    await session.logout()
    release()

    await expect(loggingIn).rejects.toBeInstanceOf(SessionStateError)
    expect(session.state()).toEqual({ status: 'signedOut' })
  })
})

describe('계정 전환과 로그인 시 되돌림', () => {
  it('로그아웃하면 되돌림 감지 기준을 지워, 다른 계정(낮은 revision)으로 로그인할 수 있다', async () => {
    const { session, revisionMemory } = await unlockedWithItem()
    expect(revisionMemory.get()).toBe(2)

    await session.logout()

    expect(revisionMemory.get()).toBe(0)
  })

  it('로그인 때 되돌림이 감지되면 알리고, 사용자가 받아들이면 로그인한다', async () => {
    const { session, vaultApi, revisionMemory } = await unlockedWithItem()
    session.lock()
    revisionMemory.raise(5)

    await expect(session.login('user@example.com', PASSWORD)).rejects.toBeInstanceOf(RevisionRollbackError)
    await session.login('user@example.com', PASSWORD, { acceptRollback: true })

    expect(session.state()).toMatchObject({ status: 'unlocked', vault: { revision: vaultApi.revision } })
  })
})

describe('잠금 해제 직후 동기화가 네트워크 외 이유로 실패할 때', () => {
  it('세션이 만료됐으면(401) 로그아웃 상태로 바꾸고 다시 로그인하게 한다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    session.lock()
    vaultApi.get = async () => {
      throw new ServerError(401, 'UNAUTHORIZED')
    }

    await expect(session.unlock(PASSWORD)).rejects.toBeInstanceOf(SessionExpiredError)
    expect(session.state()).toEqual({ status: 'signedOut' })
  })

  it('서버 오류나 알 수 없는 응답이면 저장해 둔 볼트로 열고 동기화 실패를 알린다', async () => {
    for (const failure of [new ServerError(503, 'UNKNOWN'), new UnexpectedResponseError('형식 오류')]) {
      const { session, vaultApi } = await unlockedWithItem()
      session.lock()
      vaultApi.get = async () => {
        throw failure
      }

      expect(await session.unlock(PASSWORD)).toEqual({ offline: true })
      expect(session.state().status).toBe('unlocked')
    }
  })

  it('되돌림을 받아들였는데 서버에 볼트가 없으면 저장해 둔 볼트를 다시 올리고 연다', async () => {
    const { session, vaultApi } = await unlockedWithItem()
    session.lock()
    vaultApi.payload = null
    vaultApi.revision = 0

    await expect(session.unlock(PASSWORD)).rejects.toBeInstanceOf(RevisionRollbackError)
    await session.unlock(PASSWORD, { acceptRollback: true })

    const state = session.state()
    expect(state.status === 'unlocked' && activeItems(state.vault.data).map((i) => i.id)).toEqual(['a'])
    expect(vaultApi.revision).toBe(1)
  })
})

describe('가입 직후 빈 볼트 저장 실패', () => {
  it('가입은 됐지만 볼트를 준비하지 못했다고 구분해 알린다 (다시 가입하지 말고 로그인하도록)', async () => {
    const { session, vaultApi, authApi } = setup()
    vaultApi.offline = true

    await expect(session.register('new@example.com', PASSWORD)).rejects.toBeInstanceOf(VaultSetupError)
    expect(authApi.registered).toHaveLength(1)
    expect(session.state()).toEqual({ status: 'signedOut' })
  })
})
