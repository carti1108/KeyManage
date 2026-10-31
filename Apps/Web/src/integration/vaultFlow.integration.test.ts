// 웹 클라이언트 코드(키 유도·암호화·세션·동기화)를 실제 백엔드에 붙여 끝까지 이어지는 흐름을 확인한다.
// 백엔드를 먼저 띄워야 한다: Back에서 ./gradlew :app:bootRun, 그다음 npm run test:integration
import { beforeAll, describe, expect, it } from 'vitest'
import { createServices } from '../app/services'
import { ServerError } from '../domain/server/serverErrors'
import { SessionStateError, WrongMasterPasswordError, type VaultSession } from '../domain/session/vaultSession'
import { activeItems, addItem, deleteItem, type VaultData } from '../domain/vault/vaultData'

// 앱 tsconfig는 브라우저 기준이라 Node의 process 타입이 없다. 통합 테스트는 Node에서만 돈다
const env = (globalThis as { process?: { env: Record<string, string | undefined> } }).process?.env
const API = env?.KEYMANAGE_API_URL ?? 'http://localhost:8080'
const PASSWORD = 'integration-master-password'

// 기기마다 따로 만든다. 되돌림 감지 기준도 기기마다 따로 둔다
const device = (): VaultSession => createServices(API).session

function unlockedVault(session: VaultSession): VaultData {
  const state = session.state()
  if (state.status !== 'unlocked') throw new Error(`볼트가 열려 있지 않음: ${state.status}`)
  return state.vault.data
}

const names = (session: VaultSession) => activeItems(unlockedVault(session)).map((item) => item.serviceName).sort()

const item = (serviceName: string) => ({ serviceName, username: `${serviceName.toLowerCase()}@me`, password: `${serviceName}-pw` })

// 다른 기기의 변경은 잠금 해제할 때 서버에서 다시 받아 온다
async function reopen(session: VaultSession) {
  session.lock()
  return session.unlock(PASSWORD)
}

describe('웹 클라이언트 ↔ 백엔드 통합', () => {
  const email = `it-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`
  const laptop = device()
  const phone = device()

  beforeAll(async () => {
    const response = await fetch(`${API}/api/v1/auth/prelogin`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'probe@example.com' }),
    }).catch(() => null)
    if (!response?.ok) throw new Error(`백엔드(${API})에 연결할 수 없음. Back에서 ./gradlew :app:bootRun으로 먼저 띄운다`)
  })

  it('가입하면 빈 볼트를 저장하고 바로 연다 (SEC-003)', async () => {
    await laptop.register(email, PASSWORD)

    expect(laptop.state().status).toBe('unlocked')
    expect(names(laptop)).toEqual([])
  })

  it('한 기기에서 저장한 항목을 다른 기기 로그인에서 본다', async () => {
    await laptop.save(addItem(unlockedVault(laptop), item('GitHub'), new Date()))

    await phone.login(email, PASSWORD)

    expect(names(phone)).toEqual(['GitHub'])
    expect(activeItems(unlockedVault(phone))[0]).toMatchObject({ username: 'github@me', password: 'GitHub-pw' })
  })

  it('두 기기가 같은 revision에서 동시에 저장해도 병합해 둘 다 남긴다 (VLT-001)', async () => {
    // 둘 다 같은 볼트를 보고 있는 상태에서 각자 항목을 더한다
    const laptopEdit = addItem(unlockedVault(laptop), item('Apple'), new Date())
    const phoneEdit = addItem(unlockedVault(phone), item('Naver'), new Date())

    await laptop.save(laptopEdit)
    // 휴대폰은 서버 revision이 앞서 409를 받고, 최신 볼트와 병합해 다시 저장한다
    await phone.save(phoneEdit)

    expect(names(phone)).toEqual(['Apple', 'GitHub', 'Naver'])
    await reopen(laptop)
    expect(names(laptop)).toEqual(['Apple', 'GitHub', 'Naver'])
  })

  it('삭제는 다른 기기에서도 사라진다', async () => {
    const github = activeItems(unlockedVault(laptop)).find((entry) => entry.serviceName === 'GitHub')!

    await laptop.save(deleteItem(unlockedVault(laptop), github.id, new Date()))
    await reopen(phone)

    expect(names(phone)).toEqual(['Apple', 'Naver'])
    // 삭제 표시만 남아 병합해도 되살아나지 않는다
    expect(unlockedVault(phone).items.find((entry) => entry.id === github.id)).toMatchObject({ deletedAt: expect.any(String) })
  })

  it('잠금 해제는 틀린 비밀번호를 거절하고 맞는 비밀번호로 연다', async () => {
    laptop.lock()

    await expect(laptop.unlock('wrong-master-password')).rejects.toBeInstanceOf(WrongMasterPasswordError)
    expect(laptop.state().status).toBe('locked')

    const { offline } = await laptop.unlock(PASSWORD)
    expect(offline).toBe(false)
    expect(names(laptop)).toEqual(['Apple', 'Naver'])
  })

  it('틀린 비밀번호로는 로그인하지 못한다 (SEC-002)', async () => {
    const stranger = device()

    await expect(stranger.login(email, 'wrong-master-password')).rejects.toSatisfy(
      (error: unknown) => error instanceof ServerError && error.code === 'AUTH_FAILED',
    )
    expect(stranger.state().status).toBe('signedOut')
  })

  it('로그아웃하면 이 기기 세션을 지우고, 다시 로그인하면 같은 볼트를 연다', async () => {
    await phone.logout()

    expect(phone.state().status).toBe('signedOut')
    await expect(phone.unlock(PASSWORD)).rejects.toBeInstanceOf(SessionStateError)

    await phone.login(email, PASSWORD)
    expect(names(phone)).toEqual(['Apple', 'Naver'])
  })
})
